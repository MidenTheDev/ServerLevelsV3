package us.to.midensthings.serverLevels.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

public class ExpGainBuiltin implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    @EventHandler
    public void onChat(AsyncChatEvent event) {

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            if (system.getExpOnChat() != 0) {
                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(event.getPlayer(),system.getSystemName());
                lp.incrementExp(system.getExpOnChat()*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }

        Player p = event.getEntity().getKiller();

        if (event.getEntity() instanceof Player) {
            plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
                if (system.getExpOnPlayerKill() != 0) {
                    LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                    lp.incrementExp(system.getExpOnPlayerKill()*(system.getExpGainMultiplier()+1));
                    plugin.getDatabaseManager().saveLeveledPlayer(lp);
                }
            });
            return;
        }
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            if (system.getExpOnMobKill() != 0) {
                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                lp.incrementExp(system.getExpOnMobKill()*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });
    }




    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            if (system.getExpOnBlockBreak() != 0) {
                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                lp.incrementExp(system.getExpOnBlockBreak()*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            if (system.getExpOnBlockPlace() != 0) {
                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                lp.incrementExp(system.getExpOnBlockPlace()*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });
    }
}
