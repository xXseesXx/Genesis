package genesis.adapter;

import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import genesis.adapter.terrain.TerrainColumns;
import genesis.oracle.TerrainSubstrate;

/** Simple biomes for the terrain prototype. No vanilla GenLayer geography. */
public final class GenesisBiomeManager extends WorldChunkManager {

    final TerrainColumns columns;

    public GenesisBiomeManager(long seed) {
        this(seed, TerrainColumns.Style.CLASSIC);
    }

    public GenesisBiomeManager(long seed, TerrainColumns.Style style) {
        columns = new TerrainColumns(seed, style);
    }

    public static BiomeGenBase biome(TerrainColumns.Column column) {
        return switch (column.water()) {
            case OCEAN -> column.groundY() < 48 ? BiomeGenBase.deepOcean : BiomeGenBase.ocean;
            case LAKE, RIVER -> BiomeGenBase.river;
            case NONE -> landBiome(column);
        };
    }

    public static float rainfall(TerrainColumns.Column column) {
        return (float) Math.max(0, Math.min(1, column.rainfall() / 2000.0));
    }

    private static BiomeGenBase landBiome(TerrainColumns.Column column) {
        if (column.groundY() >= 112) return BiomeGenBase.extremeHills;
        if (column.drainage() == TerrainSubstrate.Drainage.POOR && column.humidity() >= .55 && column.groundY() <= 76) {
            return BiomeGenBase.swampland;
        }
        if (column.rainfall() < 260 || column.humidity() < .20) return BiomeGenBase.desert;
        if (column.humidity() < .36) return BiomeGenBase.savanna;
        if (column.humidity() >= .68 || column.rainfall() >= 1200) return BiomeGenBase.forest;
        return BiomeGenBase.plains;
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return biome(columns.sample(x, z));
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] out, int x, int z, int width, int height) {
        return fill(out, x, z, width, height, 4);
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] out, int x, int z, int width, int height) {
        return fill(out, x, z, width, height, 1);
    }

    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] out, int x, int z, int width, int height, boolean cache) {
        return fill(out, x, z, width, height, 1);
    }

    private BiomeGenBase[] fill(BiomeGenBase[] out, int x, int z, int width, int height, int scale) {
        int count = Math.multiplyExact(width, height);
        if (out == null || out.length < count) out = new BiomeGenBase[count];
        for (int dz = 0; dz < height; dz++) for (int dx = 0; dx < width; dx++) {
            out[dz * width + dx] = biome(columns.sample(((long) x + dx) * scale, ((long) z + dz) * scale));
        }
        return out;
    }

    @Override
    public float[] getRainfall(float[] out, int x, int z, int width, int height) {
        int count = Math.multiplyExact(width, height);
        if (out == null || out.length < count) out = new float[count];
        for (int dz = 0; dz < height; dz++) for (int dx = 0; dx < width; dx++) {
            out[dz * width + dx] = rainfall(columns.sample((long) x + dx, (long) z + dz));
        }
        return out;
    }

    @Override
    public List<BiomeGenBase> getBiomesToSpawnIn() {
        return List.of(BiomeGenBase.plains, BiomeGenBase.forest, BiomeGenBase.savanna);
    }

    @Override
    public boolean areBiomesViable(int x, int z, int radius, List<BiomeGenBase> allowed) {
        for (int dz = z - radius; dz <= z + radius; dz += 4) {
            for (int dx = x - radius; dx <= x + radius; dx += 4) {
                if (!allowed.contains(getBiomeGenAt(dx, dz))) return false;
            }
        }
        return true;
    }

    @Override
    public ChunkPosition findBiomePosition(int x, int z, int radius, List<BiomeGenBase> allowed, Random random) {
        ChunkPosition result = null;
        int found = 0;
        for (int iz = (z - radius) >> 2; iz <= (z + radius) >> 2; iz++) {
            for (int ix = (x - radius) >> 2; ix <= (x + radius) >> 2; ix++) {
                if (allowed.contains(getBiomeGenAt(ix * 4, iz * 4)) && random.nextInt(++found) == 0) {
                    result = new ChunkPosition(ix * 4, 0, iz * 4);
                }
            }
        }
        return result;
    }
}
