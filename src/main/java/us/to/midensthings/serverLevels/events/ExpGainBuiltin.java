package us.to.midensthings.serverLevels.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.ExpRateLimiter;
import us.to.midensthings.serverLevels.util.EntityUtil;
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
            ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(p, ExpRateLimiter.Source.CHAT, "chat");
            plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
                giveExp(p, system, system.getExpOnChat() * gain.multiplierFor(system));
            });
        });
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        // Breaking an armor stand also fires this event, with the player as the killer
        if (!EntityUtil.canGiveExp(event.getEntity())) {
            return;
        }

        Player p = event.getEntity().getKiller();

        // Player was killed
        // Normally a separate event, but EntityDeathEvent and PlayerDeathEvent both fire when a player dies, so this is required
        if (event.getEntity() instanceof Player victim) {
            // Repeats are counted per victim, so killing the same player over and over gives less exp
            ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(p, ExpRateLimiter.Source.PLAYER_KILL, victim.getUniqueId().toString());
            plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
                giveExp(p, system, system.getExpOnPlayerKill() * gain.multiplierFor(system));
            });
            return;
        }

        // Other entity was killed
        boolean isMythicMob = plugin.enabledCompats.contains("MythicMobs") && plugin.getMythicMobsCompat().isMythicMob(event.getEntity());
        boolean fromSpawner = EntityUtil.isFromSpawner(event.getEntity());
        String mobKey = isMythicMob ? plugin.getMythicMobsCompat().getMobName(event.getEntity()) : event.getEntityType().name();
        ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(p, ExpRateLimiter.Source.MOB_KILL, String.valueOf(mobKey));

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            double expToAdd;

            if (isMythicMob) {
                // Use the exp for the mob from the system's mythicmobs section
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
                if (fromSpawner) {
                    expToAdd *= system.getSpawnerMobMultiplier();
                }
            } else {
                // Vanilla mob, use the exp for the mob from the system's mobs section, adjusted if it came from a mob spawner
                expToAdd = system.getMobKillExp(event.getEntityType(), fromSpawner);
            }

            giveExp(p, system, expToAdd * gain.multiplierFor(system));
        });
    }




    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        // Check if player was using silk touch or not
        boolean silkTouch = p.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SILK_TOUCH) != 0;
        // ItemsAdder and CraftEngine blocks are made from vanilla blocks, but use their own exp, never the vanilla block's
        String customBlockId = plugin.getCustomBlockId(event.getBlock());
        Material type = event.getBlock().getType();
        ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(p, ExpRateLimiter.Source.BLOCK_BREAK,
                customBlockId != null ? customBlockId : type.name());

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            double exp = customBlockId != null ? system.getCustomBlockBreakExp(customBlockId, silkTouch) : system.getBlockBreakExp(type, silkTouch);
            giveExp(p, system, exp * gain.multiplierFor(system));
        });
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        Block block = event.getBlock();
        if (plugin.hasCustomBlocks()) {
            // ItemsAdder and CraftEngine may turn the block into their custom block after this event, so check a tick later
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    giveBlockPlaceExp(p, block.getType(), plugin.getCustomBlockId(block));
                }
            });
            return;
        }
        giveBlockPlaceExp(p, block.getType(), null);
    }

    private void giveBlockPlaceExp(Player p, Material type, String customBlockId) {
        ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(p, ExpRateLimiter.Source.BLOCK_PLACE,
                customBlockId != null ? customBlockId : type.name());
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            double exp = customBlockId != null ? system.getCustomBlockPlaceExp(customBlockId) : system.getBlockPlaceExp(type);
            giveExp(p, system, exp * gain.multiplierFor(system));
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
