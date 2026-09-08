# Minecraft adapter — reserved for M9

Target: Minecraft 1.7.10 / GregTech: New Horizons.

Use the official [GTNH ExampleMod1.7.10 project starter](https://github.com/GTNewHorizons/ExampleMod1.7.10#getting-started) when mod implementation begins. The official README directs new projects to its downloadable starter, rather than a clone of the example repository. Follow the actual starter link from that README and pin the imported release/checksum then.

Import and customize starter content under this directory; preserve the Genesis repository's `.git`, remote, roadmap, core, and harness. Do not nest the example project's Git metadata or overwrite the Genesis root. No mod template has been imported yet because the standalone generator comes first.

The adapter must consume the existing core, not copy/reimplement its algorithms. Only this module may depend on Forge/Minecraft/GTNH. Establish the exact pack version, world/dimension registration approach, height mapping, and coexistence with other world generators during M9 integration. Until then, `build/genesis-core.jar` is a standalone library, not a loadable Minecraft mod.
