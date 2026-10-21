package us.to.midensthings.serverLevels.compat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;

public class PAPIExpansion extends PlaceholderExpansion {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    @Override
    public @NotNull String getIdentifier() {
        return "ServerLevelsExpansion";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Miden";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        for (LevelSystem system : plugin.getLevelSystemRegistry().getAllSystems()) {
            if (params.equalsIgnoreCase(system+"_level")) {
                return String.valueOf(system.getLeveledPlayer(player.getPlayer()).getLevel());
            }
            if (params.equalsIgnoreCase(system+"_exp")) {
                return String.valueOf(system.getLeveledPlayer(player.getPlayer()).getExp());
            }
            if (params.equalsIgnoreCase(system+"_exp_multiplier")) {
                return String.valueOf(system.getLeveledPlayer(player.getPlayer()).getExpMultiplier());
            }

        }
        return null;
    }

}
