package genesis.oracle;

import genesis.core.hash.Lattice;
import genesis.oracle.MaritimeEnvelope.Key;
import genesis.oracle.MaritimeEnvelope.Site;

/** Research boundary contracts: disjoint mutual-nearest pairs share RIDGE, all other faces OCEAN.
 * This preserves bounded maritime groups, but is only a two-cell land-connection experiment.
 * RIDGE names a no-transfer constraint to realize in future terrain, not a solved elevation field.
 */
public final class MixedBoundaryEnvelope {
    public static final String VERSION = "mixed-boundary-v1";
    public enum Type { RIDGE, OCEAN }
    public record Edge(Key first, Key second) {
        public Edge {
            if (first == null || second == null || first.equals(second)) throw new IllegalArgumentException("Distinct edge endpoints required");
            if (first.compareTo(second) > 0) { Key swap = first; first = second; second = swap; }
        }
    }
    public record Sample(Key owner, Key partner, boolean ocean, int ridgeStrength) {}
    private final MaritimeEnvelope sites;
    public final int spacing, collar;
    public MixedBoundaryEnvelope(long seed, int spacing, int collar) {
        sites = new MaritimeEnvelope(seed, spacing, collar);
        if (collar < spacing / 64) throw new IllegalArgumentException("Collar must resolve the canonical mesh");
        this.spacing = spacing; this.collar = collar;
    }
    public Site site(Key key) { return sites.site(key.i(), key.j()); }
    public MaritimeEnvelope.Bounds bounds(Key key) { return sites.bounds(key); }
    /** Nearest among other sites. Fixed support is sufficient for middle-half jitter. */
    public Key nearest(Key key) {
        Site center = site(key); Key best = null; long distance = Long.MAX_VALUE;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            if (dx == 0 && dz == 0) continue;
            Site other = site(new Key(Math.addExact(key.i(), dx), Math.addExact(key.j(), dz)));
            long d = squared(center.x(), center.z(), other);
            if (d < distance || d == distance && (best == null || other.key().compareTo(best) < 0)) {
                best = other.key(); distance = d;
            }
        }
        return best;
    }
    public Key partner(Key key) { Key other = nearest(key); return nearest(other).equals(key) ? other : null; }
    public Type type(Edge edge) { return edge.second.equals(partner(edge.first)) ? Type.RIDGE : Type.OCEAN; }
    public Sample sample(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        long i = Math.floorDiv(x, spacing), j = Math.floorDiv(z, spacing);
        Site[] candidates = new Site[25]; Site owner = null; long best = Long.MAX_VALUE; int k = 0;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            Site candidate = site(new Key(i + dx, j + dz)); candidates[k++] = candidate;
            long d = squared(x, z, candidate);
            if (d < best || d == best && (owner == null || candidate.key().compareTo(owner.key()) < 0)) {
                owner = candidate; best = d;
            }
        }
        Key partner = partner(owner.key()); boolean ocean = false; int ridge = 0;
        for (Site candidate : candidates) if (!candidate.key().equals(owner.key())) {
            long gap = squared(x, z, candidate) - best;
            long l1 = Math.abs(candidate.x() - owner.x()) + Math.abs(candidate.z() - owner.z());
            long limit = 2L * collar * l1;
            if (candidate.key().equals(partner)) {
                if (gap < limit) ridge = (int) ((limit - gap) * 1024 / limit);
            } else if (gap <= limit) ocean = true;
        }
        // Ocean wins at a ridge/ocean junction: never dam the outer ocean network with a vertex cap.
        return new Sample(owner.key(), partner, ocean, ocean ? 0 : ridge);
    }
    private static long squared(long x, long z, Site site) {
        long dx = x - site.x(), dz = z - site.z(); return dx * dx + dz * dz;
    }
}
