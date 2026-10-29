package us.to.midensthings.serverLevels.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.util.DurationParser;

import java.time.Duration;
import java.util.*;

/***
 * Lowers the exp a player gets from doing the same thing over and over, like a mob grinder killing the same mob
 * or spamming one ability. Each exp action has a source key (e.g. "mob-kill:ZOMBIE"). Repeats of the same key within
 * the window give full exp up to free-repeats, then each further repeat gives less, down to minimum-multiplier.
 * <p>
 * The rate-limiter section in config.yml is the default for every level system. A level system can override any of it
 * with its own rate-limiter section.
 */
public class ExpRateLimiter {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    /***
     * The kinds of exp sources, each can be limited or not in the config
     */
    public enum Source {
        CHAT("chat"),
        MOB_KILL("mob-kill"),
        PLAYER_KILL("player-kill"),
        BLOCK_BREAK("block-break"),
        BLOCK_PLACE("block-place"),
        ENCHANTING("enchanting"),
        BENDING_ABILITY("bending-ability"),
        BENDING_DAMAGE("bending-damage"),
        BENDING_KILL("bending-kill");

        private final String configKey;

        Source(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigKey() {
            return configKey;
        }
    }

    private record Settings(boolean enabled, long windowMillis, int freeRepeats, double reductionPerRepeat,
                            double minimumMultiplier, Set<Source> limitedSources) {

        static final Settings DISABLED = new Settings(false, 0, 0, 0, 1, Set.of());

        boolean limits(Source source) {
            return enabled && limitedSources.contains(source);
        }

        // How many repeats it takes to reach the minimum multiplier, older repeats don't change anything
        int repeatsNeeded() {
            return freeRepeats + (reductionPerRepeat > 0 ? (int) Math.ceil((1 - minimumMultiplier) / reductionPerRepeat) : 0) + 1;
        }

        double multiplier(int repeats) {
            if (repeats < freeRepeats) {
                return 1;
            }
            return Math.max(minimumMultiplier, 1 - (repeats - freeRepeats + 1) * reductionPerRepeat);
        }
    }

    /***
     * One recorded exp gain. Each level system gets its own multiplier from it, using its own rate limiter settings
     */
    public final class Gain {
        private final Source source;
        private final long time;
        // Times of earlier gains with the same key, oldest first
        private final long[] earlierTimes;

        private Gain(Source source, long time, long[] earlierTimes) {
            this.source = source;
            this.time = time;
            this.earlierTimes = earlierTimes;
        }

        /***
         * @return the multiplier for this gain's exp in the system, 1 for full exp
         */
        public double multiplierFor(LevelSystem system) {
            Settings s = settingsFor(system.getSystemName());
            if (!s.limits(source)) {
                return 1;
            }
            int repeats = 0;
            for (long earlier : earlierTimes) {
                if (time - earlier <= s.windowMillis()) {
                    repeats++;
                }
            }
            return s.multiplier(repeats);
        }
    }

    private volatile Settings defaults = Settings.DISABLED;
    // Settings for each level system, from its rate-limiter section on top of the defaults
    private volatile Map<String, Settings> systemSettings = Map.of();
    // The longest window and most repeats any system needs, so recorded gains are kept long enough for all of them
    private volatile long longestWindowMillis = 0;
    private volatile int mostRepeatsNeeded = 0;

    // Times of recent exp gains for each player, by source key
    private final Map<UUID, Map<String, ArrayDeque<Long>>> recentGains = new HashMap<>();

