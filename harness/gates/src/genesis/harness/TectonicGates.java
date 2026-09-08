package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.fields.Fields;
import genesis.core.fields.TileCache;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.core.tectonics.PlateTopology;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.ArrayList;

/** Independent larger-window geometry reference and metamorphic M1 checks. */
public final class TectonicGates {
    private TectonicGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run() throws Exception {
        for (long n : new long[] {0, 1, 2, 3, 4, 999999999999L, Long.MAX_VALUE})
            check(PlateTopology.sqrt(n) == BigInteger.valueOf(n).sqrt().longValueExact(), "Integer sqrt reference");
        Set<Integer> kinds = new HashSet<>();
        double maxGradient = 0;
        for (long seed : new long[] {0, 1, -1, 42, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (int spacing : new int[] {8192, 16384, 262144}) {
                Params params = new Params(Map.of("plateSpacing", (double) spacing));
                PlateTopology topology = new PlateTopology(seed, params);
                Generator g = new Generator(seed, params);
                for (int trial = 0; trial < 128; trial++) {
                    long x = Math.floorMod(Hash64.hash(seed, 0, trial, 0), 8L * spacing) - 4L * spacing;
                    long z = Math.floorMod(Hash64.hash(seed, 0, trial, 1), 8L * spacing) - 4L * spacing;
                    if (trial == 0) { x = Lattice.MAX_COORDINATE; z = -Lattice.MAX_COORDINATE; }
                    var sample = topology.sample(x, z);
                    long ci = Math.floorDiv(x, spacing), cj = Math.floorDiv(z, spacing);
                    PlateTopology.Site nearest = null;
                    long best = Long.MAX_VALUE;
                    for (int row = -5; row <= 5; row++) for (int col = -5; col <= 5; col++) {
                        var site = topology.site(ci + col, cj + row);
                        long d = PlateTopology.squared(site, x, z);
                        if (d < best || (d == best && PlateTopology.precedes(site, nearest))) { best = d; nearest = site; }
                    }
                    check(nearest.id == sample.owner.id, "Owner differs from 11x11 reference");
                    double boundary = Double.POSITIVE_INFINITY;
                    for (int row = -5; row <= 5; row++) for (int col = -5; col <= 5; col++) {
                        var b = topology.site(ci + col, cj + row);
                        if (b.id == nearest.id) continue;
                        long dx = b.x - nearest.x, dz = b.z - nearest.z;
                        double distance = (PlateTopology.squared(b, x, z) - best) / (2 * StrictMath.sqrt(dx * dx + dz * dz));
                        boundary = Math.min(boundary, distance);
                    }
                    check(Math.abs(sample.distanceQ / 256.0 - boundary) < .02, "Boundary distance differs from larger geometric reference");
                    check(sample.boundaryType == topology.classify(sample.neighbor, sample.owner), "Boundary classification asymmetric");
                    kinds.add(sample.boundaryType);
                    long sub = g.fields.get(Fields.SUB_PLATE_ID, x, z);
                    check(sub == topology.child(sample.owner, x, z), "Child not conditioned on parent");
                    double uplift = g.fields.get(Fields.UPLIFT, x, z);
                    check(Double.isFinite(uplift), "Non-finite uplift");
                    if (x < Lattice.MAX_COORDINATE) {
                        double gradient = Math.abs(g.fields.get(Fields.UPLIFT, x + 1, z) - uplift);
                        maxGradient = Math.max(maxGradient, gradient * spacing);
                        check(gradient <= 64.0 / spacing, "Uplift gradient exceeds M1 bound");
                    }
                }
                // Query-cell borders and forced memo eviction; values agree with a fresh generator.
                for (int i = -40; i <= 40; i++) {
                    long x = i * (long) spacing;
                    double a = g.fields.get(Fields.UPLIFT, x - 1, -1), b = g.fields.get(Fields.UPLIFT, x, -1);
                    check(Math.abs(a - b) <= 64.0 / spacing, "Uplift discontinuity at lattice border");
                    check(g.fields.get(Fields.PLATE_ID, x, -1).equals(new Generator(seed, params).fields.get(Fields.PLATE_ID, x, -1)), "Memo eviction changed ownership");
                }
            }
        }
        check(kinds.size() == 3, "Did not exercise every boundary class");
        for (int continent : new int[] {0, 100}) {
            Params extreme = new Params(Map.of("plateSpacing", 8192.0, "subdivisions", 8.0, "plateSpeed", 128.0,
                "plateJitter", (double) (continent / 2), "continentalPercent", (double) continent, "upliftRadius", 1.1, "upliftStrength", 2.0,
                "crustBlendRadius", continent == 0 ? 1100.0 : 2500.0));
            Generator g = new Generator(137, extreme);
            for (long x : new long[] {-Lattice.MAX_COORDINATE, -8193, -1, 0, 8191, Lattice.MAX_COORDINATE})
                for (long z : new long[] {-Lattice.MAX_COORDINATE, -1, 0, Lattice.MAX_COORDINATE}) {
                    g.fields.get(Fields.SUB_PLATE_ID, x, z);
                    check(g.fields.get(Fields.CRUST_TYPE, x, z) == continent / 100, "Crust probability endpoints ignored");
                    check(g.fields.get(Fields.CRUST_FRACTION, x, z) == continent * 10, "Integer crust blend loses constant-field endpoints");
                    check(Math.abs(g.fields.get(Fields.VELOCITY_X, x, z)) <= 128, "Velocity out of range");
                    check(Double.isFinite(g.fields.get(Fields.UPLIFT, x, z)), "Extreme params yield non-finite uplift");
                }
        }
        Generator base = new Generator(42, Params.defaults());
        Generator refined = new Generator(42, new Params(Map.of("subdivisions", 8.0)));
        Generator noUplift = new Generator(42, new Params(Map.of("upliftStrength", 0.0)));
        for (int i = -64; i <= 64; i++) {
            long x = i * 2047L, z = -i * 4093L;
            check(base.fields.get(Fields.PLATE_ID, x, z).equals(refined.fields.get(Fields.PLATE_ID, x, z)), "Sub-plate refinement moves parent");
            check(base.fields.get(Fields.UPLIFT, x, z).equals(refined.fields.get(Fields.UPLIFT, x, z)), "Sub-plate refinement changes coarse uplift");
            check(noUplift.fields.get(Fields.UPLIFT, x, z) == 0, "Uplift forcing ignored");
        }
        Params regular = new Params(Map.of("plateJitter", 0.0));
        PlateTopology grid = new PlateTopology(42, regular);
        var edge = grid.sample(0, 8192);
        check(edge.distanceQ == 0, "Regular Voronoi edge should have zero distance");
        check(grid.sample(-1, 8192).boundaryType == grid.sample(1, 8192).boundaryType, "Boundary differs on opposite sides");
        TileCache cache = new TileCache(base, 4);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 96; i++) {
                final long x = (i - 48) * 16384L;
                jobs.add(() -> Arrays.equals(cache.values(Fields.PLATE_ID, x, -1, 17, 16, 16), new Generator(42, Params.defaults()).fields.values(Fields.PLATE_ID, x, -1, 17, 16, 16)));
            }
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Concurrent plate/ID cache mismatch");
        }
        System.out.printf("PASS TECT: larger-window ownership/distance reference, symmetric classes, exact IDs, refinement, eviction/concurrency; max sampled gradient*S %.3f%n", maxGradient);
    }
}
