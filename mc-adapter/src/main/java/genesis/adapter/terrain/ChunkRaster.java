package genesis.adapter.terrain;

import java.util.Arrays;

import genesis.oracle.ContinentalHydrology.Root;
import genesis.oracle.FluvialNetwork;
import genesis.oracle.TerrainSubstrate;

/** Vanilla 1.7.10 block-array layout, tested without loading Minecraft. */
public final class ChunkRaster {

    public static final byte AIR = 0;
    public static final byte BEDROCK = 1;
    public static final byte STONE = 2;
    public static final byte DIRT = 3;
    public static final byte GRASS = 4;
    public static final byte GRAVEL = 5;
    public static final byte WATER = 6;
    public static final byte SAND = 7;
    public static final byte CLAY = 8;
    public static final byte SANDSTONE = 9;
    public static final byte HARDENED_CLAY = 10;
    public static final byte STAINED_HARDENED_CLAY = 11;
    public static final byte OBSIDIAN = 12;
    public static final byte COBBLESTONE = 13;

    static final byte RED_SAND_METADATA = 1;
    static final byte PODZOL_METADATA = 2;
    static final byte GRAY_CLAY_METADATA = 7;
    static final byte BLACK_CLAY_METADATA = 15;

    public record Raster(byte[] blocks, byte[] metadata, TerrainColumns.Column[] columns) {

        public Raster {
            if (blocks == null || metadata == null
                || columns == null
                || blocks.length != 16 * 16 * TerrainColumns.HEIGHT
                || metadata.length != blocks.length
                || columns.length != 256) {
                throw new IllegalArgumentException("Complete chunk raster required");
            }
        }
    }

    /** Tiny per-call cache for the one (occasionally few) snapped nodes touching a chunk. */
    static final class RootCache {

        private final TerrainColumns terrain;
        private long[] snappedX = new long[4];
        private long[] snappedZ = new long[4];
        private Root[] roots = new Root[4];
        private int size;

        RootCache(TerrainColumns terrain) {
            if (terrain == null) throw new IllegalArgumentException("Terrain columns required");
            this.terrain = terrain;
        }

        Root resolve(long x, long z) {
            long sx = terrain.hydrology.snap(x), sz = terrain.hydrology.snap(z);
            for (int i = 0; i < size; i++) {
                if (snappedX[i] == sx && snappedZ[i] == sz) return roots[i];
            }
            if (size == roots.length) {
                int capacity = size * 2;
                snappedX = Arrays.copyOf(snappedX, capacity);
                snappedZ = Arrays.copyOf(snappedZ, capacity);
                roots = Arrays.copyOf(roots, capacity);
            }
            Root root = terrain.rootAt(sx, sz);
            snappedX[size] = sx;
            snappedZ[size] = sz;
            roots[size++] = root;
            return root;
        }

        int resolutionCount() {
            return size;
        }
    }

    private ChunkRaster() {}

