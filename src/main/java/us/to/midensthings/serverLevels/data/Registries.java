package us.to.midensthings.serverLevels.data;

import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;

public class Registries {

    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    private static LevelSystemRegistry levelSystemRegistry = plugin.getLevelSystemRegistry();

    public static LevelSystem getLevelSystem(String name) {
        return levelSystemRegistry.getLevelSystem(name);
    }
}
