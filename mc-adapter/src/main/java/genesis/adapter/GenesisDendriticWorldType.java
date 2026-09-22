package genesis.adapter;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;

import genesis.adapter.terrain.TerrainColumns;

/** Experimental Genesis fork with drainage-guided dendritic mountain detail. */
public final class GenesisDendriticWorldType extends WorldType {

    /** WorldType names are persisted IDs and Minecraft 1.7.10 limits them to 16 characters. */
    public static final String NAME = "genesis_dendrite";

    public GenesisDendriticWorldType() {
        super(NAME);
    }

    @Override
    public WorldChunkManager getChunkManager(World world) {
        return new GenesisBiomeManager(world.getSeed(), TerrainColumns.Style.DENDRITIC);
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String options) {
        if (options != null && !options.isEmpty()) {
            throw new IllegalArgumentException(
                "Genesis Dendritic v1 uses its fixed terrain preset; generator-options must be empty");
        }
        return new GenesisChunkProvider(world, ((GenesisBiomeManager) world.getWorldChunkManager()).columns);
    }

    @Override
    public int getMinimumSpawnHeight(World world) {
        return 64;
    }

    @Override
    public int getSpawnFuzz() {
        return GenesisWorldType.SPAWN_FUZZ;
    }
}