    public static Raster generate(TerrainColumns terrain, int chunkX, int chunkZ) {
        byte[] blocks = new byte[16 * 16 * TerrainColumns.HEIGHT];
        byte[] metadata = new byte[blocks.length];
        TerrainColumns.Column[] columns = new TerrainColumns.Column[256];
        RootCache rootCache = new RootCache(terrain);
        TerrainColumns.Column[] halo = new TerrainColumns.Column[18 * 18];
        long originX = (long) chunkX * 16, originZ = (long) chunkZ * 16;
        for (int z = -1; z <= 16; z++) for (int x = -1; x <= 16; x++) {
            long worldX = originX + x, worldZ = originZ + z;
            halo[(z + 1) * 18 + x + 1] = terrain.sampleBase(worldX, worldZ, rootCache.resolve(worldX, worldZ));
        }
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int h = (z + 1) * 18 + x + 1;
            var column = TerrainColumns.containWater(halo[h], halo[h - 1], halo[h + 1], halo[h - 18], halo[h + 18]);
            columns[z * 16 + x] = column;
            fillColumn(blocks, metadata, x, z, column);
        }
        return new Raster(blocks, metadata, columns);
    }

    static void fillColumn(byte[] blocks, int x, int z, TerrainColumns.Column column) {
        fillColumn(blocks, new byte[16 * 16 * TerrainColumns.HEIGHT], x, z, column);
    }

    static void fillColumn(byte[] blocks, byte[] metadata, int x, int z, TerrainColumns.Column column) {
        if (blocks == null || blocks.length != 16 * 16 * TerrainColumns.HEIGHT || column == null) {
            throw new IllegalArgumentException("Complete chunk raster and column required");
        }
        if (metadata == null || metadata.length != blocks.length) {
            throw new IllegalArgumentException("Complete chunk metadata required");
        }
        int offset = index(x, 0, z), ground = column.groundY();
        blocks[offset] = BEDROCK;
        boolean sediment = column.water() != TerrainColumns.Water.NONE || column.channelBed();
        int rockTop = Math.min(ground, column.bedrockY());
        fill(blocks, metadata, offset + 1, offset + rockTop + 1, rockBlock(column.rock()), rockMetadata(column.rock()));
        if (sediment) {
            int sedimentStart = Math.max(1, ground - 1);
            if (rockTop + 1 < sedimentStart) {
                byte subsoil = subsoilBlock(column.drainage());
                fill(
                    blocks,
                    metadata,
                    offset + rockTop + 1,
                    offset + sedimentStart,
                    subsoil,
                    subsoilMetadata(column, subsoil));
            }
            byte sedimentBlock = sedimentBlock(column);
            fill(
                blocks,
                metadata,
                offset + sedimentStart,
                offset + ground + 1,
                sedimentBlock,
                sedimentMetadata(column, sedimentBlock));
        } else if (rockTop < ground) {
            byte subsoil = subsoilBlock(column.drainage());
            fill(blocks, metadata, offset + rockTop + 1, offset + ground, subsoil, subsoilMetadata(column, subsoil));
            byte surface = surfaceBlock(column);
            blocks[offset + ground] = surface;
            metadata[offset + ground] = surfaceMetadata(column, surface);
        }
        if (column.waterY() > ground) {
            Arrays.fill(blocks, offset + ground + 1, offset + column.waterY() + 1, WATER);
        }
    }

    static byte rockBlock(TerrainSubstrate.Rock rock) {
        return switch (rock) {
            case BASALT, SHALE, LIMESTONE -> STAINED_HARDENED_CLAY;
            case GRANITE -> STONE;
            case SANDSTONE -> SANDSTONE;
        };
    }

    static byte rockMetadata(TerrainSubstrate.Rock rock) {
        return switch (rock) {
            case BASALT -> BLACK_CLAY_METADATA;
            case SHALE -> GRAY_CLAY_METADATA;
            default -> 0;
        };
    }

    private static byte sedimentBlock(TerrainColumns.Column column) {
        if (column.water() == TerrainColumns.Water.LAKE) {
            return column.drainage() == TerrainSubstrate.Drainage.WELL_DRAINED ? SAND : CLAY;
        }
        if (column.water() == TerrainColumns.Water.OCEAN) return SAND;
        if (column.planform() == FluvialNetwork.Planform.CASCADE) return COBBLESTONE;
        if (column.currentVelocity() >= 1.2) return GRAVEL;
        if (column.currentVelocity() < .25 && column.drainage() == TerrainSubstrate.Drainage.POOR) return CLAY;
        return SAND;
    }

    private static byte sedimentMetadata(TerrainColumns.Column column, byte block) {
        return block == SAND && column.rock() == TerrainSubstrate.Rock.SANDSTONE && column.humidity() < .35
            ? RED_SAND_METADATA
            : 0;
    }

    static byte subsoilBlock(TerrainSubstrate.Drainage drainage) {
        return switch (drainage) {
            case WELL_DRAINED -> SAND;
            case MODERATE -> DIRT;
            case POOR -> CLAY;
        };
    }

    private static byte subsoilMetadata(TerrainColumns.Column column, byte block) {
        return block == SAND && column.rock() == TerrainSubstrate.Rock.SANDSTONE && column.humidity() < .35
            ? RED_SAND_METADATA
            : 0;
    }

    private static byte surfaceBlock(TerrainColumns.Column column) {
        return column.humidity() < .22 || column.rainfall() < 260 ? SAND
            : column.humidity() > .72 && column.rainfall() > 900 ? DIRT : GRASS;
    }

    private static byte surfaceMetadata(TerrainColumns.Column column, byte block) {
        if (block == DIRT) return PODZOL_METADATA;
        return block == SAND && column.rock() == TerrainSubstrate.Rock.SANDSTONE ? RED_SAND_METADATA : 0;
    }

    private static void fill(byte[] blocks, byte[] metadata, int from, int to, byte block, byte blockMetadata) {
        if (from >= to) return;
        Arrays.fill(blocks, from, to, block);
        if (blockMetadata != 0) Arrays.fill(metadata, from, to, blockMetadata);
    }

    /** Layout consumed by Chunk's height-aware block+metadata constructor. */
    public static int index(int x, int y, int z) {
        if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= TerrainColumns.HEIGHT) {
            throw new IndexOutOfBoundsException("Chunk-local coordinate outside 16x256x16");
        }
        return (x * 16 + z) * TerrainColumns.HEIGHT + y;
    }
}
