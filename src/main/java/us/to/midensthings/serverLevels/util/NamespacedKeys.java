package us.to.midensthings.serverLevels.util;

import org.bukkit.NamespacedKey;
import us.to.midensthings.serverLevels.ServerLevels;

public class NamespacedKeys {
    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    public static NamespacedKey generatePlayerExpMultiplierKey(String levelSystem) {
        return new NamespacedKey(plugin,levelSystem+"_exp_multiplier");
    }

}
