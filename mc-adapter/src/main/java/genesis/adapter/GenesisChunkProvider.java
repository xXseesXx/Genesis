package genesis.adapter;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import genesis.adapter.terrain.ChunkRaster;
import genesis.adapter.terrain.TerrainColumns;

/** Pure 16x16 column rasterization; no neighboring chunk writes or vanilla terrain pass. */
public final class GenesisChunkProvider implements IChunkProvider {

    private final World world;
    private final TerrainColumns columns;

    public GenesisChunkProvider(World world, TerrainColumns columns) {
        this.world = world;
        this.columns = columns;
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        var raster = ChunkRaster.generate(columns, chunkX, chunkZ);
        Block[] palette = { Blocks.air, Blocks.bedrock, Blocks.stone, Blocks.dirt, Blocks.grass, Blocks.gravel,
            Blocks.water, Blocks.sand, Blocks.clay, Blocks.sandstone, Blocks.hardened_clay,
            Blocks.stained_hardened_clay, Blocks.obsidian, Blocks.cobblestone };
        Block[] blocks = new Block[raster.blocks().length];
        for (int i = 0; i < blocks.length; i++) blocks[i] = palette[Byte.toUnsignedInt(raster.blocks()[i])];
        byte[] biomes = new byte[256];
        for (int i = 0; i < biomes.length; i++)
            biomes[i] = (byte) GenesisBiomeManager.biome(raster.columns()[i]).biomeID;
        // The legacy Block[]-only constructor hardcodes 128-block strides
        // (x << 11, z << 7), even for a 256-high input array. Its metadata
        // overload derives the stride from the array and preserves every Z column.
        Chunk chunk = new Chunk(world, blocks, raster.metadata(), chunkX, chunkZ);
        System.arraycopy(biomes, 0, chunk.getBiomeArray(), 0, biomes.length);
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public Chunk loadChunk(int x, int z) {
        return provideChunk(x, z);
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    @Override
    public void populate(IChunkProvider provider, int x, int z) {}

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "Genesis tectonics + erosion + hydrology";
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return world.getBiomeGenForCoords(x, z)
            .getSpawnableList(type);
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structure, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int x, int z) {}

    @Override
    public void saveExtraData() {}
}
