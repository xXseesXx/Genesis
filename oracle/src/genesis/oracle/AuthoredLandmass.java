package genesis.oracle;

import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/** R2b finite domain-aware growth experiment. Not production continents or river routing.
 * A complete canonical 3S box is evaluated; requested image bounds never enter construction.
 * The connected prefix of positive-cost shortest paths defines land; remaining water is
 * classified only by paths to explicit maritime-reserve samples, never arbitrary box edges.
 */
public final class AuthoredLandmass {
    public static final String VERSION = "authored-landmass-v1";
    public static final int CELLS_PER_SPACING = 64;
    public static final int SIDE = 3 * CELLS_PER_SPACING + 1;
    private static final long DOMAIN = 0x415554484c414e44L;
    private final boolean[] land, eligible;
    private final byte[] water;
    private final int[] growthParent;
    private final long[] distance;
    public final long x0, z0;
    public final int step, seedIndex, eligibleCount, reachableCount, landCount, percent;
    public final MaritimeEnvelope.Key owner;

    private record Entry(int index, long distance) {}

    public AuthoredLandmass(long seed, int spacing, int collar, MaritimeEnvelope.Key owner, int percent) {
        if (owner == null || percent < 1 || percent > 90 || collar < spacing / CELLS_PER_SPACING)
            throw new IllegalArgumentException("Explicit domain, coverage 1..90 and collar >= coarse step required");
        var envelope = new MaritimeEnvelope(seed, spacing, collar);
        var bounds = envelope.bounds(owner);
        // Fail closed at numeric support. No clipped partial root or synthetic coast at the guard.
        Lattice.check(bounds.x0()); Lattice.check(bounds.z0()); Lattice.check(bounds.x1()); Lattice.check(bounds.z1());
        this.owner = owner; this.percent = percent; step = spacing / CELLS_PER_SPACING;
        x0 = bounds.x0(); z0 = bounds.z0();
        int n = SIDE * SIDE;
        land = new boolean[n]; eligible = new boolean[n]; water = new byte[n];
        growthParent = new int[n]; distance = new long[n];
        Arrays.fill(growthParent, -1); Arrays.fill(distance, Long.MAX_VALUE);
        boolean[] reserve = new boolean[n], own = new boolean[n];
        int[] costs = new int[n]; long recipeSeed = Hash64.stream(seed, DOMAIN);
        var site = envelope.site(owner.i(), owner.j());
        int root = -1, total = 0; long closest = Long.MAX_VALUE;
        for (int p = 0; p < n; p++) {
            long x = x(p), z = z(p); var sample = envelope.sample(x, z);
            own[p] = sample.owner().equals(owner); reserve[p] = sample.reservedOcean();
            eligible[p] = own[p] && !reserve[p];
            if (eligible[p]) {
                total++;
                long dx = x - site.x(), dz = z - site.z(), d2 = dx * dx + dz * dz;
                if (d2 < closest) { closest = d2; root = p; }
            }
            costs[p] = growthCost(recipeSeed, Math.floorDiv(x, step), Math.floorDiv(z, step));
        }
        if (root < 0) throw new IllegalStateException("Canonical domain has no safe lattice seed");
        seedIndex = root; eligibleCount = total;
        // Reproducible queue ordering; positive edge cost makes every receiver earlier in order.
        var queue = new PriorityQueue<Entry>(Comparator.comparingLong(Entry::distance).thenComparingInt(Entry::index));
        int[] order = new int[n]; int count = 0;
        distance[root] = 0; queue.add(new Entry(root, 0));
        while (!queue.isEmpty()) {
            Entry entry = queue.remove(); int p = entry.index();
            if (entry.distance() != distance[p]) continue;
            order[count++] = p;
            for (int q : neighbors(p)) if (eligible[q]) {
                long candidate = distance[p] + costs[p] + costs[q];
                if (candidate < distance[q]) {
                    distance[q] = candidate; growthParent[q] = p; queue.add(new Entry(q, candidate));
                }
            }
        }
        reachableCount = count;
        landCount = Math.max(1, count * percent / 100);
        for (int k = 0; k < landCount; k++) land[order[k]] = true;
        // Classification codes: 0 land, 1 certified ocean-connected water, 2 interior unconnected
        // water, 3 outside this domain's modeled non-reserve interior. No outer-edge fallback.
        ArrayDeque<Integer> flood = new ArrayDeque<>();
        for (int p = 0; p < n; p++) {
            water[p] = land[p] ? 0 : (byte) (own[p] || reserve[p] ? 2 : 3);
            if (reserve[p]) { water[p] = 1; flood.addLast(p); }
        }
        while (!flood.isEmpty()) {
            int p = flood.removeFirst();
            for (int q : neighbors(p)) if (water[q] == 2) { water[q] = 1; flood.addLast(q); }
        }
    }
    private static int growthCost(long seed, long x, long z) {
        // Broad integer bilinear preference, not heights or a claimed tectonic model.
        long i = Math.floorDiv(x, 8), j = Math.floorDiv(z, 8);
        int fx = Math.floorMod(x, 8), fz = Math.floorMod(z, 8);
        long value = 0;
        for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) {
            int weight = (dx == 0 ? 8 - fx : fx) * (dz == 0 ? 8 - fz : fz);
            value += Math.floorMod(Hash64.hash(seed, 0, i + dx, j + dz), 256) * weight;
        }
        int smooth = (int) (value / 64);
        return 8 + smooth * smooth / 128;
    }
    private void checkIndex(int p) { if (p < 0 || p >= land.length) throw new IllegalArgumentException("Outside canonical raster"); }
    public int size() { return land.length; }
    public long x(int p) { checkIndex(p); return x0 + p % SIDE * (long) step; }
    public long z(int p) { checkIndex(p); return z0 + p / SIDE * (long) step; }
    public boolean land(int p) { checkIndex(p); return land[p]; }
    public boolean eligible(int p) { checkIndex(p); return eligible[p]; }
    public int water(int p) { checkIndex(p); return water[p]; }
    public int growthParent(int p) { checkIndex(p); return growthParent[p]; }
    public long distance(int p) { checkIndex(p); return distance[p]; }
    public boolean landAt(long x, long z) {
        if (x < x0 || z < z0 || x > x0 + (SIDE - 1L) * step || z > z0 + (SIDE - 1L) * step
            || Math.floorMod(x - x0, step) != 0 || Math.floorMod(z - z0, step) != 0)
            throw new IllegalArgumentException("Query must name a canonical sample in this complete domain");
        return land[(int) ((z - z0) / step * SIDE + (x - x0) / step)];
    }
    private static int[] neighbors(int p) {
        int x = p % SIDE, z = p / SIDE; int[] result = new int[4]; int count = 0;
        if (x > 0) result[count++] = p - 1;
        if (x + 1 < SIDE) result[count++] = p + 1;
        if (z > 0) result[count++] = p - SIDE;
        if (z + 1 < SIDE) result[count++] = p + SIDE;
        return Arrays.copyOf(result, count);
    }
}
