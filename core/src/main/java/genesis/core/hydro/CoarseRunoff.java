package genesis.core.hydro;

import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact unit-rain accumulation on the resolved coarse graph, not a global basin solver.
 * Unknown interior runoff is explicitly excluded and propagated as an open frontier.
 * Each incoming edge increases rank by one, bounding dependencies by seaSearchRadius.
 */
public final class CoarseRunoff {
    public static final String VERSION = "coarse-runoff-v1";
    private static final int CACHE_CELLS = 4096;
    private final int spacing, radius;
    private final long minimum, maximum;
    private final FieldRegistry inputs;
    private final Map<Key, Sample> cache = new LinkedHashMap<Key, Sample>(16, 1, true);
    private static final class Key {
        final long i, j;
        Key(long i, long j) { this.i = i; this.j = j; }
        @Override public int hashCode() { return (int) Hash64.hash(0, 0, i, j); }
        @Override public boolean equals(Object o) { return o instanceof Key && i == ((Key)o).i && j == ((Key)o).j; }
    }
    public static final class Sample {
        /** -1 unresolved; otherwise one unit per resolved upstream land anchor, including self. */
        public final int units;
        /** -1 unresolved; 0 closed under current routing; 1 touches unknown/numeric support. */
        public final int status;
        private Sample(int units, int status) { this.units = units; this.status = status; }
    }
    public CoarseRunoff(Params params, FieldRegistry inputs) {
        spacing = params.integer("coarseSpacing"); radius = params.integer("seaSearchRadius");
        maximum = Lattice.MAX_COORDINATE / spacing; minimum = -maximum; this.inputs = inputs;
    }
    private boolean allowed(long i, long j) { return i >= minimum && i <= maximum && j >= minimum && j <= maximum; }
    private int rank(long i, long j) {
        int r = inputs.get(Fields.DRAINAGE_RANK, i * spacing, j * spacing);
        if (r < -1 || r > radius) throw new IllegalArgumentException("Coarse rank outside committed radius");
        return r;
    }
    private int direction(long i, long j, int r) {
        int d = inputs.get(Fields.FLOW_DIRECTION, i * spacing, j * spacing);
        if (r < 0 ? d != -1 : r == 0 ? d != 0 : d < 1 || d > 4)
            throw new IllegalArgumentException("Coarse rank/direction disagreement");
        return d;
    }
    public Sample at(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        // Same inward anchor convention as CoastTopology.at for non-dividing spacing.
        return cell(Math.max(minimum, Math.floorDiv(x, spacing)), Math.max(minimum, Math.floorDiv(z, spacing)));
    }
    public synchronized Sample cell(long i, long j) {
        if (!allowed(i, j)) throw new IllegalArgumentException("Runoff anchor outside supported domain");
        Key key = new Key(i, j); Sample found = cache.get(key);
        if (found != null) return found;
        int r = rank(i, j), d = direction(i, j, r);
        int units = r < 0 ? -1 : r == 0 ? 0 : 1, status = r < 0 ? -1 : 0;
        if (r >= 0) for (int side = 1; side <= 4; side++) {
            long ni = i + (side == 2 ? 1 : side == 4 ? -1 : 0);
            long nj = j + (side == 3 ? 1 : side == 1 ? -1 : 0);
            if (!allowed(ni, nj)) { status = 1; continue; } // Never a numeric-edge outlet.
            int nr = rank(ni, nj), nd = direction(ni, nj, nr);
            if (side == d && nr != r - 1) throw new IllegalArgumentException("Outgoing coarse rank must decrease by one");
            if (nr < 0) { status = 1; continue; }
            int opposite = side <= 2 ? side + 2 : side - 2;
            if (nd != opposite) continue;
            if (nr != r + 1) throw new IllegalArgumentException("Incoming coarse rank must increase by one");
            Sample upstream = cell(ni, nj);
            units = Math.addExact(units, upstream.units);
            status = Math.max(status, upstream.status);
        }
        // Reverse paths cannot leave the Manhattan diamond of radius (R-r).
        if (r >= 0 && units > 1 + 2 * (radius - r) * (radius - r + 1))
            throw new IllegalStateException("Runoff exceeds finite dependency bound");
        found = new Sample(units, status); cache.put(key, found);
        if (cache.size() > CACHE_CELLS) cache.remove(cache.keySet().iterator().next());
        return found;
    }
}
