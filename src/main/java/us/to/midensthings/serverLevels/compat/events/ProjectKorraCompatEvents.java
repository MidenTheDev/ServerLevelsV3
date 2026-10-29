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
import us.to.midensthings.serverLevels.util.EntityUtil;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.data.ExpRateLimiter;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.io.File;

public class ProjectKorraCompatEvents implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    File pkYml = new File(plugin.getDataFolder()+"/Compat/projectkorra.yml");
    // volatile since /sl reload replaces it from a background thread
    volatile YamlConfiguration pkConf;

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
        // Only real mobs and players give exp, not armor stands
        if (!EntityUtil.canGiveExp(event.getEntity())) {
            return;
        }
        Player attacker = event.getAttacker();
        String abilityName = event.getAbility().getName();

        if (event.getEntity() instanceof Player victim) {
            // player death. Repeats are counted per victim, so killing the same player over and over gives less exp
            ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(attacker, ExpRateLimiter.Source.BENDING_KILL, victim.getUniqueId().toString());
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-kill-player", "abilities-kill-player", abilityName);
                giveExp(attacker, levelSystem, expFromAbility * gain.multiplierFor(levelSystem));
            });

        } else {
            // not player death. Repeats are counted per mob type, like a mob grinder
            ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(attacker, ExpRateLimiter.Source.BENDING_KILL, mobKey(event.getEntity()));
            boolean fromSpawner = EntityUtil.isFromSpawner(event.getEntity());
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-kill-mob", "abilities-kill-mob", abilityName);
                double expFromMob = getMobExp(levelSystem, "kill-mobs", event.getEntity());
                double spawnerMultiplier = fromSpawner ? levelSystem.getSpawnerMobMultiplier() : 1;
                giveExp(attacker, levelSystem, (expFromAbility + expFromMob) * spawnerMultiplier * gain.multiplierFor(levelSystem));
            });
        }

    }

    // Entity damaged by bending
    @EventHandler
    public void entityDamagedByBending(AbilityDamageEntityEvent event) {

        // Only real mobs and players give exp. Armor stands count as living entities, so checking for LivingEntity isn't enough
        if (!EntityUtil.canGiveExp(event.getEntity()) || !(event.getEntity() instanceof LivingEntity le)) {
            return;
        }

        if (le.getHealth() <= event.getDamage()) {
            // Entity was killed. Let the death event above handle it.
            return;
        }

        Player source = event.getSource();
        String abilityName = event.getAbility().getName();
        // Repeats are counted per ability, so spamming one ability on a target gives less exp
        ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(source, ExpRateLimiter.Source.BENDING_DAMAGE, abilityName);

        if (event.getEntity() instanceof Player) {
            // player damaged
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-damage-player", "abilities-damage-player", abilityName);
                giveExp(source, levelSystem, expFromAbility * gain.multiplierFor(levelSystem));
            });

        } else {
            // mob damaged
            boolean fromSpawner = EntityUtil.isFromSpawner(event.getEntity());
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "bending-damage-mob", "abilities-damage-mob", abilityName);
                double expFromMob = getMobExp(levelSystem, "damage-mobs", event.getEntity());
                double spawnerMultiplier = fromSpawner ? levelSystem.getSpawnerMobMultiplier() : 1;
                giveExp(source, levelSystem, (expFromAbility + expFromMob) * spawnerMultiplier * gain.multiplierFor(levelSystem));
            });
        }

    }

    /***
     * Gives exp for using an ability. Called once per use by PKAbilityUseTracker, which works out when an ability was used
     */
    public void onAbilityUsed(Player player, String abilityName) {
        ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(player, ExpRateLimiter.Source.BENDING_ABILITY, abilityName);
        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
            double expFromAbility = getAbilityExp(levelSystem, "bending-use-ability", "abilities-use", abilityName);
            giveExp(player, levelSystem, expFromAbility * gain.multiplierFor(levelSystem));
        });
    }

    // The rate limiter key for a mob, its MythicMobs name if it has one, otherwise its type
    private String mobKey(Entity entity) {
        if (plugin.enabledCompats.contains("MythicMobs")) {
            MythicMobsCompat mmComp = plugin.getMythicMobsCompat();
            if (mmComp.isMythicMob(entity)) {
                return String.valueOf(mmComp.getMobName(entity));
            }
        }
        return entity.getType().name();
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
        lp.gainExp(exp);
    }

}
