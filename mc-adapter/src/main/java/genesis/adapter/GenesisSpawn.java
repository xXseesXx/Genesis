package genesis.adapter;

import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import genesis.adapter.terrain.SpawnSearch;

/** Spawn searching is separate from biome queries and never changes terrain. */
public final class GenesisSpawn {

    @SubscribeEvent
    public void createSpawn(WorldEvent.CreateSpawnPosition event) {
        if (event.world.provider.dimensionId != 0
            || !(event.world.getWorldChunkManager() instanceof GenesisBiomeManager manager)) return;
        var spawn = SpawnSearch.find(manager.columns);
        event.world.getWorldInfo()
            .setSpawnPosition(spawn.x(), spawn.y(), spawn.z());
        event.setCanceled(true);
    }
}
