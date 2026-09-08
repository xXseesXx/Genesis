package genesis.core.hydro;

import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Geometry consumer of the existing partial coarse graph. Not a global routing solver,
 * flux calculation, river bed, or ocean classifier. All lookups use world-cell keys.
 */
public final class CoarseChannels {
    private static final int CACHE_CELLS = 512;
    public final int spacing, distanceCap;
    private final BoundaryPorts ports;
    private final FieldRegistry inputs;
    private final Map<Key, Cell> cache = new LinkedHashMap<Key, Cell>(16, 1, true);
    private static final class Key {
        final long i, j;
        Key(long i, long j) { this.i = i; this.j = j; }
        @Override public int hashCode() { return (int) Hash64.hash(0, 0, i, j); }
        @Override public boolean equals(Object o) { return o instanceof Key && i == ((Key) o).i && j == ((Key) o).j; }
    }
    public static final class Cell {
        public final long hubX, hubZ;
        public final int direction;
        private final BoundaryPorts.Port[] crossings;
        private Cell(long x, long z, int direction, BoundaryPorts.Port[] crossings) {
            this.hubX = x; this.hubZ = z; this.direction = direction; this.crossings = crossings;
        }
        public BoundaryPorts.Port[] crossings() { return crossings.clone(); }
    }
    public CoarseChannels(long seed, Params params, FieldRegistry inputs) {
        spacing = params.integer("coarseSpacing"); distanceCap = spacing / 4;
        ports = new BoundaryPorts(seed, params); this.inputs = inputs;
    }
    private boolean allowed(long i, long j) {
        long x = Math.multiplyExact(i, spacing), z = Math.multiplyExact(j, spacing);
        return x >= -Lattice.MAX_COORDINATE && z >= -Lattice.MAX_COORDINATE
            && x <= Lattice.MAX_COORDINATE - spacing && z <= Lattice.MAX_COORDINATE - spacing;
    }
    private int direction(long i, long j) {
        if (!allowed(i, j)) return -1; // Numeric support limit is not a drainage outlet.
        int d = inputs.get(Fields.FLOW_DIRECTION, i * spacing, j * spacing);
        if (d < -1 || d > 4) throw new IllegalArgumentException("Invalid coarse flow direction");
        return d;
    }
    public synchronized Cell cell(long i, long j) {
        if (!allowed(i, j)) throw new IllegalArgumentException("Channel cell outside supported coordinate domain");
        Key key = new Key(i, j); Cell found = cache.get(key);
        if (found != null) return found;
        int d = direction(i, j);
        List<BoundaryPorts.Port> crossings = new ArrayList<BoundaryPorts.Port>();
        for (int side = 1; side <= 4; side++) {
            long ni = i + (side == 2 ? 1 : side == 4 ? -1 : 0);
            long nj = j + (side == 3 ? 1 : side == 1 ? -1 : 0);
            if (!allowed(ni, nj)) continue;
            int opposite = side <= 2 ? side + 2 : side - 2;
            if (d == side || direction(ni, nj) == opposite) crossings.add(ports.port(ports.face(0, i, j, side)));
        }
        found = new Cell(i * spacing + spacing / 2, j * spacing + spacing / 2, d,
            crossings.toArray(new BoundaryPorts.Port[crossings.size()]));
        cache.put(key, found);
        if (cache.size() > CACHE_CELLS) cache.remove(cache.keySet().iterator().next());
        return found;
    }
    public int channelDistance(long x, long z) { return distance(x, z, false); }
    public int portDistance(long x, long z) { return distance(x, z, true); }
    private int distance(long x, long z, boolean onlyPorts) {
        Lattice.check(x); Lattice.check(z);
        long i = Math.floorDiv(x, spacing), j = Math.floorDiv(z, spacing);
        long best = (long) distanceCap * distanceCap;
        // Cap < cell width: this fixed halo contains every segment/port that could matter.
        // It is independent of the requested tile size and origin.
        for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
            if (!allowed(i + di, j + dj)) continue;
            Cell c = cell(i + di, j + dj);
            for (BoundaryPorts.Port p : c.crossings) {
                long px = x - p.x, pz = z - p.z;
                long squared;
                if (onlyPorts) squared = px * px + pz * pz;
                else {
                    long ax = x - c.hubX, az = z - c.hubZ, bx = p.x - c.hubX, bz = p.z - c.hubZ;
                    long dot = ax * bx + az * bz, length = bx * bx + bz * bz;
                    if (dot <= 0) squared = ax * ax + az * az;
                    else if (dot >= length) squared = px * px + pz * pz;
                    else {
                        long cross = ax * bz - az * bx;
                        squared = cross * cross / length;
                    }
                }
                best = Math.min(best, squared);
            }
        }
        return integerSqrt(best);
    }
    private static int integerSqrt(long value) {
        long low = 0, high = 1;
        while (high * high <= value) high *= 2;
        while (low + 1 < high) {
            long mid = (low + high) / 2;
            if (mid * mid <= value) low = mid; else high = mid;
        }
        return (int) low;
    }
}
