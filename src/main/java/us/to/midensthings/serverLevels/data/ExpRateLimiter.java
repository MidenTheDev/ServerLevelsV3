package us.to.midensthings.serverLevels.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.util.DurationParser;

import java.time.Duration;
import java.util.*;

/***
 * Lowers the exp a player gets from doing the same thing over and over, like a mob grinder killing the same mob
 * or spamming one ability. Each exp action has a source key (e.g. "mob-kill:ZOMBIE"). Repeats of the same key within
 * the window give full exp up to free-repeats, then each further repeat gives less, down to minimum-multiplier.
 * Settings are under rate-limiter in config.yml.
 */
public class ExpRateLimiter {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    /***
     * The kinds of exp sources, each can be limited or not in config.yml
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
                            double minimumMultiplier, Set<Source> limitedSources) {}

    private volatile Settings settings = new Settings(false, 0, 0, 0, 1, Set.of());

    // Times of recent exp gains for each player, by source key. Only touched on the main thread, synchronized to be safe for API use
    private final Map<UUID, Map<String, ArrayDeque<Long>>> recentGains = new HashMap<>();

    /***
     * Reads the rate-limiter settings from config.yml
     */
    public void load(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("rate-limiter");
        if (section == null || !section.getBoolean("enabled", false)) {
            settings = new Settings(false, 0, 0, 0, 1, Set.of());
            synchronized (this) {
                recentGains.clear();
            }
            return;
        }

        String windowText = section.getString("window", "1m");
        Duration window = DurationParser.parse(windowText);
        if (window == null || window.toSeconds() < 1) {
            plugin.getLogger().warning("Invalid rate-limiter window '" + windowText + "' in config.yml, using 1m");
            window = Duration.ofMinutes(1);
        }

        EnumSet<Source> limited = EnumSet.noneOf(Source.class);
        for (Source source : Source.values()) {
            if (section.getBoolean("sources." + source.getConfigKey(), true)) {
                limited.add(source);
            }
        }

        settings = new Settings(true, window.toMillis(),
                Math.max(0, section.getInt("free-repeats", 10)),
                Math.max(0, section.getDouble("reduction-per-repeat", 0.1)),
                Math.clamp(section.getDouble("minimum-multiplier", 0.1), 0, 1),
                limited);
    }

    /***
     * Records an exp gain from the source and returns the multiplier to apply to its exp.
     * Call this once per action, not once per level system, so one action only counts as one repeat.
     * @param key what was done, e.g. the mob type or ability name. Repeats are counted per key
     * @return 1 for full exp, lower when the same key has been repeated too often recently
     */
    public synchronized double recordGain(Player p, Source source, String key) {
        Settings s = settings;
        if (!s.enabled() || !s.limitedSources().contains(source)) {
            return 1;
        }

        long now = System.currentTimeMillis();
        ArrayDeque<Long> times = recentGains
                .computeIfAbsent(p.getUniqueId(), uuid -> new HashMap<>())
                .computeIfAbsent(source.getConfigKey() + ":" + key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > s.windowMillis()) {
            times.pollFirst();
        }

        int repeats = times.size();
        double multiplier = 1;
        if (repeats >= s.freeRepeats()) {
            multiplier = Math.max(s.minimumMultiplier(), 1 - (repeats - s.freeRepeats() + 1) * s.reductionPerRepeat());
        }

        times.addLast(now);
        // Once the multiplier is at its minimum, older entries don't change anything, so cap the memory used
        int maxTracked = s.freeRepeats() + (s.reductionPerRepeat() > 0
                ? (int) Math.ceil((1 - s.minimumMultiplier()) / s.reductionPerRepeat()) : 0) + 1;
        while (times.size() > maxTracked) {
            times.pollFirst();
        }
        return multiplier;
    }

    /***
     * Forgets a player's recent gains, called when they log off
     */
    public synchronized void clearPlayer(UUID uuid) {
        recentGains.remove(uuid);
    }
}
