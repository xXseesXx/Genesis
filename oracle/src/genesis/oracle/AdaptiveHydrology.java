package genesis.oracle;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only adaptive realization grid layered over a committed continental root.
 *
 * <p>The root remains the authority for terminals, receivers, lakes and flux. This
 * class deliberately does not independently route a cropped child tile: doing that
 * would make a tile edge a false outlet. Instead it exposes a deterministic 32 -> 8
 * -> 2 metre view. A child inherits its parent's receiver/flux, and its local source
 * is apportioned with integer prefix sums so every complete child grid sums exactly
 * to the parent source.</p>
 */
public final class AdaptiveHydrology {
    public static final String VERSION = "adaptive-hydrology-v2";
    public static final int INTERMEDIATE_STEP = 8;
    public static final int FINE_STEP = 2;
    private static final int OCEAN_TILE_CACHE = 512;
    private static final int MAX_FINE_LAKE_CELLS = 4_000_000;

    private final TectonicTerrain world;
    private final Map<ContinentalHydrology.Root, RootCache> caches = new WeakHashMap<>();

    public AdaptiveHydrology(TectonicTerrain world) {
        if (world == null) throw new IllegalArgumentException("Terrain required");
        this.world = world;
    }

    public enum Level {
        COARSE, INTERMEDIATE, FINE
    }

    /** Immutable refinement commitment suitable for a single world column. */
    public record Cell(Level level, int step, long x, long z, int parent, int downstream,
                       long parentFlux, long localSource, FluvialNetwork.Channel channel,
                       FluvialNetwork.LakeMask lake) {
        public Cell {
            if (level == null || step < 1 || parent < -1 || downstream < -1 || parentFlux < 0 || localSource < 0) {
                throw new IllegalArgumentException("Invalid adaptive hydrology cell");
            }
        }
    }

    /** Selects detail only around committed channels, lake shores and their parent cells. */
    public Cell sample(ContinentalHydrology.Root root, long worldX, long worldZ) {
        if (root == null) throw new IllegalArgumentException("Hydrology root required");
        int parent = root.index(worldX, worldZ);
        if (!root.active(parent)) return new Cell(Level.COARSE, root.step, snap(worldX, root.step), snap(worldZ, root.step),
            parent, -1, 0, 0, null, null);

        FluvialNetwork.Channel channel = root.channelAt(worldX, worldZ);
        FluvialNetwork.LakeMask lake = root.fluvial().lakeMaskAt(worldX, worldZ);
        Level level = Level.COARSE;
        if (nearCommittedParent(root, parent) || lake != null) level = Level.INTERMEDIATE;
        if (inFineCorridor(root, parent, channel, lake)) level = Level.FINE;
        int step = switch (level) {
            case COARSE -> root.step;
            case INTERMEDIATE -> intermediateStep(root.step);
            case FINE -> fineStep(root.step);
        };
        return new Cell(level, step, snap(worldX, step), snap(worldZ, step), parent, root.downstream(parent),
            root.flux(parent), apportionedSource(root, parent, worldX, worldZ, step), channel, lake);
    }

    /**
     * Two-block coastal connectivity inside the containing coarse quad. Only fine
     * terrain at or below sea level connected to a committed maritime corner is wet.
     */
    public boolean seaAt(ContinentalHydrology.Root root, long x, long z) {
        if (root == null) throw new IllegalArgumentException("Hydrology root required");
        long qx = Math.floorDiv(x - root.bounds.x(), root.step);
        long qz = Math.floorDiv(z - root.bounds.z(), root.step);
        if (qx < 0 || qz < 0 || qx >= root.bounds.width() - 1L || qz >= root.bounds.height() - 1L) {
            return root.seaAt(x, z);
        }
        long key = (qx << 32) ^ (qz & 0xffffffffL);
        RootCache cache = cache(root);
        OceanTile tile;
        synchronized (cache) {
            tile = cache.ocean.get(key);
            if (tile == null) {
                tile = buildOcean(root, (int) qx, (int) qz);
                cache.ocean.put(key, tile);
            }
        }
        return tile.wet(x, z);
    }

    /** Two-block post-erosion flood of the nearby committed lake component. */
    public FluvialNetwork.LakeSample lakeAt(ContinentalHydrology.Root root, long x, long z, double fineBedElevation) {
        if (root == null || !Double.isFinite(fineBedElevation)) throw new IllegalArgumentException("Finite lake query required");
        FluvialNetwork.LakeMask candidate = root.fluvial().lakeMaskAt(x, z);
        if (candidate == null) return null;
        RootCache cache = cache(root);
        FineLake fine;
        synchronized (cache) {
            fine = cache.lakes.get(candidate.lake().id());
            if (fine == null) {
                fine = buildLake(root, candidate.lake());
                cache.lakes.put(candidate.lake().id(), fine);
            }
        }
        if (fine.fallback) return root.lakeAt(x, z, fineBedElevation);
        if (!fine.wet(x, z) || fineBedElevation >= candidate.lake().surface()) return null;
        return new FluvialNetwork.LakeSample(candidate.lake(), true, 1, candidate.lake().surface(),
            fineBedElevation, candidate.lake().surface() - fineBedElevation);
    }