    /***
     * Reads the default settings from config.yml and each level system's overrides from levelsystems.yml
     */
    public void load(FileConfiguration config, YamlConfiguration levelSystemsConf) {
        Settings newDefaults = readSettings(config.getConfigurationSection("rate-limiter"), Settings.DISABLED, "config.yml");

        Map<String, Settings> newSystemSettings = new HashMap<>();
        for (String system : levelSystemsConf.getKeys(false)) {
            ConfigurationSection override = levelSystemsConf.getConfigurationSection(system + ".rate-limiter");
            newSystemSettings.put(system, override == null ? newDefaults : readSettings(override, newDefaults, system));
        }

        long longestWindow = newDefaults.enabled() ? newDefaults.windowMillis() : 0;
        int mostRepeats = newDefaults.enabled() ? newDefaults.repeatsNeeded() : 0;
        for (Settings s : newSystemSettings.values()) {
            if (s.enabled()) {
                longestWindow = Math.max(longestWindow, s.windowMillis());
                mostRepeats = Math.max(mostRepeats, s.repeatsNeeded());
            }
        }

        synchronized (this) {
            defaults = newDefaults;
            systemSettings = newSystemSettings;
            longestWindowMillis = longestWindow;
            mostRepeatsNeeded = mostRepeats;
            if (longestWindow == 0) {
                recentGains.clear();
            }
        }
    }

    // Reads a rate-limiter section, using the fallback for anything it doesn't set
    private Settings readSettings(ConfigurationSection section, Settings fallback, String where) {
        if (section == null) {
            return fallback;
        }
        boolean enabled = section.getBoolean("enabled", fallback.enabled());
        if (!enabled) {
            return Settings.DISABLED;
        }

        long windowMillis = fallback.windowMillis() > 0 ? fallback.windowMillis() : Duration.ofMinutes(1).toMillis();
        if (section.contains("window")) {
            String windowText = section.getString("window");
            Duration window = DurationParser.parse(windowText);
            if (window == null || window.toSeconds() < 1) {
                plugin.getLogger().warning("Invalid rate-limiter window '" + windowText + "' for " + where + ", using "
                        + DurationParser.format(Duration.ofMillis(windowMillis)));
            } else {
                windowMillis = window.toMillis();
            }
        }

        // Sources not listed keep the fallback's setting. With no fallback, every source except block breaking and
        // enchanting is limited, same as the default config.yml
        boolean fallbackHasSources = fallback.enabled();
        EnumSet<Source> limited = EnumSet.noneOf(Source.class);
        for (Source source : Source.values()) {
            boolean defaultValue = fallbackHasSources
                    ? fallback.limitedSources().contains(source)
                    : source != Source.BLOCK_BREAK && source != Source.ENCHANTING;
            if (section.getBoolean("sources." + source.getConfigKey(), defaultValue)) {
                limited.add(source);
            }
        }

        return new Settings(true, windowMillis,
                Math.max(0, section.getInt("free-repeats", fallback.enabled() ? fallback.freeRepeats() : 10)),
                Math.max(0, section.getDouble("reduction-per-repeat", fallback.enabled() ? fallback.reductionPerRepeat() : 0.1)),
                Math.clamp(section.getDouble("minimum-multiplier", fallback.enabled() ? fallback.minimumMultiplier() : 0.1), 0, 1),
                limited);
    }

    private Settings settingsFor(String system) {
        return systemSettings.getOrDefault(system, defaults);
    }

    /***
     * Records an exp gain from the source. Call this once per action, not once per level system, so one action only
     * counts as one repeat, then use the result's multiplierFor for each system's exp.
     * @param key what was done, e.g. the mob type or ability name. Repeats are counted per key
     */
    public synchronized Gain recordGain(Player p, Source source, String key) {
        long now = System.currentTimeMillis();
        if (longestWindowMillis == 0) {
            // No system limits anything, nothing to record
            return new Gain(source, now, new long[0]);
        }

        ArrayDeque<Long> times = recentGains
                .computeIfAbsent(p.getUniqueId(), uuid -> new HashMap<>())
                .computeIfAbsent(source.getConfigKey() + ":" + key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > longestWindowMillis) {
            times.pollFirst();
        }
        long[] earlier = times.stream().mapToLong(Long::longValue).toArray();

        times.addLast(now);
        // Once every system is at its minimum multiplier, older entries don't change anything, so cap the memory used
        while (times.size() > mostRepeatsNeeded) {
            times.pollFirst();
        }
        return new Gain(source, now, earlier);
    }

    /***
     * Forgets a player's recent gains, called when they log off
     */
    public synchronized void clearPlayer(UUID uuid) {
        recentGains.remove(uuid);
    }
}
