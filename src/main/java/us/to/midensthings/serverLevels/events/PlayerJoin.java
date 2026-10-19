package us.to.midensthings.serverLevels.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.PlayerLoader;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerJoin implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    // Logins that are pending can still be rejected by other plugins, so drop them if the player never joins
    private static final long PENDING_LOGIN_TIMEOUT_MS = 60_000;

    // Data loaded during pre-login, waiting for the player to actually join
    private record PendingLogin(Map<String, LeveledPlayer> loaded, Set<String> checkedSystems, long loadedAt) {}
    private final Map<UUID, PendingLogin> pendingLogins = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();

        long now = System.currentTimeMillis();
        pendingLogins.values().removeIf(pending -> now - pending.loadedAt() > PENDING_LOGIN_TIMEOUT_MS);

        if (PlayerLoader.isCachedAnywhere(uuid)) {
            // Their old session is still online. Its data gets saved when it disconnects, so load after that in onJoin instead
            return;
        }

        // Pre-login is async, so it's fine to wait on the database here.
        // Loading goes through the database thread, so this always runs after the save from their last logout
        Collection<LevelSystem> systems = plugin.getLevelSystemRegistry().getAllSystems();
        Map<String, LeveledPlayer> loaded = plugin.getDatabaseManager().loadPlayerNow(uuid,
                PlayerLoader.buildLoads(Bukkit.getOfflinePlayer(uuid).getPersistentDataContainer(), systems));

        Set<String> checkedSystems = new HashSet<>();
        systems.forEach(system -> checkedSystems.add(system.getSystemName()));
        pendingLogins.put(uuid, new PendingLogin(loaded, checkedSystems, now));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        PendingLogin pending = pendingLogins.remove(uuid);
        if (pending == null) {
            // Nothing was loaded during pre-login, load everything now
            PlayerLoader.loadOnlinePlayer(p);
            return;
        }

        pending.loaded().forEach((systemName, lp) -> {
            LevelSystem system = plugin.getLevelSystemRegistry().getLevelSystem(systemName);
            if (system != null) {
                system.getPlayerCache().putIfAbsent(uuid, lp);
            }
        });

        // Load any systems added by a reload between pre-login and joining
        List<LevelSystem> missedSystems = plugin.getLevelSystemRegistry().getAllSystems().stream()
                .filter(system -> !pending.checkedSystems().contains(system.getSystemName()))
                .toList();
        PlayerLoader.loadOnlinePlayer(p, missedSystems);
    }

}
