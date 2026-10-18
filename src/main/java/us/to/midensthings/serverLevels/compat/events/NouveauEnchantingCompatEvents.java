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

    public NouveauEnchantingCompatEvents() {
        reloadConfig();
    }

    public void reloadConfig() {
        neConf = YamlConfiguration.loadConfiguration(neYml);
    }

    @EventHandler
    public void onEnchant(PlayerEnchantEvent event) {
        final Registry<Enchantment> enchantmentRegistry = RegistryAccess
                .registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT);

        plugin.getLevelSystemRegistry().getAllSystems().forEach(system -> {

            // Start with default exp amount
            double expToAdd = neConf.getDouble(system.getSystemName()+".exp-gain.on-enchant");
            if (neConf.getConfigurationSection(system.getSystemName()+".enchantments") != null) {
                for (String enchantKey : neConf.getConfigurationSection(system.getSystemName()+".enchantments").getKeys(false)) {
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

                LeveledPlayer lp = LeveledPlayer.fromPlayer(event.getPlayer(), system.getSystemName());
                if (lp == null) {
                    // Player isn't in this system
                    return;
                }
                lp.incrementExp(expToAdd);
                lp.save();
            }
        });

    }
}
