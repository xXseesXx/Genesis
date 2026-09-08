package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.elevation.CoastTopology;
import genesis.core.elevation.MacroElevation;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.ArrayList;

public final class CoastGates {
    private CoastGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static FieldRegistry fixture(int crust) {
        return new FieldRegistry.Builder().add(Fields.CRUST_FRACTION, (x, z) -> crust * 1000).add(Fields.UPLIFT, (x, z) -> 1.0).build();
    }
    public static void run() throws Exception {
        Params pureCrust = new Params(Map.of("crustInfluence", 1000.0, "seaSearchRadius", 8.0));
        CoastTopology allLand = new CoastTopology(42, pureCrust, fixture(1));
        CoastTopology allSea = new CoastTopology(42, pureCrust, fixture(0));
        check(allLand.route(0, 0).rank == -1 && allLand.route(0, 0).direction == -1, "Land without a terminal must be unresolved");
        check(allSea.route(0, 0).rank == 0 && allSea.route(0, 0).direction == 0, "Sea must be a terminal");
        FieldRegistry cliff = new FieldRegistry.Builder().add(Fields.CRUST_FRACTION, (x, z) -> x >= 0 ? 1000 : 0).add(Fields.UPLIFT, (x, z) -> 2.0).build();
        CoastTopology straight = new CoastTopology(42, pureCrust, cliff);
        MacroElevation relief = new MacroElevation(straight, pureCrust, cliff);
        long coastX = -straight.spacing / 2;
        check(straight.numerator(coastX, 17) == 0 && relief.elevation(coastX, 17) == 0, "Relief moves an exact committed coast");
        for (int i = -8; i <= 12; i++) {
            var route = straight.route(i, 0);
            int expected = i < 0 ? 0 : i + 1 <= 8 ? i + 1 : -1;
            check(route.rank == expected, "Straight coast analytic rank mismatch");
        }
        for (int i = 0; i < 8300; i++) allSea.route(i, -i);
        check(allSea.route(0, 0).rank == 0, "Eviction changed sea route");
        int resolvedLand = 0, unresolvedLand = 0, sea = 0;
        for (long seed : new long[] {0, 42, -1, Long.MIN_VALUE}) {
            Generator g = new Generator(seed, Params.defaults());
            Generator mountains = new Generator(seed, new Params(Map.of("mountainHeight", 8000.0)));
            CoastTopology topology = new CoastTopology(seed, g.params, g.fields);
            int step = topology.spacing, radius = g.params.integer("seaSearchRadius");
            for (int trial = 0; trial < 96; trial++) {
                long i = Math.floorMod(Hash64.hash(seed, 0, trial, 1), 64) - 32;
                long j = Math.floorMod(Hash64.hash(seed, 0, trial, 2), 64) - 32;
                var route = topology.route(i, j);
                int reference = Integer.MAX_VALUE;
                for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                    int distance = Math.abs(dx) + Math.abs(dz);
                    if (distance <= radius && topology.vertex(i + dx, j + dz) <= 0) reference = Math.min(reference, distance);
                }
                check(route.rank == (reference == Integer.MAX_VALUE ? -1 : reference), "Rank differs from independent diamond reference");
                if (route.rank < 0) unresolvedLand++;
                else if (route.rank == 0) sea++;
                else {
                    resolvedLand++;
                    long ci = i, cj = j;
                    for (int remaining = route.rank; remaining > 0; remaining--) {
                        var next = topology.route(ci, cj);
                        check(next.rank == remaining, "Coarse path does not strictly decrease rank");
                        switch (next.direction) { case 1 -> cj--; case 2 -> ci++; case 3 -> cj++; case 4 -> ci--; default -> throw new AssertionError("Invalid resolved direction"); }
                    }
                    check(topology.vertex(ci, cj) <= 0 && topology.route(ci, cj).rank == 0, "Coarse path misses sea terminal");
                }
                long x = i * step + Math.floorMod(Hash64.mix(trial), step), z = j * step + step / 3;
                int mask = g.fields.get(Fields.SEA_MASK, x, z);
                double height = g.fields.get(Fields.BASE_ELEVATION, x, z);
                check(Double.isFinite(height) && (height <= 0 ? 1 : 0) == mask, "Elevation contradicts land/sea commitment");
                check(mask == mountains.fields.get(Fields.SEA_MASK, x, z), "Mountain parameter changes coastline");
                for (int stride : new int[] {1, 16, 256}) {
                    Object[] tile = g.fields.values(Fields.SEA_MASK, x - stride, z, stride, 3, 1);
                    check(tile[1].equals(mask), "Coast moves with sample resolution");
                }
                // Piecewise-linear score has a finite slope on either side of shared edges.
                long a = topology.numerator(i * step - 1, z), b = topology.numerator(i * step + 1, z);
                check(Math.abs(a - b) <= 4000, "Coarse query edge introduces a coastline score jump");
            }
            for (long x : new long[] {-Lattice.MAX_COORDINATE, Lattice.MAX_COORDINATE})
                for (long z : new long[] {-Lattice.MAX_COORDINATE, Lattice.MAX_COORDINATE}) {
                    check(Double.isFinite(g.fields.get(Fields.BASE_ELEVATION, x, z)), "Edge elevation invalid");
                    check(g.fields.get(Fields.DRAINAGE_RANK, x, z) >= -1, "Edge rank invalid");
                }
        }
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Callable<Boolean>>();
            for (int n = 0; n < 48; n++) { final int i = n - 24; jobs.add(() -> allSea.route(i, -i).rank == new CoastTopology(42, pureCrust, fixture(0)).route(i, -i).rank); }
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Concurrent coast cache differs");
        }
        check(resolvedLand > 0 && sea > 0, "No coastline coverage in test corpus");
        System.out.printf("PASS COAST: exact coast/relief agreement, zoom/edge stability, independent ranks and monotone resolved paths; sampled sea=%d resolved land=%d unresolved land=%d%n", sea, resolvedLand, unresolvedLand);
    }
}
