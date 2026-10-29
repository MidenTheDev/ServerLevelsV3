package us.to.midensthings.serverLevels.util;

import org.bukkit.configuration.file.FileConfiguration;
import us.to.midensthings.serverLevels.ServerLevels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/***
 * Brings older config.yml files up to the current config version, adding new options without touching existing ones.
 */
public final class ConfigUpdater {

    // An update that brings the config up to the given version
    private record Migration(String version, Consumer<FileConfiguration> apply) {}

    // Must be in version order. Add a new entry here whenever the config format changes
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration("2.1", ConfigUpdater::addAutosaveInterval),
            new Migration("2.2", ConfigUpdater::addRateLimiter)
    );

    private ConfigUpdater() {}

    /***
     * Applies every update newer than the config's current version, then saves the config if anything changed.
     */
    public static void update(ServerLevels plugin) {
        FileConfiguration config = plugin.getConfig();
        // Pass null as the default so the value from the jar's config.yml isn't used when the user's config has no version
        Object rawVersion = config.get("config-version", null);
        String currentVersion = rawVersion == null ? "0" : rawVersion.toString();

        boolean updated = false;
        for (Migration migration : MIGRATIONS) {
            if (!UpdateTracker.isNewer(migration.version(), currentVersion)) {
                continue;
            }
            plugin.getLogger().info("Updating config.yml from version " + currentVersion + " to " + migration.version());
            migration.apply().accept(config);
            // Written as a number to match the existing config-version format
            config.set("config-version", Double.parseDouble(migration.version()));
            currentVersion = migration.version();
            updated = true;
        }

        if (updated) {
            plugin.saveConfig();
        }
    }

    // 2.1: autosave-interval
    private static void addAutosaveInterval(FileConfiguration config) {
        if (config.contains("autosave-interval", true)) {
            return;
        }

        // Comments in config.yml sit below the option they describe, which Bukkit stores as comments above the next key.
        // Move config-version to the end so autosave-interval goes before it, and keep the comment layout the same
        List<String> commentsAboveVersion = config.getComments("config-version");
        config.set("config-version", null);

        config.set("autosave-interval", "5m");
        config.setComments("autosave-interval", commentsAboveVersion);

        config.set("config-version", 0.0);
        config.setComments("config-version", new ArrayList<>(Arrays.asList(
                "How often player data is saved to the database. Combine d, h, m and s, e.g. 1d5h4m2s, 30m or 45s.",
                "Player data is also saved when a player logs off and when the server stops.",
                null // blank line
        )));
    }

    // 2.2: rate-limiter
    private static void addRateLimiter(FileConfiguration config) {
        if (config.contains("rate-limiter", true)) {
            return;
        }

        // Same layout trick as addAutosaveInterval, so the section goes before config-version with its comments below it
        List<String> commentsAboveVersion = config.getComments("config-version");
        config.set("config-version", null);

        config.set("rate-limiter.enabled", true);
        config.set("rate-limiter.window", "1m");
        config.set("rate-limiter.free-repeats", 10);
        config.set("rate-limiter.reduction-per-repeat", 0.1);
        config.set("rate-limiter.minimum-multiplier", 0.1);
        config.set("rate-limiter.sources.chat", true);
        config.set("rate-limiter.sources.mob-kill", true);
        config.set("rate-limiter.sources.player-kill", true);
        config.set("rate-limiter.sources.block-break", false);
        config.set("rate-limiter.sources.block-place", true);
        config.set("rate-limiter.sources.enchanting", false);
        config.set("rate-limiter.sources.bending-ability", true);
        config.set("rate-limiter.sources.bending-damage", true);
        config.set("rate-limiter.sources.bending-kill", true);
        config.setComments("rate-limiter", commentsAboveVersion);

        config.set("config-version", 0.0);
        config.setComments("config-version", new ArrayList<>(Arrays.asList(
                "Lowers exp for doing the same thing over and over, like a mob grinder killing the same mob or spamming one ability.",
                "A repeat is the same source and thing, e.g. killing the same type of mob, using the same ability, or placing the same block.",
                "  window: how far back repeats are counted. free-repeats: how many repeats in the window still give full exp.",
                "  reduction-per-repeat: each repeat after that gives this much less (0.1 = 10% less each time).",
                "  minimum-multiplier: exp never drops below this fraction of normal. 0 lets it drop to no exp at all.",
                "  sources: which kinds of exp are limited. block-break is off by default, since mining the same block is normal play.",
                null // blank line
        )));
    }
}
