package us.to.midensthings.serverLevels.compat.events;

import com.projectkorra.projectkorra.event.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.io.File;

public class ProjectKorraCompatEvents implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    File pkYml = new File(plugin.getDataFolder()+"/Compat/projectkorra.yml");
    YamlConfiguration pkConf;

    // Events based on entity damage and death will fire twice due to the built-in mobkill and playerkill events!

    // Entity killed by bending
    @EventHandler
    public void entityKilledByBending(EntityBendingDeathEvent event) {
        pkConf = YamlConfiguration.loadConfiguration(pkYml);

        if (event.getEntity() instanceof Player) {
            // player death
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {

                if (!(plugin.getDatabaseManager().playerHasRecord(event.getAbility().getPlayer(), levelSystem.getSystemName()))) {
                    return;
                }

                // default exp
                double expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".exp-gain.bending-kill-player");

                // Check if there is a specific amount of exp to gain for this ability
                if (pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-kill-player") != null) {
                    for (String ability : pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-kill-player").getKeys(false)) {
                        if (event.getAbility().getName().equalsIgnoreCase(ability)) {
                            expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".abilities-kill-player."+ability);
                        }
                    }
                }

                if (expFromAbility != 0) {
                    LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(event.getAttacker(), levelSystem.getSystemName());
                    lp.incrementExp(expFromAbility*(levelSystem.getExpGainMultiplier()+1));
                    plugin.getDatabaseManager().saveLeveledPlayer(lp);
                }
            });

        } else {
            // not player death
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {

                if (!(plugin.getDatabaseManager().playerHasRecord(event.getAbility().getPlayer(), levelSystem.getSystemName()))) {
                    return;
                }

                // default exp
                double expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".exp-gain.bending-kill-mob");
                double expFromMob = 0;
                // Check if there is a specific amount of exp to gain for this ability
                if (pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-kill-mob") != null) {
                    for (String ability : pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-kill-mob").getKeys(false)) {
                        if (event.getAbility().getName().equalsIgnoreCase(ability)) {
                            expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".abilities-kill-mob."+ability);
                            break;
                        }
                    }
                }

                // Check if there is a specific amount of exp to gain for this mob
                if (pkConf.getConfigurationSection(levelSystem.getSystemName()+".kill-mobs") != null) {

                    boolean isCustomEntity = false;
                    // Check for mythicmobs specifications first
                    if (plugin.enabledCompats.contains("MythicMobs")) {
                        MythicMobsCompat mmComp = new MythicMobsCompat();
                        if (mmComp.isMythicMob(event.getEntity())) {
                            isCustomEntity = true;
                            for (String mob : pkConf.getConfigurationSection(levelSystem.getSystemName()+".kill-mobs").getKeys(false)) {
                                if (mmComp.getMobName(event.getEntity()).equals(mob)) {
                                    expFromMob = pkConf.getDouble(levelSystem.getSystemName()+".kill-mobs."+mob);
                                    break;
                                }
                            }
                        }
                    }

                    // If it's not a custom entity, do vanilla method
                    if (!isCustomEntity) {
                        for (String mob : pkConf.getConfigurationSection(levelSystem.getSystemName()+".kill-mobs").getKeys(false)) {
                            EntityType entityType = event.getEntity().getType();
                            if (entityType == EntityType.valueOf(mob)) {
                                expFromMob = pkConf.getDouble(levelSystem.getSystemName()+".kill-mobs."+mob);
                                break;
                            }
                        }
                    }

                }

                if (expFromAbility != 0 || expFromMob != 0) {
                    double expToAdd = expFromAbility + expFromMob;
                    LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(event.getAttacker(), levelSystem.getSystemName());
                    lp.incrementExp(expToAdd*(levelSystem.getExpGainMultiplier()+1));
                    plugin.getDatabaseManager().saveLeveledPlayer(lp);
                }
            });
        }

    }

    // Entity damaged by bending
    @EventHandler
    public void entityDamagedByBending(AbilityDamageEntityEvent event) {
        pkConf = YamlConfiguration.loadConfiguration(pkYml);

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity le = (LivingEntity) event.getEntity();
        if (le.getHealth() <= event.getDamage()) {
            // Entity was killed. Let the death event above handle it.
            return;
        }


        if (event.getEntity() instanceof Player) {
            // player death
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                if (!(plugin.getDatabaseManager().playerHasRecord(event.getAbility().getPlayer(), levelSystem.getSystemName()))) {
                    return;
                }
                // default exp
                double expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".exp-gain.bending-kill-player");

                // Check if there is a specific amount of exp to gain for this ability
                if (pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-kill-player") != null) {
                    for (String ability : pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-kill-player").getKeys(false)) {
                        if (event.getAbility().getName().equalsIgnoreCase(ability)) {
                            expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".abilities-kill-player."+ability);
                        }
                    }
                }

                if (expFromAbility != 0) {
                    LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(event.getSource(), levelSystem.getSystemName());
                    lp.incrementExp(expFromAbility*(levelSystem.getExpGainMultiplier()+1));
                    plugin.getDatabaseManager().saveLeveledPlayer(lp);
                }
            });

        } else {
            // not player death
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {

                if (!(plugin.getDatabaseManager().playerHasRecord(event.getAbility().getPlayer(), levelSystem.getSystemName()))) {
                    return;
                }

                // default exp
                double expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".exp-gain.bending-damage-mob");
                double expFromMob = 0;

                // Check if there is a specific amount of exp to gain for this ability
                if (pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-damage-mob") != null) {
                    for (String ability : pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-damage-mob").getKeys(false)) {
                        if (event.getAbility().getName().equalsIgnoreCase(ability)) {
                            expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".abilities-damage-mob."+ability);
                            break;
                        }
                    }
                }

                // Check if there is a specific amount of exp to gain for this mob
                if (pkConf.getConfigurationSection(levelSystem.getSystemName()+".damage-mobs") != null) {

                    boolean isCustomEntity = false;
                    // Check for mythicmobs specifications first
                    if (plugin.enabledCompats.contains("MythicMobs")) {
                        MythicMobsCompat mmComp = new MythicMobsCompat();
                        if (mmComp.isMythicMob(event.getEntity())) {
                            isCustomEntity = true;
                            for (String mob : pkConf.getConfigurationSection(levelSystem.getSystemName()+".damage-mobs").getKeys(false)) {
                                if (mmComp.getMobName(event.getEntity()).equals(mob)) {
                                    expFromMob = pkConf.getDouble(levelSystem.getSystemName()+".damage-mobs."+mob);
                                    break;
                                }
                            }
                        }
                    }

                    // if its not a custom entity, do vanilla method
                    if (!isCustomEntity) {
                        for (String mob : pkConf.getConfigurationSection(levelSystem.getSystemName()+".damage-mobs").getKeys(false)) {
                            EntityType entityType = event.getEntity().getType();
                            if (entityType == EntityType.valueOf(mob)) {
                                expFromMob = pkConf.getDouble(levelSystem.getSystemName()+".damage-mobs."+mob);
                                break;
                            }
                        }
                    }

                }

                if (expFromAbility != 0 || expFromMob != 0) {
                    double expToAdd = expFromAbility + expFromMob;
                    LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(event.getSource(), levelSystem.getSystemName());
                    lp.incrementExp(expToAdd*(levelSystem.getExpGainMultiplier()+1));
                    plugin.getDatabaseManager().saveLeveledPlayer(lp);
                }
            });
        }

    }

    // Ability used
    @EventHandler
    public void onAbilityUsed(PlayerCooldownChangeEvent event) {
        pkConf = YamlConfiguration.loadConfiguration(pkYml);

        if (event.getResult() != PlayerCooldownChangeEvent.Result.ADDED) {
            return;
        }

        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {

            if (!(plugin.getDatabaseManager().playerHasRecord(event.getPlayer().getPlayer(), levelSystem.getSystemName()))) {
                return;
            }

            // default exp
            double expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".exp-gain.bending-use-ability");
            // Check if there is a specific amount of exp to gain for this ability
            if (pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-use") != null) {
                for (String ability : pkConf.getConfigurationSection(levelSystem.getSystemName()+".abilities-use").getKeys(false)) {
                    if (event.getAbility().equalsIgnoreCase(ability)) {

                        expFromAbility = pkConf.getDouble(levelSystem.getSystemName()+".abilities-use."+ability);
                    }
                }
            }

            if (expFromAbility != 0) {
                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer((Player) event.getPlayer(), levelSystem.getSystemName());
                lp.incrementExp(expFromAbility*(levelSystem.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });

    }

}
