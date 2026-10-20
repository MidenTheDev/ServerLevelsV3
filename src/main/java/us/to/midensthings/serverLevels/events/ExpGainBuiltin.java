package us.to.midensthings.serverLevels.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

public class ExpGainBuiltin implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        // Chat events are async. Level ups run milestone commands and touch player data, so handle them on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
                giveExp(p, system, system.getExpOnChat());
            });
        });
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }

        Player p = event.getEntity().getKiller();

        // Player was killed
        // Normally a separate event, but EntityDeathEvent and PlayerDeathEvent both fire when a player dies, so this is required
        if (event.getEntity() instanceof Player) {
            plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
                giveExp(p, system, system.getExpOnPlayerKill());
            });
            return;
        }

        // Other entity was killed
        boolean isMythicMob = plugin.enabledCompats.contains("MythicMobs") && plugin.getMythicMobsCompat().isMythicMob(event.getEntity());

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            double expToAdd;

            if (isMythicMob) {
                // Use the exp for the mob from mythicmobs.yml
                MythicMobsCompat mmComp = plugin.getMythicMobsCompat();
                expToAdd = system.getDefaultExpOnMobKill();
                if (mmComp.getConfig().getConfigurationSection(system.getSystemName()) != null) {
                    expToAdd = mmComp.getConfig().getDouble(system.getSystemName()+".on-mobkill");
                    if (mmComp.getConfig().getConfigurationSection(system.getSystemName()+".mobs") != null) {
                        String mobName = mmComp.getMobName(event.getEntity());
                        for (String mob : mmComp.getConfig().getConfigurationSection(system.getSystemName()+".mobs").getKeys(false)) {
                            if (mob.equals(mobName)) {
                                expToAdd = mmComp.getConfig().getDouble(system.getSystemName()+".mobs."+mob);
                            }
                        }
                    }
                }
            } else {
                // Vanilla mob, use the exp for the mob from mobs.yml
                expToAdd = system.getMobKillExp(event.getEntityType());
            }

            giveExp(p, system, expToAdd);
        });
    }




    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        // Check if player was using silk touch or not
        boolean silkTouch = p.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SILK_TOUCH) != 0;

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            giveExp(p, system, system.getBlockBreakExp(event.getBlock().getType(), silkTouch));
        });
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            giveExp(p, system, system.getBlockPlaceExp(event.getBlock().getType()));
        });
    }

    // Adds the exp to the player in the system, autosave writes it to the database. Does nothing if the exp is 0 or the player isn't in the system
    private void giveExp(Player p, LevelSystem system, double expToAdd) {
        if (expToAdd == 0) {
            return;
        }
        LeveledPlayer lp = LeveledPlayer.fromPlayer(p, system.getSystemName());
        if (lp == null) {
            // Player isn't in this system
            return;
        }
        lp.gainExp(expToAdd);
    }
}
