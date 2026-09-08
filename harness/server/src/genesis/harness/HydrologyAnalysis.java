package genesis.harness;

import genesis.core.Generator;
import genesis.core.fields.Fields;
import genesis.core.hash.Lattice;
import genesis.oracle.FiniteHydrology;
import genesis.oracle.WindowFlux;
import java.util.Arrays;

/** Adapter from immutable world samples to an explicitly bounded reference experiment. */
public final class HydrologyAnalysis {
    private HydrologyAnalysis() {}
    public static String json(Generator generator, long x, long z, long step, int width, int height) {
        return json(generator, x, z, step, width, height, "edges");
    }
    public static String json(Generator generator, long x, long z, long step, int width, int height, String policy) {
        FiniteHydrology.Boundary boundary = switch (policy) {
            case "edges" -> FiniteHydrology.Boundary.OPEN_EDGES;
            case "connectedWater" -> FiniteHydrology.Boundary.CONNECTED_WATER;
            default -> throw new IllegalArgumentException("outlets must be edges or connectedWater");
        };
        if (width < 2 || height < 2 || width > 256 || height > 256 || step < 1 || step > 1_048_576)
            throw new IllegalArgumentException("Analysis dimensions must be 2..256; step 1..1048576");
        // Validate the entire request before expensive sampling.
        Lattice.check(x); Lattice.check(z);
        Lattice.check(Math.addExact(x, Math.multiplyExact(step, width - 1L)));
        Lattice.check(Math.addExact(z, Math.multiplyExact(step, height - 1L)));
        long start = System.nanoTime();
        int[] elevation = new int[width * height]; boolean[] sea = new boolean[elevation.length];
        for (int j = 0; j < height; j++) for (int i = 0; i < width; i++) {
            int p = j * width + i; long wx = x + i * step, wz = z + j * step;
            sea[p] = generator.fields.get(Fields.SEA_MASK, wx, wz) == 1;
            long mm = Math.round(generator.fields.get(Fields.BASE_ELEVATION, wx, wz) * 1000);
            elevation[p] = Math.toIntExact(sea[p] ? Math.min(0, mm) : Math.max(1, mm));
        }
        var result = FiniteHydrology.solve(width, height, elevation, sea, FiniteHydrology.terminals(width, height, sea, boundary));
        long[] depth = new long[elevation.length]; int enclosed = 0, connected = 0;
        for (int p = 0; p < depth.length; p++) {
            depth[p] = (long) result.filled()[p] - elevation[p];
            if (result.water()[p] == 1) connected++;
            if (result.water()[p] == 2) enclosed++;
        }
        StringBuilder windows = new StringBuilder("[");
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int x0 = quadrant % 2 == 0 ? 0 : width / 2, x1 = quadrant % 2 == 0 ? width / 2 : width;
            int z0 = quadrant / 2 == 0 ? 0 : height / 2, z1 = quadrant / 2 == 0 ? height / 2 : height;
            var ledger = WindowFlux.audit(result, x0, z0, x1, z1);
            if (quadrant != 0) windows.append(',');
            windows.append("{\"x0\":").append(x0).append(",\"z0\":").append(z0).append(",\"x1\":").append(x1).append(",\"z1\":").append(z1)
                .append(",\"rain\":").append(ledger.rain()).append(",\"inflow\":").append(ledger.inflow())
                .append(",\"outflow\":").append(ledger.outflow()).append(",\"terminalDischarge\":").append(ledger.terminalDischarge())
                .append(",\"unresolvedRain\":").append(ledger.unresolvedRain()).append(",\"incomingEdges\":").append(ledger.incomingEdges())
                .append(",\"outgoingEdges\":").append(ledger.outgoingEdges()).append(",\"balanced\":").append(ledger.balanced()).append('}');
        }
        windows.append(']');
        return "{\"version\":\"" + FiniteHydrology.VERSION + "\",\"generatorVersion\":\"" + Generator.VERSION
            + "\",\"model\":\"" + generator.model.id
            + "\",\"boundary\":\"" + (boundary == FiniteHydrology.Boundary.OPEN_EDGES ? "all-edge-cells" : "connected-water-cells")
            + "\",\"connectivity\":8,\"heightUnit\":\"model millimetres\",\"x\":\"" + x
            + "\",\"z\":\"" + z + "\",\"step\":" + step + ",\"width\":" + width + ",\"height\":" + height
            + ",\"landCells\":" + result.landCells() + ",\"discharged\":" + result.discharged()
            + ",\"unresolvedLandCells\":" + result.unresolvedLand() + ",\"terminalCount\":" + result.terminalCount()
            + ",\"edgeWaterCells\":" + connected + ",\"enclosedWaterCells\":" + enclosed
            + ",\"milliseconds\":" + (System.nanoTime() - start) / 1e6
            + ",\"windows\":" + windows
            + ",\"fields\":{\"elevation\":" + Arrays.toString(elevation) + ",\"filled\":" + Arrays.toString(result.filled())
            + ",\"fillDepth\":" + Arrays.toString(depth) + ",\"water\":" + Arrays.toString(result.water())
            + ",\"downstream\":" + Arrays.toString(result.downstream()) + ",\"outlet\":" + Arrays.toString(result.outlet())
            + ",\"order\":" + Arrays.toString(result.order()) + ",\"accumulation\":" + Arrays.toString(result.accumulation())
            + ",\"status\":" + Arrays.toString(result.status()) + "}}";
    }
}
