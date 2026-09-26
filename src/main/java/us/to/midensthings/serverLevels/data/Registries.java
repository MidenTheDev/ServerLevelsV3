package us.to.midensthings.serverLevels.data;

import us.to.midensthings.serverLevels.ServerLevels;

public class Registries {

    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    public static LevelSystemRegistry levelSystemRegistry = plugin.getLevelSystemRegistry();
}
