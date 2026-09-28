package us.to.midensthings.serverLevels.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

public class ExpGainBuiltin implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    @EventHandler
    public void onChat(AsyncChatEvent event) {

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            if (system.getExpOnChat() != 0) {
                if (plugin.getDatabaseManager().playerHasRecord(event.getPlayer(),system.getSystemName())) {
                    LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(event.getPlayer(),system.getSystemName());
                    lp.incrementExp(system.getExpOnChat()*(system.getExpGainMultiplier()+1));
                    plugin.getDatabaseManager().saveLeveledPlayer(lp);
                }

            }
        });
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }

        Player p = event.getEntity().getKiller();

        // Player was killed
        // Nomrally a separate event, but EntityDeathEvent and PlayerDeathEvent both fire when a player dies, so this is required
        if (event.getEntity() instanceof Player) {
            plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
                if (plugin.getDatabaseManager().playerHasRecord(p,system.getSystemName())) {
                    if (system.getExpOnPlayerKill() != 0) {
                        LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                        lp.incrementExp(system.getExpOnPlayerKill()*(system.getExpGainMultiplier()+1));
                        plugin.getDatabaseManager().saveLeveledPlayer(lp);
                    }
                }

            });
            return;
        }

        // Other entity was killed
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {

            // Check first if the playe is in the system
            if (!(plugin.getDatabaseManager().playerHasRecord(p,system.getSystemName()))) {
                return;
            }
            double expToAdd = system.getExpOnMobKill();

            // Check if the mob is one of the mobs listed for the system in mobs.yml
            if (plugin.enabledCompats.contains("MythicMobs")) {
                MythicMobsCompat mmComp = new MythicMobsCompat();
                if (mmComp.isMythicMob(event.getEntity())) {
                    if (mmComp.getConfig().getConfigurationSection(system.getSystemName()) != null) {
                        expToAdd = mmComp.getConfig().getDouble(system.getSystemName()+".on-mobkill");
                        if (mmComp.getConfig().getConfigurationSection(system.getSystemName()+".mobs") != null) {
                            String mobName = mmComp.getMobName(event.getEntity());
                            plugin.getLogger().warning(mobName);
                            for (String mob : mmComp.getConfig().getConfigurationSection(system.getSystemName()+".mobs").getKeys(false)) {
                                if (mobName.equals(mob)) {
                                    expToAdd = mmComp.getConfig().getDouble(system.getSystemName()+".mobs."+mob);
                                }
                            }
                        }
                    }

                }
            } else if (plugin.getMobsConf().getConfigurationSection(system.getSystemName()) != null) {
                for (String mob : plugin.getMobsConf().getConfigurationSection(system.getSystemName()).getKeys(false)) {
                    EntityType entityType = EntityType.valueOf(mob);
                    if (entityType == event.getEntityType()) {
                        expToAdd = plugin.getMobsConf().getDouble(system.getSystemName()+"."+mob);
                        break;
                    }
                }
            }



            if (expToAdd != 0) {

                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                lp.incrementExp(expToAdd*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });
    }




    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {

            if (!(plugin.getDatabaseManager().playerHasRecord(p,system.getSystemName()))) {
                return;
            }

            double expToAdd = system.getExpOnBlockPlace();
            Material blockType = event.getBlock().getType();

            // Check if player was using silk touch or not
            if (p.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SILK_TOUCH) == 0) {
                // Check if there is a configuration section at all
                if (plugin.getBlocksConf().getConfigurationSection(system.getSystemName()+".break.no-silk") != null) {
                    // Check if the broken block is in the config
                    for(String block : plugin.getBlocksConf().getConfigurationSection(system.getSystemName()+".break.no-silk").getKeys(false)) {
                        Material compBlock = Material.valueOf(block);
                        if (compBlock == blockType) {
                            expToAdd = plugin.getBlocksConf().getDouble(system.getSystemName()+".break.no-silk."+block);
                        }
                    }
                }
            } else {
                // Check if there is a configuration section at all
                if (plugin.getBlocksConf().getConfigurationSection(system.getSystemName()+".break.silk") != null) {
                    // Check if the broken block is in the config
                    for(String block : plugin.getBlocksConf().getConfigurationSection(system.getSystemName()+".break.silk").getKeys(false)) {
                        Material compBlock = Material.valueOf(block);
                        if (compBlock == blockType) {

                            expToAdd = plugin.getBlocksConf().getDouble(system.getSystemName()+".break.silk."+block);

                        }
                    }
                }
            }


            if (expToAdd != 0) {

                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                lp.incrementExp(expToAdd*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);


            }
        });
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {

            if (!(plugin.getDatabaseManager().playerHasRecord(p,system.getSystemName()))) {
                return;
            }

            double expToAdd = system.getExpOnBlockPlace();
            // Check if there is a section for placed blocks for this system in blocks.yml
            if (plugin.getBlocksConf().getConfigurationSection(system.getSystemName()+".place") != null) {
                Material blockType = event.getBlock().getType();
                // Check if the placed block is in the config
                for(String block : plugin.getBlocksConf().getConfigurationSection(system.getSystemName()+".place").getKeys(false)) {
                    Material compBlock = Material.valueOf(block);
                    if (compBlock == blockType) {
                        expToAdd = plugin.getBlocksConf().getDouble(system.getSystemName()+".place."+block);
                    }
                }
            }

            if (expToAdd != 0) {
                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p,system.getSystemName());
                lp.incrementExp(expToAdd*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });
    }
}
