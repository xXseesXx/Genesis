package genesis.harness;

import genesis.core.Generator;
import genesis.core.fields.FieldId;
import genesis.core.fields.Fields;
import genesis.core.hash.Hash64;
import java.awt.image.BufferedImage;

/** Fixed palettes: display mapping never changes generator values or depends on viewport range. */
public final class Renderer {
    private Renderer() {}
    public record Layer(FieldId<?> field, double opacity) {
        public Layer {
            if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1)
                throw new IllegalArgumentException("Opacity must be in [0,1]");
        }
    }
    public record Result(BufferedImage image, double min, double max, double mean, double landFraction, long nanos) {}

    public static Result render(Generator generator, Layer[] layers, long x, long z, long step, int w, int h) {
        if (layers.length == 0 || layers.length > generator.fields.ids().size())
            throw new IllegalArgumentException("Invalid layer count");
        long start = System.nanoTime();
        BufferedImage image = null;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum = 0;
        int land = 0;
        boolean landMetric = layers[0].field == Fields.BASE_ELEVATION || layers[0].field == Fields.CONTINENTALITY || layers[0].field == Fields.SEA_MASK || layers[0].field == Fields.CONTINENT_SCAFFOLD || layers[0].field == Fields.CONTINENT_SEA_MASK;
        for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
            Layer layer = layers[layerIndex];
            Object[] tile = generator.fields.values(layer.field, x, z, step, w, h);
            if (image == null) image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
                Number raw = (Number) tile[row * w + col];
                double value = raw.doubleValue();
                if (layerIndex == 0 && layer.field.type != Long.class) { min = Math.min(min, value); max = Math.max(max, value); sum += value; }
                if (layerIndex == 0 && landMetric && (layer.field == Fields.SEA_MASK || layer.field == Fields.CONTINENT_SEA_MASK ? value == 0 : value > 0)) land++;
                int color;
                if (layer.field.type == Long.class) {
                    long identity = raw.longValue();
                    if (layer.field == Fields.SUB_PLATE_ID) identity = Hash64.stream(identity, generator.fields.get(Fields.PLATE_ID, x + col * step, z + row * step));
                    long hash = Hash64.mix(identity);
                    color = java.awt.Color.HSBtoRGB((hash >>> 40) / (float) (1 << 24), .38f, .82f);
                } else color = color(layer.field, value);
                if (layer.field == Fields.BASE_ELEVATION && value > 0 && w > 1 && h > 1) {
                    int left = Math.max(0, col - 1), right = Math.min(w - 1, col + 1);
                    int top = Math.max(0, row - 1), bottom = Math.min(h - 1, row + 1);
                    double dx = (((Number) tile[row * w + right]).doubleValue() - ((Number) tile[row * w + left]).doubleValue()) / ((right - left) * (double) step);
                    double dz = (((Number) tile[bottom * w + col]).doubleValue() - ((Number) tile[top * w + col]).doubleValue()) / ((bottom - top) * (double) step);
                    // Display-only relief exaggeration and northwest illumination.
                    double light = (.7 + 2 * dx + 2 * dz) / Math.sqrt(1 + 16 * dx * dx + 16 * dz * dz);
                    color = blend(0x101b22, color, Math.max(.35, Math.min(1, .45 + .65 * light)));
                }
                double opacity = layer.opacity;
                if (layer.field == Fields.CHANNEL_DISTANCE || layer.field == Fields.PORT_DISTANCE) {
                    // World-coordinate distance is pure; stroke thickness is display-only.
                    // Keep thickness below the distance cap so absent guides never fill the map.
                    double stroke = Math.min(generator.params.integer("coarseSpacing") / 16.0,
                        step * (layer.field == Fields.PORT_DISTANCE ? 2.0 : 1.25));
                    color = layer.field == Fields.PORT_DISTANCE ? 0xffd17b : 0x64dce6;
                    opacity *= Math.max(0, 1 - value / stroke);
                    if (layerIndex == 0) image.setRGB(col, row, 0x102029);
                }
                if (layer.field == Fields.BOUNDARY_TYPE) {
                    double distance = generator.fields.get(Fields.BOUNDARY_DISTANCE, x + col * step, z + row * step);
                    // Two sample pixels is a display stroke, never a generator corridor commitment.
                    opacity *= Math.max(0, 1 - distance / (2 * (double) step));
                }
                image.setRGB(col, row, blend(image.getRGB(col, row), color, opacity));
            }
        }
        return new Result(image, min == Double.POSITIVE_INFINITY ? Double.NaN : min,
            max == Double.NEGATIVE_INFINITY ? Double.NaN : max, sum / (w * (double) h), landMetric ? land / (w * (double) h) : Double.NaN, System.nanoTime() - start);
    }
    private static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | blue;
    }
    public static int color(FieldId<?> field, double value) {
        if (field == Fields.SEA_MASK || field == Fields.CONTINENT_SEA_MASK) return value == 1 ? 0x245b78 : 0xa8b879;
        if (field == Fields.CONTINENT_SCAFFOLD) return value <= 0 ? blend(0x0a263e, 0x5299a7, 1 + value / 1000) : blend(0xa8b879, 0x526d45, value / 1000);
        if (field == Fields.TERRAIN_DETAIL || field == Fields.TECTONIC_RELIEF)
            return blend(0x13262a, field == Fields.TERRAIN_DETAIL ? 0xe0c798 : 0xe79c6f,
                Math.sqrt(Math.max(0, Math.min(1, value / field.displayMax))));
        if (field == Fields.SEA_DISTANCE || field == Fields.DRAINAGE_RANK || field == Fields.FLOW_DIRECTION) {
            if (value < 0) return 0xc261a3;
            if (field == Fields.FLOW_DIRECTION) return new int[] {0x245b78, 0xdbba79, 0xb6d898, 0x8cacde, 0xe28b77}[(int) value];
        }
        if (field == Fields.BASE_ELEVATION) {
            if (value <= 0) return blend(0x0a263e, 0x5299a7, Math.max(0, Math.min(1, 1 + value / 4000)));
            int[] land = {0x92b177, 0xb5bd82, 0xc4af85, 0x9f8d7c, 0xf2eee0};
            double scaled = Math.sqrt(Math.min(1, value / 4000)) * (land.length - 1);
            int index = Math.min(land.length - 2, (int) scaled);
            return blend(land[index], land[index + 1], scaled - index);
        }
        if (field == Fields.BOUNDARY_TYPE) return value < 0 ? 0x64bad1 : value > 0 ? 0xed9770 : 0xc8ba77;
        if (field == Fields.CRUST_TYPE) return value == 0 ? 0x285c78 : 0xa6bb7b;
        double t = Math.max(0, Math.min(1, (value - field.displayMin) / (field.displayMax - field.displayMin)));
        // UI palette anchors, not generator parameters.
        int[] stops = field == Fields.UPLIFT || field == Fields.VELOCITY_X || field == Fields.VELOCITY_Z
            ? new int[] { 0x2d85ac, 0x172a33, 0xf3b478 }
            : field.name.equals("ridges")
            ? new int[] { 0x151a27, 0x4c5776, 0xaf97b6, 0xffdcb0 }
            : new int[] { 0x102839, 0x286178, 0x7aa899, 0xddd8a6, 0xf6eee0 };
        double scaled = t * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) scaled);
        return blend(stops[i], stops[i + 1], scaled - i);
    }
}
