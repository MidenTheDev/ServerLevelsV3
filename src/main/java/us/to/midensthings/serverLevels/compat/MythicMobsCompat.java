package us.to.midensthings.serverLevels.compat;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import us.to.midensthings.serverLevels.ServerLevels;

import java.io.File;

public class MythicMobsCompat {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    private File mmYml = new File(plugin.getDataFolder()+"/Compat/mythicmobs.yml");
    // volatile since /sl reload replaces it from a background thread
    private volatile YamlConfiguration mmConf;

    public MythicMobsCompat() {
        reloadConfig();
    }

    public void reloadConfig() {
        mmConf = YamlConfiguration.loadConfiguration(mmYml);
    }

    public boolean isMythicMob(Entity entity) {
        return MythicBukkit.inst().getMobManager().isMythicMob(entity);
    }
    public boolean isMythicMob(String mobName) {
        return MythicBukkit.inst().getMobManager().getMobTypes().contains(mobName);
    }

    /***
     * @return the internal MythicMobs name of the entity, or null if it isn't an active mythic mob
     */
    public String getMobName(Entity entity) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(activeMob -> activeMob.getType().getInternalName())
                .orElse(null);
    }

    public YamlConfiguration getConfig() {
        return mmConf;
    }
}
