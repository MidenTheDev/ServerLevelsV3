package us.to.midensthings.serverLevels.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import us.to.midensthings.serverLevels.ServerLevels;

public class PlayerLeave implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
            levelSystem.getPlayerCache().unloadPlayer(event.getPlayer());
        });
    }

}
