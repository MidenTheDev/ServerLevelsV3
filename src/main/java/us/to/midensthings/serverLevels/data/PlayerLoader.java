package us.to.midensthings.serverLevels.data;

import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.ApiStatus;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.util.NamespacedKeys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/***
 * Helpers for loading player data into the level system caches.
 */
@ApiStatus.Internal
public final class PlayerLoader {
    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    private PlayerLoader() {}

    public static double readExpMultiplier(PersistentDataContainerView pdc, String system) {
        return pdc.getOrDefault(NamespacedKeys.generatePlayerExpMultiplierKey(system), PersistentDataType.DOUBLE, 1.0);
    }

    public static List<DatabaseManager.SystemLoad> buildLoads(PersistentDataContainerView pdc, Collection<LevelSystem> systems) {
        List<DatabaseManager.SystemLoad> loads = new ArrayList<>();
        for (LevelSystem system : systems) {
            loads.add(new DatabaseManager.SystemLoad(system.getSystemName(), system.doAutoAddPlayers(), readExpMultiplier(pdc, system.getSystemName())));
        }
        return loads;
    }

    public static boolean isCachedAnywhere(UUID uuid) {
        for (LevelSystem system : plugin.getLevelSystemRegistry().getAllSystems()) {
            if (system.getPlayerCache().getLeveledPlayer(uuid) != null) {
                return true;
            }
        }
        return false;
    }

    /***
     * Loads an online player's data for every system they aren't cached in yet, in the background.
     * Must be called on the main thread.
     */
    public static void loadOnlinePlayer(Player p) {
        loadOnlinePlayer(p, plugin.getLevelSystemRegistry().getAllSystems().stream()
                .filter(system -> system.getPlayerCache().getLeveledPlayer(p.getUniqueId()) == null)
                .toList());
    }

    /***
     * Loads an online player's data for the given systems in the background.
     * Must be called on the main thread.
     */
    public static void loadOnlinePlayer(Player p, Collection<LevelSystem> systems) {
        if (systems.isEmpty()) {
            return;
        }
        UUID uuid = p.getUniqueId();
        plugin.getDatabaseManager().loadPlayerAsync(uuid, buildLoads(p.getPersistentDataContainer(), systems), loaded -> {
            // Skip if the player logged off (or reconnected as a new session) while loading
            if (Bukkit.getPlayer(uuid) != p) {
                return;
            }
            loaded.forEach((systemName, lp) -> {
                LevelSystem system = plugin.getLevelSystemRegistry().getLevelSystem(systemName);
                if (system != null) {
                    system.getPlayerCache().putIfAbsent(uuid, lp);
                }
            });
        });
    }
}
