package genesis.oracle;

import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.oracle.MaritimeEnvelope.Key;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/** Full canonical-cell research realization with mandatory shared ridge land.
 * Coverage refers to ALL owned mesh vertices, including those reserved for ocean.
 * No river routing, physical ridge-height guarantee, or production-world adoption is implied.
 */
public final class MixedBoundaryLandmass {
    public static final int SIDE = 193;
    private static final long DOMAIN = 0x4d49584c414e44L;
    private record Entry(int index, long distance) {}
    private final boolean[] land;
    private final byte[] water;
    private final int[] ridge;
    public final Key owner;
    public final long x0, z0;
    public final int step, ownedCount, eligibleCount, mandatoryCount, landCount;
    public MixedBoundaryLandmass(long seed, int spacing, int collar, Key owner, int percent) {
        if (owner == null || percent < 1 || percent > 90) throw new IllegalArgumentException("Owner and coverage 1..90 required");
        var envelope = new MixedBoundaryEnvelope(seed, spacing, collar);
        var bounds = envelope.bounds(owner);
        Lattice.check(bounds.x0()); Lattice.check(bounds.z0()); Lattice.check(bounds.x1()); Lattice.check(bounds.z1());
        this.owner = owner; x0 = bounds.x0(); z0 = bounds.z0(); step = spacing / 64;
        int n = SIDE * SIDE; land = new boolean[n]; water = new byte[n]; ridge = new int[n];
        boolean[] own = new boolean[n], ocean = new boolean[n], eligible = new boolean[n];
        int[] cost = new int[n]; long[] distance = new long[n]; Arrays.fill(distance, Long.MAX_VALUE);
        var queue = new PriorityQueue<Entry>(Comparator.comparingLong(Entry::distance).thenComparingInt(Entry::index));
        int owned = 0, safe = 0, mandatory = 0, root = -1; long closest = Long.MAX_VALUE;
        var site = envelope.site(owner); long recipe = Hash64.stream(seed, DOMAIN);
        for (int p = 0; p < n; p++) {
            long x = x(p), z = z(p); var s = envelope.sample(x, z);
            own[p] = s.owner().equals(owner); ocean[p] = s.ocean(); ridge[p] = s.ridgeStrength();
            eligible[p] = own[p] && !ocean[p]; if (own[p]) owned++;
            if (eligible[p]) {
                safe++;
                long dx = x - site.x(), dz = z - site.z(), d = dx * dx + dz * dz;
                if (d < closest) { closest = d; root = p; }
                if (ridge[p] > 0) { mandatory++; distance[p] = 0; queue.add(new Entry(p,0)); }
            }
            cost[p] = growthCost(recipe, Math.floorDiv(x, step), Math.floorDiv(z, step));
        }
        ownedCount = owned; eligibleCount = safe; mandatoryCount = mandatory;
        landCount = Math.max(1, owned * percent / 100);
        if (landCount < mandatory || landCount > safe || root < 0)
            throw new IllegalArgumentException("Coverage cannot satisfy mandatory ridge/ocean constraints");
        if (queue.isEmpty()) { distance[root] = 0; queue.add(new Entry(root,0)); }
        int[] order = new int[n]; int count = 0;
        while (!queue.isEmpty()) {
            var e = queue.remove(); int p = e.index;
            if (distance[p] != e.distance) continue;
            order[count++] = p;
            for (int q : neighbors(p)) if (eligible[q]) {
                long candidate = distance[p] + cost[p] + cost[q];
                if (candidate < distance[q]) { distance[q] = candidate; queue.add(new Entry(q,candidate)); }
            }
        }
        if (count < landCount) throw new IllegalArgumentException("Coverage exceeds reachable safe mesh");
        for (int k = 0; k < landCount; k++) land[order[k]] = true;
        ArrayDeque<Integer> flood = new ArrayDeque<>();
        for (int p = 0; p < n; p++) {
            water[p] = land[p] ? 0 : (byte) (own[p] || ocean[p] ? 2 : 3);
            if (ocean[p]) { water[p] = 1; flood.add(p); }
        }
        while (!flood.isEmpty()) for (int q : neighbors(flood.removeFirst())) if (water[q] == 2) {
            water[q] = 1; flood.addLast(q);
        }
    }
    private static int growthCost(long seed, long x, long z) {
        long i = Math.floorDiv(x,8), j = Math.floorDiv(z,8); int fx = Math.floorMod(x,8), fz = Math.floorMod(z,8);
        long value = 0;
        for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++)
            value += Math.floorMod(Hash64.hash(seed,0,i+dx,j+dz),256) * (dx == 0 ? 8-fx : fx) * (dz == 0 ? 8-fz : fz);
        int smooth = (int) (value / 64); return 8 + smooth * smooth / 128;
    }
    private void check(int p) { if (p < 0 || p >= land.length) throw new IllegalArgumentException("Outside canonical mesh"); }
    public int size() { return land.length; }
    public long x(int p) { check(p); return x0 + p % SIDE * (long) step; }
    public long z(int p) { check(p); return z0 + p / SIDE * (long) step; }
    public boolean land(int p) { check(p); return land[p]; }
    public int water(int p) { check(p); return water[p]; }
    public int ridge(int p) { check(p); return ridge[p]; }
    public int index(long x, long z) {
        if (x < x0 || z < z0 || x > x0 + (SIDE-1L)*step || z > z0 + (SIDE-1L)*step
            || Math.floorMod(x-x0,step) != 0 || Math.floorMod(z-z0,step) != 0)
            throw new IllegalArgumentException("Expected canonical world mesh coordinate");
        return (int) ((z-z0)/step*SIDE+(x-x0)/step);
    }
    private static int[] neighbors(int p) {
        int[] q = new int[4]; int n = 0, x = p % SIDE, z = p / SIDE;
        if (x > 0) q[n++] = p-1; if (x < SIDE-1) q[n++] = p+1;
        if (z > 0) q[n++] = p-SIDE; if (z < SIDE-1) q[n++] = p+SIDE;
        return Arrays.copyOf(q,n);
    }
}
