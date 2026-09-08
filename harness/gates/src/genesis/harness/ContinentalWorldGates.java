package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.elevation.ContinentalScaffold;
import genesis.core.fields.FieldId;
import genesis.core.fields.Fields;
import genesis.core.fields.TileCache;
import genesis.core.hash.Lattice;
import genesis.core.hydro.CoarseChannels;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Continental composition checks: these are world fields, not just scaffold pictures. */
public final class ContinentalWorldGates {
    private static final List<FieldId<?>> CONNECTED = List.of(Fields.CONTINENTALITY, Fields.BASE_ELEVATION,
        Fields.SEA_MASK, Fields.CONTINENT_SEA_MASK, Fields.TERRAIN_DETAIL, Fields.TECTONIC_RELIEF,
        Fields.SEA_DISTANCE, Fields.DRAINAGE_RANK, Fields.FLOW_DIRECTION, Fields.CHANNEL_DISTANCE, Fields.PORT_DISTANCE);
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static Generator world(long seed, Params params) { return new Generator(seed, params, Generator.Model.CONTINENTAL); }
    public static void run() throws Exception {
        Params defaults = Params.defaults(); Generator g = world(42, defaults);
        Generator flat = world(42, new Params(Map.of("terrainDetailHeight", 0.0, "mountainHeight", 0.0)));
        Generator changedRelief = world(42, new Params(Map.of("terrainDetailHeight", 4000.0, "mountainHeight", 8000.0, "wavelength", 32768.0)));
        Generator dry = world(42, new Params(Map.of("continentCoverage", 0.0)));
        Random random = new Random(237); int land = 0, sea = 0, varied = 0, resolved = 0;
        int spacing = defaults.integer("coarseSpacing");
        for (int n = 0; n < 1200; n++) {
            long x = random.nextLong(-262144, 262144), z = random.nextLong(-262144, 262144);
            int mask = g.fields.get(Fields.SEA_MASK, x, z);
            double score = g.fields.get(Fields.CONTINENTALITY, x, z), height = g.fields.get(Fields.BASE_ELEVATION, x, z);
            double detail = g.fields.get(Fields.TERRAIN_DETAIL, x, z), tectonic = g.fields.get(Fields.TECTONIC_RELIEF, x, z);
            check(mask == (score <= 0 ? 1 : 0) && mask == (height <= 0 ? 1 : 0), "Continental coast/height sign mismatch");
            check(mask == g.fields.get(Fields.CONTINENT_SEA_MASK, x, z), "Continental mask alias uses old scaffold coast");
            check(mask == flat.fields.get(Fields.SEA_MASK, x, z) && mask == changedRelief.fields.get(Fields.SEA_MASK, x, z), "Relief changes move coast");
            if (mask == 1) { sea++; check(detail == 0 && tectonic == 0, "Land relief leaks into water"); }
            else {
                land++; check(height == score * defaults.get("landHeight") + tectonic + detail, "Height layers do not compose");
                if (height > flat.fields.get(Fields.BASE_ELEVATION, x, z) + 1) varied++;
            }
            check(dry.fields.get(Fields.SEA_MASK, x, z) == 1 && dry.fields.get(Fields.BASE_ELEVATION, x, z) == -defaults.get("oceanDepth"), "Coverage does not drive terrain");
            check(dry.fields.get(Fields.FLOW_DIRECTION, x, z) == 0 && dry.fields.get(Fields.DRAINAGE_RANK, x, z) == 0,
                "Coverage does not drive drainage");
            check(dry.fields.get(Fields.CHANNEL_DISTANCE, x, z) == spacing / 4 && dry.fields.get(Fields.PORT_DISTANCE, x, z) == spacing / 4,
                "Old land routes remain in continental water-only fixture");
            long ax = Math.floorDiv(x, spacing) * spacing, az = Math.floorDiv(z, spacing) * spacing;
            int rank = g.fields.get(Fields.DRAINAGE_RANK, ax, az);
            check(g.fields.get(Fields.SEA_DISTANCE, x, z) == (rank < 0 ? -1 : rank * spacing), "Distance uses another model");
            for (int r = rank; r > 0; r--) {
                resolved++; int direction = g.fields.get(Fields.FLOW_DIRECTION, ax, az);
                ax += direction == 2 ? spacing : direction == 4 ? -spacing : 0;
                az += direction == 3 ? spacing : direction == 1 ? -spacing : 0;
                check(g.fields.get(Fields.DRAINAGE_RANK, ax, az) == r - 1, "Continental route does not decrease rank");
            }
            if (rank >= 0) check(g.fields.get(Fields.SEA_MASK, ax, az) == 1, "Route terminates on old rather than continental sea");
        }
        check(land > 100 && sea > 100 && varied > 100 && resolved > 100, "Composition fixture lacks terrain/coast/route coverage");
        var guides = new CoarseChannels(42, defaults, g.fields); int crossings = 0;
        for (int j = -16; j < 16; j++) for (int i = -16; i < 16; i++) for (var port : guides.cell(i,j).crossings()) {
            crossings++; check(g.fields.get(Fields.PORT_DISTANCE, port.x, port.z) == 0 && g.fields.get(Fields.CHANNEL_DISTANCE, port.x, port.z) == 0,
                "Continental guides do not consume continental routing");
        }
        check(crossings > 0, "No continental crossing fixture");
        // All fields, not only the newly added ones, retain crop/zoom/cold query equivalence in this model.
        for (FieldId<?> field : g.fields.ids()) {
            Object[] whole = g.fields.values(field, -65397, -94317, 2051, 64, 64);
            Object[] zoom = world(42, defaults).fields.values(field, -65397, -94317, 4102, 32, 32);
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) check(whole[z * 2 * 64 + x * 2].equals(zoom[z * 32 + x]), "Continental zoom changes " + field.name);
            for (int q : new int[]{3,0,2,1}) {
                int ox = q % 2 * 32, oz = q / 2 * 32;
                Object[] part = world(42, defaults).fields.values(field, -65397 + ox * 2051L, -94317 + oz * 2051L, 2051, 32, 32);
                for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) check(whole[(z + oz) * 64 + x + ox].equals(part[z * 32 + x]), "Continental crop changes " + field.name);
            }
        }
        for (long seed : new long[]{Long.MIN_VALUE, Long.MAX_VALUE}) for (long x : new long[]{-Lattice.MAX_COORDINATE, Lattice.MAX_COORDINATE}) {
            Generator edge = world(seed, new Params(Map.of("coarseSpacing", 1025.0, "continentScale", 16385.0)));
            for (FieldId<?> field : CONNECTED) check(Double.isFinite(((Number)edge.fields.get(field,x,-x)).doubleValue()), "Continental numeric limit");
        }
        var cache = new TileCache(g, 2); List<Integer> order = new ArrayList<>(); Object[][] expected = new Object[64][];
        for (int k = 0; k < 64; k++) { order.add(k); expected[k] = cache.values(Fields.BASE_ELEVATION,k*8192L-262144,-k*4096L,17,8,8); }
        Collections.shuffle(order,random);
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int k : order) jobs.add(() -> java.util.Arrays.equals(expected[k],cache.values(Fields.BASE_ELEVATION,k*8192L-262144,-k*4096L,17,8,8)));
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Continental cache/order/concurrency mismatch");
        }
        golden(false); diagnostics();
        System.out.println("PASS CONTINENT WORLD: shared coast/height/drainage, actual relief, resolved paths, all-field cold crop/zoom, cache/concurrency, numeric extremes");
    }
    public static void golden(boolean candidates) throws Exception {
        Properties baseline = new Properties();
        if (!candidates) {
            try (var in = Files.newInputStream(Path.of("harness/golden/continental-world.properties"))) { baseline.load(in); }
            check("continental-world-v1".equals(baseline.getProperty("version")), "Continental world baseline version");
            for (String id : Params.SPECS.keySet()) check(Params.defaults().get(id) == Double.parseDouble(baseline.getProperty("param." + id)), "Continental world default changed " + id);
        } else Params.defaults().values().forEach((key,value) -> System.out.println("param." + key + "=" + value));
        for (long seed : new long[]{0,1,-1,42,137,8675309,Long.MIN_VALUE,Long.MAX_VALUE}) {
            Generator g = world(seed, Params.defaults()); ByteBuffer bytes = ByteBuffer.allocate(48 * 48 * CONNECTED.size() * 8);
            for (int z = 0; z < 48; z++) for (int x = 0; x < 48; x++) for (FieldId<?> field : CONNECTED) {
                Number value = (Number)g.fields.get(field,-98167+x*4096L,-94848+z*4096L);
                bytes.putLong(field.type == Double.class ? Double.doubleToLongBits(value.doubleValue()) : value.longValue());
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
            if (candidates) System.out.println("continental-world:seed." + seed + "=" + hash);
            else check(hash.equals(baseline.getProperty("seed." + seed)), "Continental world fields changed seed " + seed);
        }
    }
    public static void diagnostics() throws Exception {
        Files.createDirectories(Path.of("build/gallery")); Generator g = world(42, Params.defaults());
        var elevation = new Renderer.Layer[]{new Renderer.Layer(Fields.BASE_ELEVATION,1)};
        ImageIO.write(Renderer.render(g,elevation,-262144,-262144,1024,512,512).image(),"png",Path.of("build/gallery/continental-terrain-overview.png").toFile());
        var scaffold = new ContinentalScaffold(42,Params.defaults()); var land = scaffold.landmass(0,0);
        for (int j = -1; j <= 1 && !land.active; j++) for (int i = -1; i <= 1 && !land.active; i++) land = scaffold.landmass(i,j);
        check(land.active,"Missing regional land fixture"); long ox = land.centerX(0)-32768, oz = land.centerZ(0)-32768;
        ImageIO.write(Renderer.render(g,elevation,ox,oz,128,512,512).image(),"png",Path.of("build/gallery/continental-terrain-regional.png").toFile());
        var fields = List.of(Fields.BASE_ELEVATION,Fields.TECTONIC_RELIEF,Fields.TERRAIN_DETAIL,Fields.SEA_MASK,Fields.DRAINAGE_RANK,Fields.CHANNEL_DISTANCE);
        var sheet = new BufferedImage(1184,916,BufferedImage.TYPE_INT_RGB); var pen = sheet.createGraphics();
        pen.setColor(new Color(0x10181b));pen.fillRect(0,0,1184,916);pen.setColor(new Color(0xe3e9e5));pen.setFont(new Font("SansSerif",Font.PLAIN,15));
        for (int k = 0; k < fields.size(); k++) {
            FieldId<?> field=fields.get(k);int px=8+k%3*392,py=36+k/3*444;
            pen.drawString(field.label,px,py-12);
            var layers=field==Fields.CHANNEL_DISTANCE?new Renderer.Layer[]{new Renderer.Layer(Fields.SEA_MASK,.4),new Renderer.Layer(field,1),new Renderer.Layer(Fields.PORT_DISTANCE,1)}:new Renderer.Layer[]{new Renderer.Layer(field,1)};
            pen.drawImage(Renderer.render(g,layers,-196608,-196608,1024,384,384).image(),px,py,null);
            pen.drawString("Seed 42 / model=continental / same coordinates",px,py+403);
        }
        pen.drawString("One committed coast drives every panel. Pink = bounded drainage unresolved; cyan/gold = guides/ports, not carved rivers.",8,907);pen.dispose();
        ImageIO.write(sheet,"png",Path.of("build/gallery/continental-world-layers.png").toFile());
    }
    public static void main(String[] args) throws Exception { golden(true); diagnostics(); }
}
