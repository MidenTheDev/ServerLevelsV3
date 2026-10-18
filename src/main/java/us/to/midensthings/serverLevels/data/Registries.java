package us.to.midensthings.serverLevels.data;

import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;

public class Registries {

    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    public static LevelSystem getLevelSystem(String name) {
        // Look the registry up every time, /sl reload replaces it with a new instance
        return plugin.getLevelSystemRegistry().getLevelSystem(name);
    }
}
