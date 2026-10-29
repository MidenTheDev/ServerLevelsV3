package us.to.midensthings.serverLevels.compat.events;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import us.to.midensthings.nouveauEnchanting.customevents.PlayerEnchantEvent;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.ExpRateLimiter;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class NouveauEnchantingCompatEvents implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    File neYml = new File(plugin.getDataFolder()+"/Compat/nouveauenchanting.yml");

    // The exp settings for one level system, parsed from nouveauenchanting.yml
    private record SystemEnchantExp(double defaultExp, Map<Enchantment, Map<Integer, Double>> enchantmentExp) {
        double getExp(Enchantment enchantment, int level) {
            Map<Integer, Double> levels = enchantmentExp.get(enchantment);
            Double exp = levels == null ? null : levels.get(level);
            // An enchantment level set to 0 uses the default, same as not listing it
            return (exp != null && exp != 0) ? exp : defaultExp;
        }
    }

    // Parsed settings by level system name. Built once when the config loads, so enchant events don't look up the registry
    private volatile Map<String, SystemEnchantExp> systemExp = Map.of();

    public NouveauEnchantingCompatEvents() {
        reloadConfig();
    }

    public void reloadConfig() {
        YamlConfiguration neConf = YamlConfiguration.loadConfiguration(neYml);
        final Registry<Enchantment> enchantmentRegistry = RegistryAccess
                .registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT);

        Map<String, SystemEnchantExp> parsed = new HashMap<>();
        for (String systemName : neConf.getKeys(false)) {
            double defaultExp = neConf.getDouble(systemName+".exp-gain.on-enchant");
            Map<Enchantment, Map<Integer, Double>> enchantmentExp = new HashMap<>();

            ConfigurationSection enchantments = neConf.getConfigurationSection(systemName+".enchantments");
            if (enchantments != null) {
                for (String enchantKey : enchantments.getKeys(false)) {
                    Enchantment enchantment = null;
                    try {
                        enchantment = enchantmentRegistry.get(RegistryKey.ENCHANTMENT.typedKey(Key.key("minecraft:" + enchantKey)));
                    } catch (InvalidKeyException e) {
                        // Invalid characters in the name, handled below
                    }
                    if (enchantment == null) {
                        plugin.getLogger().warning("Unknown enchantment '" + enchantKey + "' in nouveauenchanting.yml at " + systemName + ".enchantments, skipping it");
                        continue;
                    }

                    ConfigurationSection levels = enchantments.getConfigurationSection(enchantKey);
                    if (levels == null) {
                        continue;
                    }
                    // If the same enchantment is listed twice, the first one wins
                    Map<Integer, Double> levelExp = enchantmentExp.computeIfAbsent(enchantment, e -> new HashMap<>());
                    for (String levelKey : levels.getKeys(false)) {
                        try {
                            levelExp.putIfAbsent(Integer.parseInt(levelKey), levels.getDouble(levelKey));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid enchantment level '" + levelKey + "' in nouveauenchanting.yml at " + systemName + ".enchantments." + enchantKey + ", skipping it");
                        }
                    }
                }
            }
            parsed.put(systemName, new SystemEnchantExp(defaultExp, enchantmentExp));
        }
        systemExp = parsed;
    }

    @EventHandler
    public void onEnchant(PlayerEnchantEvent event) {
        Map<String, SystemEnchantExp> exp = systemExp;
        ExpRateLimiter.Gain gain = plugin.getExpRateLimiter().recordGain(event.getPlayer(), ExpRateLimiter.Source.ENCHANTING, event.getEnchantment().getKey().asString());

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            SystemEnchantExp settings = exp.get(system.getSystemName());
            if (settings == null) {
                return;
            }

            double expToAdd = settings.getExp(event.getEnchantment(), event.getEnchantmentLevel());
            if (expToAdd != 0) {

                LeveledPlayer lp = LeveledPlayer.fromPlayer(event.getPlayer(), system.getSystemName());
                if (lp == null) {
                    // Player isn't in this system
                    return;
                }
                lp.gainExp(expToAdd * gain.multiplierFor(system));
            }
        });

    }
}
