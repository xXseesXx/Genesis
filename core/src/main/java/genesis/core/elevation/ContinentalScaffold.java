package genesis.core.elevation;

import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.LinkedHashMap;
import java.util.Map;

/** Connected macro objects, not a noise threshold. Experimental; not the drainage coast. */
public final class ContinentalScaffold {
    public static final String VERSION = "continental-scaffold-v1";
    private static final long DOMAIN = 0x434f4e54494eL;
    // Versioned shape recipe: five overlapping disks, eight turn directions in Q1024.
    private static final int Q = 1024, SCORE = 1000, LOBES = 5, CACHE = 64;
    private static final int[] DX = {1024, 724, 0, -724, -1024, -724, 0, 724};
    private static final int[] DZ = {0, 724, 1024, 724, 0, -724, -1024, -724};
    private final long seed;
    public final int scale, spacing;
    private final int coverage, radiusMinimum, radiusVariation, armStep;
    private final Map<Key, Landmass[]> cache = new LinkedHashMap<Key, Landmass[]>(16, 1, true);
    private static final class Key {
        final long i, j;
        Key(long i, long j) { this.i = i; this.j = j; }
        @Override public int hashCode() { return (int) Hash64.hash(0, 0, i, j); }
        @Override public boolean equals(Object o) { return o instanceof Key && i == ((Key)o).i && j == ((Key)o).j; }
    }
    /** Identity is the exact (i,j) tuple, not its hash. Arrays never escape. */
    public static final class Landmass {
        public final long i, j;
        public final boolean active;
        private final long[] x, z, radius;
        private Landmass(long i, long j, boolean active, long[] x, long[] z, long[] radius) {
            this.i = i; this.j = j; this.active = active; this.x = x; this.z = z; this.radius = radius;
        }
        public long centerX(int lobe) { return x[lobe]; }
        public long centerZ(int lobe) { return z[lobe]; }
        public long radius(int lobe) { return radius[lobe]; }
        public int lobes() { return LOBES; }
        public int score(long px, long pz) {
            Lattice.check(px); Lattice.check(pz);
            if (!active) return -SCORE;
            int best = -SCORE;
            for (int k = 0; k < LOBES; k++) {
                long dx = px - x[k], dz = pz - z[k], r = radius[k];
                // Reject before squaring, including arbitrarily distant supported queries.
                if (Math.abs(dx) > r * 2 || Math.abs(dz) > r * 2) continue;
                long r2 = r * r, d2 = dx * dx + dz * dz;
                best = Math.max(best, (int) Math.max(-SCORE, (r2 - d2) * SCORE / r2));
            }
            return best;
        }
    }
    public ContinentalScaffold(long seed, Params params) {
        this.seed = Hash64.stream(seed, DOMAIN);
        scale = params.integer("continentScale"); spacing = scale * 2;
        coverage = params.integer("continentCoverage");
        radiusMinimum = params.integer("continentLobeRadius");
        radiusVariation = params.integer("continentLobeVariation");
        armStep = params.integer("continentArmStep");
    }
    public Landmass landmass(long i, long j) {
        long ox = Math.multiplyExact(i, spacing), oz = Math.multiplyExact(j, spacing);
        // Internal object centers may extend beyond the numeric query limit; never clip coasts to it.
        long limit = Lattice.MAX_COORDINATE + spacing * 4L;
        if (ox < -limit || ox > limit || oz < -limit || oz > limit)
            throw new IllegalArgumentException("Landmass address outside supported neighborhood");
        long[] x = new long[LOBES], z = new long[LOBES], radius = new long[LOBES];
        x[0] = ox + spacing / 4 + Math.floorMod(Hash64.hash(seed, 1, i, j), spacing / 2);
        z[0] = oz + spacing / 4 + Math.floorMod(Hash64.hash(seed, 2, i, j), spacing / 2);
        int direction = (int) Math.floorMod(Hash64.hash(seed, 3, i, j), DX.length);
        for (int k = 0; k < LOBES; k++) {
            long h = Hash64.hash(seed, k + 4, i, j);
            radius[k] = scale * (radiusMinimum + Math.floorMod(h, radiusVariation + 1)) / SCORE;
            if (k == 0) continue;
            // Two two-link arms attached to the central lobe; each bends at most 45 degrees.
            int parent = k <= 2 ? k - 1 : k == 3 ? 0 : 3;
            int turn = (int) Math.floorMod(Hash64.mix(h), 3) - 1;
            int angle = Math.floorMod(direction + (k > 2 ? 4 : 0) + turn, DX.length);
            x[k] = x[parent] + (long) scale * armStep * DX[angle] / (SCORE * Q);
            z[k] = z[parent] + (long) scale * armStep * DZ[angle] / (SCORE * Q);
        }
        return new Landmass(i, j, Math.floorMod(Hash64.hash(seed, 0, i, j), 100) < coverage, x, z, radius);
    }
    private synchronized Landmass[] neighborhood(long i, long j) {
        Key key = new Key(i, j); Landmass[] found = cache.get(key);
        if (found != null) return found;
        found = new Landmass[9]; int index = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) found[index++] = landmass(i + dx, j + dz);
        cache.put(key, found);
        if (cache.size() > CACHE) cache.remove(cache.keySet().iterator().next());
        return found;
    }
    /** Fixed 3x3 support, independent of requests. See docs/Continental-Scaffold.md for the bound. */
    public int score(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        int best = -SCORE;
        for (Landmass land : neighborhood(Math.floorDiv(x, spacing), Math.floorDiv(z, spacing))) best = Math.max(best, land.score(x, z));
        return best;
    }
}
