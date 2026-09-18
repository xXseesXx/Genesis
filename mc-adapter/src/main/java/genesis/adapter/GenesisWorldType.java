package genesis.adapter;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;

public final class GenesisWorldType extends WorldType {

    /** Minecraft passes this directly to Random.nextInt, so it must stay positive. */
    public static final int SPAWN_FUZZ = 1;

    public GenesisWorldType() {
        super("genesis");
    }

    @Override
    public WorldChunkManager getChunkManager(World world) {
        return new GenesisBiomeManager(world.getSeed());
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String options) {
        if (options != null && !options.isEmpty()) {
            throw new IllegalArgumentException(
                "Genesis v1 uses its fixed terrain preset; generator-options must be empty");
        }
        return new GenesisChunkProvider(world, ((GenesisBiomeManager) world.getWorldChunkManager()).columns);
    }

    @Override
    public int getMinimumSpawnHeight(World world) {
        return 64;
    }

    @Override
    public int getSpawnFuzz() {
        return SPAWN_FUZZ;
    }
}
