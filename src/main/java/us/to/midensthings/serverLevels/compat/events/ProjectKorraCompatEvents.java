package us.to.midensthings.serverLevels.compat.events;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.event.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.SystemConfigs;
import us.to.midensthings.serverLevels.util.EntityUtil;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.data.ExpRateLimiter;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.io.File;

public class ProjectKorraCompatEvents implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    // volatile since /sl reload replaces it from a background thread
    volatile YamlConfiguration pkConf;

    public ProjectKorraCompatEvents() {
        reloadConfig();
    }

    public void reloadConfig() {
        // The projectkorra section of each system file, keyed by system name
        pkConf = plugin.getCompatConf(SystemConfigs.PROJECTKORRA);
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
                double expFromAbility = getAbilityExp(levelSystem, "kill-player", abilityName);
                giveExp(attacker, levelSystem, expFromAbility * gain.multiplierFor(levelSystem));
            });

        } else {
            // not player death. Repeats are counted per mob type, like a mob grinder
            ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(attacker, ExpRateLimiter.Source.BENDING_KILL, mobKey(event.getEntity()));
            boolean fromSpawner = EntityUtil.isFromSpawner(event.getEntity());
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "kill-mob", abilityName);
                double expFromMob = getMobExp(levelSystem, "kill", event.getEntity());
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
                double expFromAbility = getAbilityExp(levelSystem, "damage-player", abilityName);
                giveExp(source, levelSystem, expFromAbility * gain.multiplierFor(levelSystem));
            });

        } else {
            // mob damaged
            boolean fromSpawner = EntityUtil.isFromSpawner(event.getEntity());
            plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                double expFromAbility = getAbilityExp(levelSystem, "damage-mob", abilityName);
                double expFromMob = getMobExp(levelSystem, "damage", event.getEntity());
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
            double expFromAbility = getAbilityExp(levelSystem, "use", abilityName);
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

    /***
     * Gets the exp for an ability from the system's projectkorra section, checking the ability first, then its sub-element
     * (e.g. lightning), then its element (e.g. fire), then the defaults
     * @param type use, kill-player, kill-mob, damage-player or damage-mob
     */
    private double getAbilityExp(LevelSystem levelSystem, String type, String abilityName) {
        ConfigurationSection settings = pkConf.getConfigurationSection(levelSystem.getSystemName());
        if (settings == null) {
            return 0;
        }

        Double exp = findExp(settings.getConfigurationSection("abilities"), abilityName, type);
        if (exp != null) {
            return exp;
        }

        CoreAbility ability = CoreAbility.getAbility(abilityName);
        if (ability != null) {
            Element element = ability.getElement();
            while (element != null) {
                exp = findExp(settings.getConfigurationSection("elements"), element.getName(), type);
                if (exp != null) {
                    return exp;
                }
                element = element instanceof Element.SubElement subElement ? subElement.getParentElement() : null;
            }
        }
        return settings.getDouble("defaults." + type, 0);
    }

    // The exp of the given type for the named entry (ability or element, any case), or null if it doesn't set one
    private static Double findExp(ConfigurationSection section, String name, String type) {
        if (section == null) {
            return null;
        }
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(name)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                return entry != null && entry.contains(type) ? entry.getDouble(type) : null;
            }
        }
        return null;
    }

    // Gets the extra exp for bending the mob, from mobs.kill or mobs.damage. MythicMobs names are checked for mythic mobs,
    // vanilla entity types otherwise
    private double getMobExp(LevelSystem levelSystem, String type, Entity entity) {
        ConfigurationSection mobs = pkConf.getConfigurationSection(levelSystem.getSystemName()+".mobs."+type);
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
