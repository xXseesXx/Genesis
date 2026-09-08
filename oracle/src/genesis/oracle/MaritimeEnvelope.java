package genesis.oracle;

import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;

/** R2a experimental geographic restriction, NOT the production continent mask.
 * Bounded jittered Voronoi domains reserve an ocean collar around their boundaries.
 * A domain can contain many land components and watersheds; its ID is not a basin ID.
 */
public final class MaritimeEnvelope {
    public static final String VERSION = "maritime-envelope-v1";
    private static final long DOMAIN = 0x4d41524954494dL;
    public record Key(long i, long j) implements Comparable<Key> {
        @Override public int compareTo(Key other) {
            int c = Long.compare(i, other.i);
            return c == 0 ? Long.compare(j, other.j) : c;
        }
    }
    public record Site(Key key, long x, long z) {}
    public record Sample(Key owner, boolean reservedOcean, int sitesRead) {}
    public record Bounds(long x0, long z0, long x1, long z1) {}
    private final long seed;
    public final int spacing, collar;

    public MaritimeEnvelope(long seed, int spacing, int collar) {
        if (spacing < 64 || spacing > 1_048_576 || spacing % 64 != 0 || collar < 1 || collar > spacing / 8)
            throw new IllegalArgumentException("Spacing must be a multiple of 64 in [64,1048576]; collar in [1,S/8]");
        this.seed = Hash64.stream(seed, DOMAIN); this.spacing = spacing; this.collar = collar;
    }
    /** Internal sites may lie beyond supported query coordinates; never clip a coast to that guard. */
    public Site site(long i, long j) {
        long x = Math.addExact(Math.multiplyExact(i, spacing), spacing / 4L
            + Math.floorMod(Hash64.hash(seed, 0, i, j), spacing / 2));
        long z = Math.addExact(Math.multiplyExact(j, spacing), spacing / 4L
            + Math.floorMod(Hash64.hash(seed, 1, i, j), spacing / 2));
        return new Site(new Key(i, j), x, z);
    }
    /** Every point owned by this site lies strictly inside this 3S by 3S box.
     * This is a computation bound, not a coastline or a set of artificial terminal cells.
     */
    public Bounds bounds(Key key) {
        if (key == null) throw new IllegalArgumentException("Missing domain identity");
        return new Bounds(Math.multiplyExact(Math.subtractExact(key.i(), 1), spacing),
            Math.multiplyExact(Math.subtractExact(key.j(), 1), spacing),
            Math.multiplyExact(Math.addExact(key.i(), 2), spacing),
            Math.multiplyExact(Math.addExact(key.j(), 2), spacing));
    }
    public Sample sample(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        long i = Math.floorDiv(x, spacing), j = Math.floorDiv(z, spacing);
        Site[] sites = new Site[25];
        Site nearest = null; long best = Long.MAX_VALUE; int count = 0;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            Site candidate = site(i + dx, j + dz); sites[count++] = candidate;
            long d2 = distanceSquared(x, z, candidate);
            if (d2 < best || (d2 == best && (nearest == null || candidate.key().compareTo(nearest.key()) < 0))) {
                best = d2; nearest = candidate;
            }
        }
        boolean reserve = false;
        for (Site candidate : sites) if (!candidate.key().equals(nearest.key())) {
            long gap = distanceSquared(x, z, candidate) - best;
            long siteL1 = Math.abs(candidate.x() - nearest.x()) + Math.abs(candidate.z() - nearest.z());
            // Exact condition for the entire +/-collar axis-aligned box to stay in this cell.
            // It avoids floating square roots and also reserves exact ownership ties as ocean.
            if (gap <= 2L * collar * siteL1) { reserve = true; break; }
        }
        return new Sample(nearest.key(), reserve, count);
    }
    private static long distanceSquared(long x, long z, Site site) {
        long dx = x - site.x(), dz = z - site.z();
        // Queried candidates are within the fixed 5x5 support; S <= 2^20 bounds all products.
        return dx * dx + dz * dz;
    }
}
