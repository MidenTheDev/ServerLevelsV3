package us.to.midensthings.serverLevels.compat.events;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import us.to.midensthings.nouveauEnchanting.customevents.PlayerEnchantEvent;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.io.File;

public class NouveauEnchantingCompatEvents implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    File neYml = new File(plugin.getDataFolder()+"/Compat/nouveauenchanting.yml");
    YamlConfiguration neConf;

    @EventHandler
    public void onEnchant(PlayerEnchantEvent event) {
        neConf = YamlConfiguration.loadConfiguration(neYml);
        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {
            // Start with default exp amount
            double expToAdd = neConf.getDouble(system.getSystemName()+".exp-gain.on-enchant");
            if (neConf.getConfigurationSection(system.getSystemName()+".enchantments") != null) {
                for (String enchantKey : neConf.getConfigurationSection(system.getSystemName()+".enchantments").getKeys(false)) {
                    final Registry<Enchantment> enchantmentRegistry = RegistryAccess
                            .registryAccess()
                            .getRegistry(RegistryKey.ENCHANTMENT);
                    Enchantment enchantment = enchantmentRegistry.get(
                            RegistryKey.ENCHANTMENT.typedKey(Key.key("minecraft:" + enchantKey)));

                    if (enchantment == event.getEnchantment()) {
                        if (neConf.getDouble(system.getSystemName()+".enchantments."+enchantKey+"."+event.getEnchantmentLevel()) != 0) {
                            expToAdd = neConf.getDouble(system.getSystemName()+".enchantments."+enchantKey+"."+event.getEnchantmentLevel());
                            break;
                        }
                    }
                }
            }

            if (expToAdd != 0) {

                LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(event.getPlayer(), system.getSystemName());
                lp.incrementExp(expToAdd*(system.getExpGainMultiplier()+1));
                plugin.getDatabaseManager().saveLeveledPlayer(lp);
            }
        });

    }
}
