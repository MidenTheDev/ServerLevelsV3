package us.to.midensthings.serverLevels.compat;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import us.to.midensthings.serverLevels.ServerLevels;

import java.io.File;
import java.util.Optional;

public class MythicMobsCompat {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    private File mmYml = new File(plugin.getDataFolder()+"/Compat/mythicmobs.yml");
    private YamlConfiguration mmConf;

    public MythicMobsCompat() {
        mmConf = YamlConfiguration.loadConfiguration(mmYml);
    }

    public boolean isMythicMob(Entity entity) {
        return MythicBukkit.inst().getMobManager().isMythicMob(entity);
    }
    public boolean isMythicMob(String mobName) {
        return MythicBukkit.inst().getMobManager().getMobTypes().contains(mobName);
    }

    public String getMobName(Entity entity) {
        Optional<ActiveMob> optActiveMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
        return optActiveMob.get().getType().getInternalName();
    }

    public YamlConfiguration getConfig() {
        return mmConf;
    }
}
