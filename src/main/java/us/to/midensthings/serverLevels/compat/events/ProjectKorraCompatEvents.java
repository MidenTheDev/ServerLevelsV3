package us.to.midensthings.serverLevels.compat.events;

import com.projectkorra.projectkorra.event.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.io.File;

public class ProjectKorraCompatEvents implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    File pkYml = new File(plugin.getDataFolder()+"/Compat/projectkorra.yml");
    YamlConfiguration pkConf;

    public ProjectKorraCompatEvents() {
        reloadConfig();
    }

    public void reloadConfig() {
        pkConf = YamlConfiguration.loadConfiguration(pkYml);
    }

    // Events based on entity damage and death will fire twice due to the built-in mobkill and playerkill events!

    // Entity killed by bending
    @EventHandler
    public void entityKilledByBending(EntityBendingDeathEvent event) {

        if (event.getEntity() instanceof Player) {
            // player death
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-kill-player", "abilities-kill-player", event.getAbility().getName());
                giveExp(event.getAttacker(), levelSystem, expFromAbility);
            });

        } else {
            // not player death
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-kill-mob", "abilities-kill-mob", event.getAbility().getName());
                double expFromMob = getMobExp(levelSystem, "kill-mobs", event.getEntity());
                giveExp(event.getAttacker(), levelSystem, expFromAbility + expFromMob);
            });
        }

    }

    // Entity damaged by bending
    @EventHandler
    public void entityDamagedByBending(AbilityDamageEntityEvent event) {

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity le = (LivingEntity) event.getEntity();
        if (le.getHealth() <= event.getDamage()) {
            // Entity was killed. Let the death event above handle it.
            return;
        }


        if (event.getEntity() instanceof Player) {
            // player damaged
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-damage-player", "abilities-damage-player", event.getAbility().getName());
                giveExp(event.getSource(), levelSystem, expFromAbility);
            });

        } else {
            // mob damaged
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-damage-mob", "abilities-damage-mob", event.getAbility().getName());
                double expFromMob = getMobExp(levelSystem, "damage-mobs", event.getEntity());
                giveExp(event.getSource(), levelSystem, expFromAbility + expFromMob);
            });
        }

    }

    // Ability used
    @EventHandler
    public void onAbilityUsed(PlayerCooldownChangeEvent event) {

        if (event.getResult() != PlayerCooldownChangeEvent.Result.ADDED) {
            return;
        }

        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
            double expFromAbility = getAbilityExp(levelSystem, "bending-use-ability", "abilities-use", event.getAbility());
            giveExp((Player) event.getPlayer(), levelSystem, expFromAbility);
        });

    }

    // Gets the default exp from exp-gain, or the ability specific exp if the ability is listed in the given section
    private double getAbilityExp(LevelSystem levelSystem, String defaultKey, String abilitySection, String abilityName) {
        String systemName = levelSystem.getSystemName();
        double exp = pkConf.getDouble(systemName+".exp-gain."+defaultKey);

        ConfigurationSection abilities = pkConf.getConfigurationSection(systemName+"."+abilitySection);
        if (abilities != null) {
            for (String ability : abilities.getKeys(false)) {
                if (abilityName.equalsIgnoreCase(ability)) {
                    exp = abilities.getDouble(ability);
                    break;
                }
            }
        }
        return exp;
    }

    // Gets the exp for the mob from the given section. MythicMobs names are checked for mythic mobs, vanilla entity types otherwise
    private double getMobExp(LevelSystem levelSystem, String mobSection, Entity entity) {
        ConfigurationSection mobs = pkConf.getConfigurationSection(levelSystem.getSystemName()+"."+mobSection);
        if (mobs == null) {
            return 0;
        }

        String mobName = entity.getType().name();
        // Check for mythicmobs specifications first
        if (plugin.enabledCompats.contains("MythicMobs")) {
            MythicMobsCompat mmComp = plugin.getMythicMobsCompat();
            if (mmComp.isMythicMob(entity)) {
                mobName = mmComp.getMobName(entity);
            }
        }

        for (String mob : mobs.getKeys(false)) {
            if (mob.equals(mobName)) {
                return mobs.getDouble(mob);
            }
        }
        return 0;
    }

    private void giveExp(Player player, LevelSystem levelSystem, double exp) {
        if (exp == 0) {
            return;
        }
        LeveledPlayer lp = LeveledPlayer.fromPlayer(player,levelSystem.getSystemName());
        if (lp == null) {
            // Player isn't in this system
            return;
        }
        lp.incrementExp(exp);
        lp.save();
    }

}
