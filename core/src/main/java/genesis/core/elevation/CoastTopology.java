package genesis.core.elevation;

import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.LinkedHashMap;
import java.util.Map;

/** Integer-only coarse coast commitments and bounded, explicitly partial sea-distance queries. */
public final class CoastTopology {
    private static final long DOMAIN = 0x434f415354L;
    private static final int SCALE = 1000;
    private static final int VERTEX_CACHE = 8192;
    private static final int ROUTE_CACHE = 4096;
    public final int spacing;
    private final long seed;
    private final Params params;
    private final FieldRegistry inputs;
    private final Map<Key, Integer> scores = new LinkedHashMap<Key, Integer>(16, 1, true);
    private final Map<Key, Route> routes = new LinkedHashMap<Key, Route>(16, 1, true);

    private static final class Key {
        final long i, j;
        Key(long i, long j) { this.i = i; this.j = j; }
        @Override public int hashCode() { return (int) Hash64.hash(0, 0, i, j); }
        @Override public boolean equals(Object other) { return other instanceof Key && i == ((Key) other).i && j == ((Key) other).j; }
    }
    public static final class Route {
        public final int rank, direction;
        public final long terminalI, terminalJ;
        private Route(int rank, int direction, long i, long j) {
            this.rank = rank; this.direction = direction; this.terminalI = i; this.terminalJ = j;
        }
    }
    public CoastTopology(long seed, Params params, FieldRegistry inputs) {
        this.seed = Hash64.stream(seed, DOMAIN); this.params = params; this.inputs = inputs;
        this.spacing = params.integer("coarseSpacing");
    }
    private static long bounded(long coordinate) { return Math.max(-Lattice.MAX_COORDINATE, Math.min(Lattice.MAX_COORDINATE, coordinate)); }
    private int variation(long x, long z) {
        int period = params.integer("continentScale");
        long i = Math.floorDiv(x, period), j = Math.floorDiv(z, period);
        long u = Math.floorMod(x, period), v = Math.floorMod(z, period);
        long a = Math.floorMod(Hash64.hash(seed, 0, i, j), SCALE + 1);
        long b = Math.floorMod(Hash64.hash(seed, 0, i + 1, j), SCALE + 1);
        long c = Math.floorMod(Hash64.hash(seed, 0, i, j + 1), SCALE + 1);
        long d = Math.floorMod(Hash64.hash(seed, 0, i + 1, j + 1), SCALE + 1);
        // Products stay below 1000 * continentScale^2 < 2^50.
        return (int) (((a * (period - u) + b * u) * (period - v) + (c * (period - u) + d * u) * v) / ((long) period * period));
    }
    public synchronized int vertex(long i, long j) {
        Key key = new Key(i, j);
        Integer cached = scores.get(key);
        if (cached != null) return cached;
        long x = Math.multiplyExact(i, spacing), z = Math.multiplyExact(j, spacing);
        int crust = inputs.get(Fields.CRUST_FRACTION, bounded(x), bounded(z));
        int influence = params.integer("crustInfluence");
        int score = (crust * influence + variation(x, z) * (SCALE - influence)) / SCALE - params.integer("seaThreshold");
        scores.put(key, score);
        if (scores.size() > VERTEX_CACHE) scores.remove(scores.keySet().iterator().next());
        return score;
    }
    /** Integer barycentric numerator. Sign is exact; no floating value decides land/sea. */
    public long numerator(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        long i = Math.floorDiv(x, spacing), j = Math.floorDiv(z, spacing);
        long u = Math.floorMod(x, spacing), v = Math.floorMod(z, spacing);
        long a = vertex(i, j), b = vertex(i + 1, j), c = vertex(i, j + 1), d = vertex(i + 1, j + 1);
        boolean diagonal = (Hash64.hash(seed, 1, i, j) & 1) == 0;
        if (diagonal) {
            if (u >= v) return a * (spacing - u) + b * (u - v) + d * v;
            return a * (spacing - v) + c * (v - u) + d * u;
        }
        if (u + v <= spacing) return a * (spacing - u - v) + b * u + c * v;
        return b * (spacing - v) + c * (spacing - u) + d * (u + v - spacing);
    }
    private boolean allowed(long i, long j) {
        long x = Math.multiplyExact(i, spacing), z = Math.multiplyExact(j, spacing);
        return x >= -Lattice.MAX_COORDINATE && x <= Lattice.MAX_COORDINATE && z >= -Lattice.MAX_COORDINATE && z <= Lattice.MAX_COORDINATE;
    }
    public synchronized Route route(long i, long j) {
        if (!allowed(i, j)) throw new IllegalArgumentException("Routing anchor outside supported domain");
        Key key = new Key(i, j);
        Route found = routes.get(key);
        if (found != null) return found;
        int rank = -1;
        long targetI = i, targetJ = j, bestHash = 0;
        for (int radius = 0; radius <= params.integer("seaSearchRadius"); radius++) {
            boolean any = false;
            for (int dx = -radius; dx <= radius; dx++) {
                int dz = radius - Math.abs(dx);
                for (int sign = -1; sign <= 1; sign += 2) {
                    if (dz == 0 && sign == 1) continue;
                    long ci = i + dx, cj = j + sign * dz;
                    if (!allowed(ci, cj) || vertex(ci, cj) > 0) continue;
                    long h = Hash64.hash(seed, 2, ci, cj);
                    int order = Long.compareUnsigned(h, bestHash);
                    if (!any || order < 0 || (order == 0 && (ci < targetI || (ci == targetI && cj < targetJ)))) {
                        any = true; targetI = ci; targetJ = cj; bestHash = h;
                    }
                }
            }
            if (any) { rank = radius; break; }
        }
        int direction = -1;
        if (rank == 0) direction = 0;
        else if (rank > 0) {
            boolean alongX = targetJ == j || (targetI != i && (Hash64.hash(seed, 3, i, j) & 1) == 0);
            direction = alongX ? (targetI > i ? 2 : 4) : (targetJ > j ? 3 : 1);
        }
        found = new Route(rank, direction, targetI, targetJ);
        routes.put(key, found);
        if (routes.size() > ROUTE_CACHE) routes.remove(routes.keySet().iterator().next());
        return found;
    }
    public Route at(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        // At the negative edge with non-dividing spacing, clamp the anchor inward.
        long minimum = -Math.floorDiv(Lattice.MAX_COORDINATE, spacing);
        return route(Math.max(minimum, Math.floorDiv(x, spacing)), Math.max(minimum, Math.floorDiv(z, spacing)));
    }
}
