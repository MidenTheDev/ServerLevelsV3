package us.to.midensthings.serverLevels.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import us.to.midensthings.serverLevels.ServerLevels;

public class PlayerJoin implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
            if (levelSystem.doAutoAddPlayers()) {
                // initializePlayer does nothing if the player already has a record, so there's no need to check first
                plugin.getDatabaseManager().initializePlayer(p,levelSystem.getSystemName());
            }
        });

    }

}
