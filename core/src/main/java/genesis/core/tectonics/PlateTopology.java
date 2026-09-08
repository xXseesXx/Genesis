package genesis.core.tectonics;

import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.LinkedHashMap;
import java.util.Map;

/** Integer-only plate decisions. See docs/Tectonics.md for bounds and fixed-point units. */
public final class PlateTopology {
    public static final int Q = 256;
    private static final int RADIUS = 3;
    private static final int SIDE = 7;
    private static final int CACHE_CELLS = 64;
    private static final long DOMAIN = 0x504c415445L;
    private final long seed;
    private final Params params;
    public final int spacing;
    private final Map<Long, Neighborhood> cache = new LinkedHashMap<Long, Neighborhood>(16, 1, true);

    public static final class Site {
        public final long i, j, x, z, id;
        public final int vx, vz, crust, age;
        private Site(long i, long j, long x, long z, int vx, int vz, int crust, int age) {
            this.i = i; this.j = j; this.x = x; this.z = z; this.id = key(i, j);
            this.vx = vx; this.vz = vz; this.crust = crust; this.age = age;
        }
    }
    static final class Neighborhood {
        final Site[] sites = new Site[SIDE * SIDE];
        final long[][] denominators = new long[SIDE * SIDE][];
    }
    public static final class Sample {
        public final Site owner, neighbor;
        public final long distanceQ;
        public final int boundaryType;
        final Neighborhood neighborhood;
        private Sample(Site owner, Site neighbor, long distanceQ, int boundaryType, Neighborhood neighborhood) {
            this.owner = owner; this.neighbor = neighbor; this.distanceQ = distanceQ;
            this.boundaryType = boundaryType; this.neighborhood = neighborhood;
        }
    }
    public PlateTopology(long seed, Params params) {
        this.seed = Hash64.stream(seed, DOMAIN); this.params = params;
        this.spacing = params.integer("plateSpacing");
    }
    /** Exact identity over the supported domain; never use a hash as the identity. */
    public static long key(long i, long j) {
        if (i < Integer.MIN_VALUE || i > Integer.MAX_VALUE || j < Integer.MIN_VALUE || j > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Plate lattice key overflow");
        return (i << 32) | (j & 0xffffffffL);
    }
    private long offset(long hash, int scale) {
        long extent = (long) scale * params.integer("plateJitter") / (2 * 100);
        return scale / 2 + Math.floorMod(hash, 2 * extent + 1) - extent;
    }
    public Site site(long i, long j) {
        long h = Hash64.hash(seed, 0, i, j);
        int speed = params.integer("plateSpeed");
        long x = i * spacing + offset(h, spacing);
        long z = j * spacing + offset(Hash64.mix(h), spacing);
        int vx = (int) Math.floorMod(Hash64.hash(seed, 1, i, j), 2 * speed + 1) - speed;
        int vz = (int) Math.floorMod(Hash64.hash(seed, 2, i, j), 2 * speed + 1) - speed;
        int crust = Math.floorMod(Hash64.hash(seed, 3, i, j), 100) < params.integer("continentalPercent") ? 1 : 0;
        int age = (int) Math.floorMod(Hash64.hash(seed, 4, i, j), params.integer("crustAgeMax") + 1);
        return new Site(i, j, x, z, vx, vz, crust, age);
    }
    private synchronized Neighborhood neighborhood(long i, long j) {
        long key = key(i, j);
        Neighborhood n = cache.get(key);
        if (n != null) return n;
        n = new Neighborhood();
        for (int row = -RADIUS; row <= RADIUS; row++) for (int col = -RADIUS; col <= RADIUS; col++)
            n.sites[(row + RADIUS) * SIDE + col + RADIUS] = site(i + col, j + row);
        // Only the central 3x3 can own a point in the query cell.
        for (int row = -1; row <= 1; row++) for (int col = -1; col <= 1; col++) {
            int a = (row + RADIUS) * SIDE + col + RADIUS;
            n.denominators[a] = new long[n.sites.length];
            for (int b = 0; b < n.sites.length; b++) {
                long dx = n.sites[b].x - n.sites[a].x, dz = n.sites[b].z - n.sites[a].z;
                n.denominators[a][b] = 2 * sqrt((dx * dx + dz * dz) * Q * Q);
            }
        }
        cache.put(key, n);
        if (cache.size() > CACHE_CELLS) cache.remove(cache.keySet().iterator().next());
        return n;
    }
    public static long sqrt(long n) {
        if (n < 0) throw new IllegalArgumentException("Negative square root");
        if (n == 0) return 0;
        long x = 1L << ((64 - Long.numberOfLeadingZeros(n) + 1) / 2);
        while (true) { long next = (x + n / x) / 2; if (next >= x) return x; x = next; }
    }
    public static long squared(Site site, long x, long z) {
        long dx = site.x - x, dz = site.z - z;
        return dx * dx + dz * dz;
    }
    public static boolean precedes(Site a, Site b) {
        int hashOrder = Long.compareUnsigned(Hash64.mix(a.id), Hash64.mix(b.id));
        return hashOrder < 0 || (hashOrder == 0 && Long.compareUnsigned(a.id, b.id) < 0);
    }
    public int classify(Site a, Site b) {
        long dx = b.x - a.x, dz = b.z - a.z;
        long vx = a.vx - b.vx, vz = a.vz - b.vz;
        long normal = vx * dx + vz * dz, shear = vx * dz - vz * dx;
        if (Math.abs(normal) * 100 <= Math.abs(shear) * params.integer("transformRatio")) return 0;
        return normal > 0 ? 1 : -1;
    }
    public Sample sample(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        Neighborhood n = neighborhood(Lattice.cell(x, spacing), Lattice.cell(z, spacing));
        int owner = -1;
        long best = Long.MAX_VALUE;
        for (int row = -1; row <= 1; row++) for (int col = -1; col <= 1; col++) {
            int index = (row + RADIUS) * SIDE + col + RADIUS;
            long distance = squared(n.sites[index], x, z);
            if (distance < best || (distance == best && precedes(n.sites[index], n.sites[owner]))) {
                best = distance; owner = index;
            }
        }
        long boundary = Long.MAX_VALUE;
        Site neighbor = null;
        for (int b = 0; b < n.sites.length; b++) if (b != owner) {
            long gap = squared(n.sites[b], x, z) - best;
            long distanceQ = gap * Q * Q / n.denominators[owner][b];
            if (distanceQ < boundary || (distanceQ == boundary && precedes(n.sites[b], neighbor))) {
                boundary = distanceQ; neighbor = n.sites[b];
            }
        }
        Site a = n.sites[owner];
        return new Sample(a, neighbor, boundary, classify(a, neighbor), n);
    }
    /** Fixed-point compact blend for coarse continental anchors; never changes ownership. */
    public int crustFraction(long x, long z) {
        Lattice.check(x); Lattice.check(z);
        Neighborhood n = neighborhood(Lattice.cell(x, spacing), Lattice.cell(z, spacing));
        long radius = (long) spacing * params.integer("crustBlendRadius") / 1000;
        long radius2 = radius * radius, weights = 0, continental = 0;
        for (Site site : n.sites) {
            long distance = squared(site, x, z);
            if (distance >= radius2) continue;
            long t = 4096 - distance * 4096 / radius2;
            long weight = t * t * t;
            weights += weight; continental += weight * site.crust;
        }
        return (int) (continental * 1000 / weights);
    }
    /** Child identity is (parent.id, returned lattice key); clipping never changes the parent. */
    public long child(Site parent, long x, long z) {
        int scale = spacing / params.integer("subdivisions");
        long i = Lattice.cell(x, scale), j = Lattice.cell(z, scale);
        long childSeed = Hash64.stream(seed, parent.id);
        long best = Long.MAX_VALUE, selected = 0;
        for (int row = -1; row <= 1; row++) for (int col = -1; col <= 1; col++) {
            long ci = i + col, cj = j + row;
            long h = Hash64.hash(childSeed, 0, ci, cj);
            long dx = ci * scale + offset(h, scale) - x, dz = cj * scale + offset(Hash64.mix(h), scale) - z;
            long distance = dx * dx + dz * dz, id = key(ci, cj);
            int order = Long.compareUnsigned(Hash64.mix(id), Hash64.mix(selected));
            if (distance < best || (distance == best && (order < 0 || (order == 0 && Long.compareUnsigned(id, selected) < 0)))) {
                best = distance; selected = id;
            }
        }
        return selected;
    }
}
