package genesis.adapter;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import genesis.adapter.command.SpeedCommand;

@Mod(modid = "genesis", name = "Genesis", version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public final class GenesisMod {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        new GenesisWorldType();
        new GenesisDendriticWorldType();
        MinecraftForge.EVENT_BUS.register(new GenesisSpawn());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new SpeedCommand());
    }
}
