package us.to.midensthings.serverLevels.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.data.SystemConfigs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            new Migration("2.2", ConfigUpdater::addRateLimiter),
            // Only changes config-version itself from a number to text, see update
            new Migration("2.3", config -> {}),
            new Migration("2.4", ConfigUpdater::addDatabaseOptions),
            new Migration("2.5", ConfigUpdater::addBoosterMessages),
            new Migration("2.6", ConfigUpdater::moveCommentsAboveKeys)
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
            // Written as text. As a number, a version like 2.10 would be read back as 2.1
            config.set("config-version", migration.version());
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

        // Before 2.6, comments in config.yml sat below the option they describe, which Bukkit stores as comments above the next key.
        // Move config-version to the end so autosave-interval goes before it, and keep the comment layout the same
        List<String> commentsAboveVersion = config.getComments("config-version");
        config.set("config-version", null);

        config.set("autosave-interval", "5m");
        config.setComments("autosave-interval", commentsAboveVersion);

        config.set("config-version", "0");
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

        config.set("config-version", "0");
        config.setComments("config-version", new ArrayList<>(Arrays.asList(
                "Lowers exp for doing the same thing over and over, like a mob grinder killing the same mob or spamming one ability.",
                "A repeat is the same source and thing, e.g. killing the same type of mob, using the same ability, or placing the same block.",
                "  window: how far back repeats are counted. free-repeats: how many repeats in the window still give full exp.",
                "  reduction-per-repeat: each repeat after that gives this much less (0.1 = 10% less each time).",
                "  minimum-multiplier: exp never drops below this fraction of normal. 0 lets it drop to no exp at all.",
                "  sources: which kinds of exp are limited. block-break is off by default, since mining the same block is normal play.",
                "These are the defaults for every level system. A system can override any of them with its own rate-limiter section in its file in systems/.",
                null // blank line
        )));
    }

    // 2.4: mysql.pool-size, mysql.ssl-mode, table-prefix
    private static void addDatabaseOptions(FileConfiguration config) {
        if (!config.contains("mysql.pool-size", true)) {
            config.set("mysql.pool-size", 10);
            config.setInlineComments("mysql.pool-size", List.of("How many connections to keep open to the MySQL server"));
        }
        if (!config.contains("mysql.ssl-mode", true)) {
            config.set("mysql.ssl-mode", "PREFERRED");
            config.setInlineComments("mysql.ssl-mode", List.of("DISABLED, PREFERRED, REQUIRED, VERIFY_CA or VERIFY_IDENTITY"));
        }
        if (config.contains("table-prefix", true)) {
            return;
        }

        // Same layout trick as addAutosaveInterval, so table-prefix goes before config-version with its comments below it
        List<String> commentsAboveVersion = config.getComments("config-version");
        config.set("config-version", null);

        config.set("table-prefix", "sys_");
        config.setComments("table-prefix", commentsAboveVersion);

        config.set("config-version", "0");
        config.setComments("config-version", new ArrayList<>(Arrays.asList(
                "Put in front of each level system's name for its database table, e.g. sys_mining. Use a different prefix for each server when",
                "several servers share one MySQL database. Changing it starts new, empty tables, it does not rename existing ones.",
                null // blank line
        )));
    }

    // 2.5: booster-start-message, booster-end-message. They used to be set per level system, with broadcast-boosters to turn them on
    private static void addBoosterMessages(FileConfiguration config) {
        // Level system files are converted the next time they load (each one keeps whether it broadcast before)
        SystemConfigs.requestBoosterMessageUpgrade();
        if (config.contains("booster-start-message", true)) {
            return;
        }

        // Same layout trick as addAutosaveInterval, so the messages go before config-version with their comments below them
        List<String> commentsAboveVersion = config.getComments("config-version");
        config.set("config-version", null);

        config.set("booster-start-message", LevelSystem.DEFAULT_BOOSTER_START_MSG);
        config.setComments("booster-start-message", commentsAboveVersion);
        config.set("booster-end-message", LevelSystem.DEFAULT_BOOSTER_END_MSG);

        config.set("config-version", "0");
        config.setComments("config-version", new ArrayList<>(Arrays.asList(
                "Broadcast to everyone when an exp booster starts or ends. Boosters are started with /sl booster. Messages use MiniMessage and can",
                "use %multiplier%, %system%, and %duration% (start message only). Set a message to '' to turn it off. A level system can set its",
                "own booster-start-message and booster-end-message in its file, including '' to turn them off for that system only.",
                null // blank line
        )));
    }

    // 2.6: comments moved above the options they describe, and the options put in the same order as a new config.yml
    private static void moveCommentsAboveKeys(FileConfiguration config) {
        if (!(config.getDefaults() instanceof FileConfiguration defaults)) {
            return;
        }

        // Take every option out, then add them back in the order of the jar's config.yml. Options the jar doesn't have
        // keep their place relative to each other, after the jar's options and before config-version
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, List<String>> comments = new LinkedHashMap<>();
        for (String key : config.getKeys(false)) {
            values.put(key, toPlainValue(config.get(key)));
            comments.put(key, config.getComments(key));
            config.set(key, null);
        }

        List<String> order = new ArrayList<>();
        for (String key : defaults.getKeys(false)) {
            if (values.containsKey(key) && !key.equals("config-version")) {
                order.add(key);
            }
        }
        for (String key : values.keySet()) {
            if (!order.contains(key) && !key.equals("config-version")) {
                order.add(key);
            }
        }
        order.add("config-version");

        for (String key : order) {
            Object value = values.get(key);
            if (value instanceof Map<?, ?> map) {
                config.createSection(key, map);
            } else {
                config.set(key, value);
            }
            if (!defaults.contains(key, true)) {
                config.setComments(key, comments.get(key));
            }
        }

        // Every option the jar has gets the jar's comments, which are now above the option
        for (String path : defaults.getKeys(true)) {
            if (config.contains(path, true)) {
                config.setComments(path, defaults.getComments(path));
                config.setInlineComments(path, defaults.getInlineComments(path));
            }
        }
    }

    // Copies a section into nested maps, so it can be added back after being removed from the config
    private static Object toPlainValue(Object value) {
        if (!(value instanceof ConfigurationSection section)) {
            return value;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, toPlainValue(section.get(key)));
        }
        return map;
    }
}
