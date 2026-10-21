package us.to.midensthings.serverLevels.compat;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Set;

public class PAPIExpansion extends PlaceholderExpansion {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    @Override
    public @NotNull String getIdentifier() {
        return "serverlevels";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ",plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    // Formats exp and multipliers like the exp gain message, e.g. 12.34. One per thread, placeholders can be requested from any thread
    private static final ThreadLocal<DecimalFormat> NUMBER_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat df = new DecimalFormat("0.##");
        df.setRoundingMode(RoundingMode.DOWN);
        return df;
    });

    // Placeholders that need a player who is in the system
    private static final Set<String> PLAYER_PLACEHOLDERS = Set.of(
            "level", "exp", "exp_multiplier", "next_level_exp", "exp_to_next", "progress", "paused");

    /*
     Placeholders, all as %serverlevels_<system>_<placeholder>%:
       level, exp, exp_multiplier  the player's level, exp, and personal exp multiplier
       next_level_exp              total exp needed for the next level, MAX at max level
       exp_to_next                 exp left until the next level, 0 at max level
       progress                    percent through the current level (0-100), same as /sl progress
       paused                      whether the player's exp gains are paused, using PlaceholderAPI's true/false text
       max_level                   the system's max level
       booster                     the active booster's multiplier, 1 with no booster
     Player placeholders are empty for offline players and players who aren't in the system.
     */
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Find the system the placeholder is for. System names can contain underscores, so use the longest name that matches
        String lowerParams = params.toLowerCase(Locale.ROOT);
        LevelSystem system = null;
        for (LevelSystem candidate : plugin.getLevelSystemRegistry().getAllSystems()) {
            String prefix = candidate.getSystemName().toLowerCase(Locale.ROOT) + "_";
            if (lowerParams.startsWith(prefix) && (system == null || candidate.getSystemName().length() > system.getSystemName().length())) {
                system = candidate;
            }
        }
        if (system == null) {
            return null;
        }
        String placeholder = lowerParams.substring(system.getSystemName().length() + 1);
        DecimalFormat df = NUMBER_FORMAT.get();

        // Placeholders that don't need a player
        switch (placeholder) {
            case "max_level":
                return String.valueOf(system.getMaxLevel());
            case "booster":
                return df.format(plugin.getBoosterManager().getMultiplier(system.getSystemName()));
        }
        if (!PLAYER_PLACEHOLDERS.contains(placeholder)) {
            return null;
        }

        // Only online players are cached. Loading offline players from the database here would block whichever thread asked
        LeveledPlayer lp = player == null ? null : system.getPlayerCache().getLeveledPlayer(player.getUniqueId());
        if (lp == null) {
            // Offline, or not in this system
            return "";
        }
        boolean maxLevel = lp.getLevel() >= system.getMaxLevel();
        return switch (placeholder) {
            case "level" -> String.valueOf(lp.getLevel());
            case "exp" -> df.format(lp.getExp());
            case "exp_multiplier" -> df.format(lp.getExpMultiplier());
            case "next_level_exp" -> maxLevel ? "MAX" : df.format(lp.getNextLevelExp());
            case "exp_to_next" -> maxLevel ? "0" : df.format(Math.max(0, lp.getNextLevelExp() - lp.getExp()));
            // Rounded down, so it never shows 100 before the player levels up
            case "progress" -> String.valueOf((int) Math.floor(lp.getLevelProgress() * 100));
            case "paused" -> lp.isPaused() ? PlaceholderAPIPlugin.booleanTrue() : PlaceholderAPIPlugin.booleanFalse();
            default -> null;
        };
    }

}