    /** Exact source partition: summing all equally sized child cells yields parent.source(). */
    public static long apportionedSource(ContinentalHydrology.Root root, int parent, long x, long z, int childStep) {
        if (root == null || !root.active(parent) || childStep < 1 || root.step % childStep != 0) return 0;
        int side = root.step / childStep;
        long localX = Math.floorMod(x - root.x(parent) + root.step / 2L, root.step);
        long localZ = Math.floorMod(z - root.z(parent) + root.step / 2L, root.step);
        int ix = (int) Math.min(side - 1, localX / childStep);
        int iz = (int) Math.min(side - 1, localZ / childStep);
        long index = (long) iz * side + ix, count = (long) side * side, source = root.source(parent);
        return Math.floorDiv(source * (index + 1), count) - Math.floorDiv(source * index, count);
    }

    private static boolean nearCommittedParent(ContinentalHydrology.Root root, int parent) {
        if (root.profile(parent) != null || root.fluvial().lakeForCell(parent) != null) return true;
        int width = root.bounds.width(), px = parent % width, pz = parent / width;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int x = px + dx, z = pz + dz;
            if (x < 0 || z < 0 || x >= width || z >= root.bounds.height()) continue;
            int p = z * width + x;
            if (root.profile(p) != null || root.fluvial().lakeForCell(p) != null) return true;
        }
        return false;
    }

    private static boolean inFineCorridor(ContinentalHydrology.Root root, int parent,
                                          FluvialNetwork.Channel channel, FluvialNetwork.LakeMask lake) {
        if (lake != null && Math.abs(lake.shorelineSignal()) <= .75) return true;
        if (channel == null) return false;
        double radius = Math.max(FINE_STEP, channel.profile().bankfullWidth() / 2 + FINE_STEP);
        return channel.threadDistance() <= radius || (root.profile(parent) != null && channel.centerDistance() <= radius);
    }

    private static int intermediateStep(int rootStep) {
        if (rootStep % INTERMEDIATE_STEP == 0) return INTERMEDIATE_STEP;
        if (rootStep % 4 == 0) return 4;
        return fineStep(rootStep);
    }

    private static int fineStep(int rootStep) {
        return rootStep % FINE_STEP == 0 ? FINE_STEP : 1;
    }

    private static long snap(long coordinate, int step) {
        return Math.floorDiv(coordinate, step) * (long) step;
    }

    private RootCache cache(ContinentalHydrology.Root root) {
        synchronized (caches) {
            return caches.computeIfAbsent(root, ignored -> new RootCache());
        }
    }

    private OceanTile buildOcean(ContinentalHydrology.Root root, int qx, int qz) {
        int side = root.step / FINE_STEP + 1;
        long originX = root.bounds.x() + qx * (long) root.step;
        long originZ = root.bounds.z() + qz * (long) root.step;
        boolean[] eligible = new boolean[side * side];
        BitSet wet = new BitSet(side * side);
        int[] queue = new int[side * side];
        int head = 0, tail = 0;
        for (int iz = 0; iz < side; iz++) for (int ix = 0; ix < side; ix++) {
            long wx = originX + ix * (long) FINE_STEP, wz = originZ + iz * (long) FINE_STEP;
            eligible[iz * side + ix] = world.sample(wx, wz).elevation() <= world.seaLevel();
        }
        int width = root.bounds.width();
        for (int cornerZ = 0; cornerZ <= 1; cornerZ++) for (int cornerX = 0; cornerX <= 1; cornerX++) {
            int p = (qz + cornerZ) * width + qx + cornerX;
            int ix = cornerX * (side - 1), iz = cornerZ * (side - 1), cell = iz * side + ix;
            if (root.sea(p) && eligible[cell] && !wet.get(cell)) {
                wet.set(cell);
                queue[tail++] = cell;
            }
        }
        while (head < tail) {
            int cell = queue[head++], cx = cell % side, cz = cell / side;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                int nx = cx + dx, nz = cz + dz;
                if (nx < 0 || nz < 0 || nx >= side || nz >= side) continue;
                int next = nz * side + nx;
                if (eligible[next] && !wet.get(next)) {
                    wet.set(next);
                    queue[tail++] = next;
                }
            }
        }
        return new OceanTile(originX, originZ, side, wet);
    }

    private FineLake buildLake(ContinentalHydrology.Root root, FluvialNetwork.Lake lake) {
        long lowX = snap(lake.minX() - root.step, FINE_STEP);
        long lowZ = snap(lake.minZ() - root.step, FINE_STEP);
        long highX = snap(lake.maxX() + root.step + FINE_STEP - 1L, FINE_STEP);
        long highZ = snap(lake.maxZ() + root.step + FINE_STEP - 1L, FINE_STEP);
        long wide = Math.floorDiv(highX - lowX, FINE_STEP) + 1;
        long high = Math.floorDiv(highZ - lowZ, FINE_STEP) + 1;
        long count = Math.multiplyExact(wide, high);
        if (wide > Integer.MAX_VALUE || high > Integer.MAX_VALUE || count > MAX_FINE_LAKE_CELLS) return FineLake.createFallback();
        int width = (int) wide, height = (int) high, size = (int) count;
        boolean[] eligible = new boolean[size];
        BitSet wet = new BitSet(size);
        int[] queue = new int[size];
        for (int iz = 0; iz < height; iz++) for (int ix = 0; ix < width; ix++) {
            long wx = lowX + ix * (long) FINE_STEP, wz = lowZ + iz * (long) FINE_STEP;
            int parent = root.index(wx, wz);
            if (!root.active(parent) || root.sea(parent)) continue;
            var raw = world.sample(wx, wz);
            double bed = root.elevation(wx, wz, raw.elevation(), world.seaLevel());
            eligible[iz * width + ix] = bed < lake.surface();
        }
        int head = 0, tail = 0;
        int minPX = Math.max(0, root.index(lake.minX(), lake.minZ()) % root.bounds.width() - 1);
        int maxPX = Math.min(root.bounds.width() - 1, root.index(lake.maxX(), lake.maxZ()) % root.bounds.width() + 1);
        int minPZ = Math.max(0, root.index(lake.minX(), lake.minZ()) / root.bounds.width() - 1);
        int maxPZ = Math.min(root.bounds.height() - 1, root.index(lake.maxX(), lake.maxZ()) / root.bounds.width() + 1);
        for (int pz = minPZ; pz <= maxPZ; pz++) for (int px = minPX; px <= maxPX; px++) {
            int p = pz * root.bounds.width() + px;
            FluvialNetwork.Lake member = root.fluvial().lakeForCell(p);
            if (member == null || member.id() != lake.id()) continue;
            int ix = (int) Math.floorDiv(root.x(p) - lowX, FINE_STEP);
            int iz = (int) Math.floorDiv(root.z(p) - lowZ, FINE_STEP);
            int cell = iz * width + ix;
            if (eligible[cell] && !wet.get(cell)) {
                wet.set(cell);
                queue[tail++] = cell;
            }
        }
        while (head < tail) {
            int cell = queue[head++], cx = cell % width, cz = cell / width;
            for (int[] direction : CARDINALS) {
                int nx = cx + direction[0], nz = cz + direction[1];
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) continue;
                int next = nz * width + nx;
                if (eligible[next] && !wet.get(next)) {
                    wet.set(next);
                    queue[tail++] = next;
                }
            }
        }
        return new FineLake(lowX, lowZ, width, height, wet, false);
    }

    private static final int[][] CARDINALS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private static final class RootCache {
        final Map<Long, OceanTile> ocean = new LinkedHashMap<>(64, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, OceanTile> eldest) {
                return size() > OCEAN_TILE_CACHE;
            }
        };
        final Map<Long, FineLake> lakes = new LinkedHashMap<>();
    }

    private record OceanTile(long x, long z, int side, BitSet water) {
        boolean wet(long worldX, long worldZ) {
            int ix = (int) Math.floorDiv(worldX - x, FINE_STEP);
            int iz = (int) Math.floorDiv(worldZ - z, FINE_STEP);
            return ix >= 0 && iz >= 0 && ix < side && iz < side && water.get(iz * side + ix);
        }
    }

    private record FineLake(long x, long z, int width, int height, BitSet water, boolean fallback) {
        static FineLake createFallback() { return new FineLake(0, 0, 0, 0, new BitSet(), true); }
        boolean wet(long worldX, long worldZ) {
            if (fallback) return false;
            int ix = (int) Math.floorDiv(worldX - x, FINE_STEP);
            int iz = (int) Math.floorDiv(worldZ - z, FINE_STEP);
            return ix >= 0 && iz >= 0 && ix < width && iz < height && water.get(iz * width + ix);
        }
    }
}
