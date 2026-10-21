package us.to.midensthings.serverLevels.data;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.util.MessageFormatter;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.util.DurationParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/***
 * Temporary exp boosters for level systems. Each system has a queue of boosters, and only the first one is active.
 * When it runs out, the next one starts right where it ended.
 * <p>
 * Boosters only count down while the server is running. Their remaining time is saved to boosters.yml,
 * so a restart or reload picks up where they left off.
 */
public class BoosterManager {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    private final File boostersYml = new File(plugin.getDataFolder(), "boosters.yml");

    // How often expired boosters are moved on, in ticks
    private static final long TICK_INTERVAL = 20;
    // How often the remaining time is saved, in ticks. Limits how much countdown is lost if the server crashes
    private static final long SAVE_INTERVAL = 60 * 20;

    private static final class QueuedBooster {
        final double multiplier;
        // Time left when this booster started (or when the server started, for the active one), in nanoseconds
        long remainingNanos;
        // When this booster became active, from System.nanoTime(). Only set for the first booster in the queue
        long activeSince;

        QueuedBooster(double multiplier, long remainingNanos) {
            this.multiplier = multiplier;
            this.remainingNanos = remainingNanos;
        }

        long remainingAt(long now) {
            return remainingNanos - (now - activeSince);
        }
    }

    /***
     * A booster in a system's queue.
     * @param multiplier the exp multiplier
     * @param remaining how long the booster has left, or its full duration if it hasn't started yet
     * @param active whether this is the booster currently running
     */
    public record BoosterInfo(double multiplier, Duration remaining, boolean active) {}

    /***
     * The result of adding a booster.
     * @param startsIn how long until the booster starts, zero if it started right away
     * @param queuePosition how many boosters are ahead of it, 0 if it started right away
     */
    public record AddedBooster(Duration startsIn, int queuePosition) {}

    // Booster queues by level system name. Every access is synchronized on this manager
    private final Map<String, Deque<QueuedBooster>> boosters = new HashMap<>();
    private BukkitTask tickTask;

    // Orders async writes of boosters.yml, so an older save can't finish after a newer one
    private final Object saveLock = new Object();
    private final AtomicLong saveCounter = new AtomicLong();
    private long lastWrittenSave = 0;

    /***
     * Loads saved boosters and starts counting down. Must be called on the main thread.
     */
    public void start() {
        load();
        long[] ticksSinceSave = {0};
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            advanceAll();
            ticksSinceSave[0] += TICK_INTERVAL;
            if (ticksSinceSave[0] >= SAVE_INTERVAL) {
                ticksSinceSave[0] = 0;
                save(true);
            }
        }, TICK_INTERVAL, TICK_INTERVAL);
    }

    /***
     * Stops counting down and saves the remaining time right away. Called when the plugin disables.
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        save(false);
    }

    /***
     * Adds a booster to the system. It starts right away if no booster is active, otherwise after every queued booster ends.
     */
    public synchronized AddedBooster addBooster(String system, double multiplier, Duration duration) {
        long now = System.nanoTime();
        // Clear out finished boosters first. This can remove the system's queue, so only get the queue afterwards
        Deque<QueuedBooster> existing = boosters.get(system);
        if (existing != null) {
            advance(system, existing, now);
        }
        Deque<QueuedBooster> queue = boosters.computeIfAbsent(system, s -> new ArrayDeque<>());

        // Time until every booster already queued has finished
        long startsInNanos = 0;
        boolean first = true;
        for (QueuedBooster queued : queue) {
            startsInNanos += first ? queued.remainingAt(now) : queued.remainingNanos;
            first = false;
        }
        int queuePosition = queue.size();

        QueuedBooster booster = new QueuedBooster(multiplier, duration.toNanos());
        boolean startsNow = queue.isEmpty();
        if (startsNow) {
            booster.activeSince = now;
        }
        queue.addLast(booster);
        if (startsNow) {
            announceStart(system, booster);
        }

        save(true);
        return new AddedBooster(Duration.ofNanos(startsInNanos), queuePosition);
    }

    /***
     * @return the active booster's multiplier for the system, or 1 if no booster is active
     */
    public synchronized double getMultiplier(String system) {
        Deque<QueuedBooster> queue = boosters.get(system);
        if (queue == null) {
            return 1;
        }
        advance(system, queue, System.nanoTime());
        QueuedBooster active = queue.peekFirst();
        return active == null ? 1 : active.multiplier;
    }

    /***
     * @return how long the system's active booster has left, or zero if no booster is active
     */
    public synchronized Duration getActiveRemaining(String system) {
        Deque<QueuedBooster> queue = boosters.get(system);
        if (queue == null) {
            return Duration.ZERO;
        }
        long now = System.nanoTime();
        advance(system, queue, now);
        QueuedBooster active = queue.peekFirst();
        return active == null ? Duration.ZERO : Duration.ofNanos(active.remainingAt(now));
    }

    /***
     * @return the system's boosters in the order they run, the active one first. Empty if there are none
     */
    public synchronized List<BoosterInfo> getBoosters(String system) {
        Deque<QueuedBooster> queue = boosters.get(system);
        if (queue == null) {
            return List.of();
        }
        long now = System.nanoTime();
        advance(system, queue, now);

        List<BoosterInfo> infos = new ArrayList<>();
        boolean first = true;
        for (QueuedBooster booster : queue) {
            long remaining = first ? booster.remainingAt(now) : booster.remainingNanos;
            infos.add(new BoosterInfo(booster.multiplier, Duration.ofNanos(remaining), first));
            first = false;
        }
        return infos;
    }

    /***
     * Cancels one of the system's boosters. Cancelling the active booster starts the next one right away.
     * @param index the booster's position in the queue, 0 being the active booster
     * @return the cancelled booster, or null if there's no booster at that position
     */
    public synchronized BoosterInfo cancelBooster(String system, int index) {
        List<BoosterInfo> infos = getBoosters(system);
        if (index < 0 || index >= infos.size()) {
            return null;
        }
        Deque<QueuedBooster> queue = boosters.get(system);
        long now = System.nanoTime();

        if (index == 0) {
            QueuedBooster cancelled = queue.pollFirst();
            announceEnd(system, cancelled);
            QueuedBooster next = queue.peekFirst();
            if (next != null) {
                next.activeSince = now;
                announceStart(system, next);
            }
        } else {
            // ArrayDeque has no remove by index, so go through it with an iterator
            Iterator<QueuedBooster> iterator = queue.iterator();
            for (int i = 0; i <= index; i++) {
                iterator.next();
            }
            iterator.remove();
        }
        if (queue.isEmpty()) {
            boosters.remove(system);
        }

        save(true);
        return infos.get(index);
    }

    /***
     * Cancels every booster for the system, active and queued
     * @return how many boosters were cancelled
     */
    public synchronized int cancelAll(String system) {
        Deque<QueuedBooster> queue = boosters.get(system);
        if (queue == null) {
            return 0;
        }
        advance(system, queue, System.nanoTime());
        int cancelled = queue.size();
        QueuedBooster active = queue.peekFirst();
        boosters.remove(system);
        if (active != null) {
            announceEnd(system, active);
        }

        save(true);
        return cancelled;
    }

    private synchronized void advanceAll() {
        long now = System.nanoTime();
        // Copy the keys, advance removes systems whose queue is empty
        for (String system : new ArrayList<>(boosters.keySet())) {
            advance(system, boosters.get(system), now);
        }
    }

    // Removes boosters that have run out, starting the next one exactly when the previous one ended
    private void advance(String system, Deque<QueuedBooster> queue, long now) {
        QueuedBooster active = queue.peekFirst();
        while (active != null && active.remainingAt(now) <= 0) {
            long endedAt = active.activeSince + active.remainingNanos;
            queue.pollFirst();
            announceEnd(system, active);

            active = queue.peekFirst();
            if (active != null) {
                active.activeSince = endedAt;
                announceStart(system, active);
            }
        }
        if (queue.isEmpty()) {
            boosters.remove(system);
        }
    }

    private void announceStart(String system, QueuedBooster booster) {
        Duration duration = Duration.ofNanos(booster.remainingNanos);
        plugin.getLogger().info("Started a " + formatMultiplier(booster.multiplier) + "x exp booster for " + system
                + " lasting " + DurationParser.format(duration));
        broadcast(system, booster.multiplier, duration, true);
    }

    private void announceEnd(String system, QueuedBooster booster) {
        plugin.getLogger().info("The " + formatMultiplier(booster.multiplier) + "x exp booster for " + system + " has ended");
        broadcast(system, booster.multiplier, Duration.ofNanos(booster.remainingNanos), false);
    }

    // Broadcasts the system's booster start or end message to everyone, if the system has broadcast-boosters on
    private void broadcast(String system, double multiplier, Duration duration, boolean started) {
        LevelSystemRegistry registry = plugin.getLevelSystemRegistry();
        LevelSystem levelSystem = registry == null ? null : registry.getLevelSystem(system);
        if (levelSystem == null || !levelSystem.broadcastsBoosters()) {
            return;
        }

        String template = started ? levelSystem.getBoosterStartMsg() : levelSystem.getBoosterEndMsg();
        // Broadcasts aren't for one player, so only placeholders that don't need a player are filled in
        Component message = MessageFormatter.format(null, template
                .replace("%multiplier%", formatMultiplier(multiplier))
                .replace("%system%", system)
                .replace("%duration%", DurationParser.format(duration)));

        if (Bukkit.isPrimaryThread()) {
            Bukkit.broadcast(message);
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(message));
        }
    }

    // e.g. 2 instead of 2.0
    private static String formatMultiplier(double multiplier) {
        return new DecimalFormat("0.##").format(multiplier);
    }

    private synchronized void load() {
        boosters.clear();
        if (!boostersYml.exists()) {
            return;
        }

        YamlConfiguration conf = YamlConfiguration.loadConfiguration(boostersYml);
        // The active booster resumes counting from now, so time the server was offline doesn't count
        long now = System.nanoTime();
        for (String system : conf.getKeys(false)) {
            Deque<QueuedBooster> queue = new ArrayDeque<>();
            for (Map<?, ?> entry : conf.getMapList(system)) {
                Object multiplier = entry.get("multiplier");
                Duration remaining = DurationParser.parse(String.valueOf(entry.get("remaining")));
                if (!(multiplier instanceof Number number) || remaining == null || remaining.isZero()) {
                    plugin.getLogger().warning("Skipping an invalid booster for " + system + " in boosters.yml: " + entry);
                    continue;
                }
                queue.addLast(new QueuedBooster(number.doubleValue(), remaining.toNanos()));
            }
            if (!queue.isEmpty()) {
                queue.peekFirst().activeSince = now;
                boosters.put(system, queue);
            }
        }
    }

    /***
     * Saves every booster's remaining time to boosters.yml
     * @param async whether to write the file off the main thread
     */
    private void save(boolean async) {
        String yaml;
        synchronized (this) {
            long now = System.nanoTime();
            YamlConfiguration conf = new YamlConfiguration();
            conf.options().setHeader(List.of(
                    "Active and queued exp boosters for each level system, in the order they run.",
                    "Boosters only count down while the server is running. Use /sl booster <system> add <multiplier> <duration> to add more."));
            boosters.forEach((system, queue) -> {
                List<Map<String, Object>> entries = new ArrayList<>();
                boolean first = true;
                for (QueuedBooster booster : queue) {
                    long remainingNanos = first ? booster.remainingAt(now) : booster.remainingNanos;
                    first = false;
                    // Round up to whole seconds, so a booster with less than a second left isn't saved as finished
                    Duration remaining = Duration.ofSeconds((long) Math.ceil(remainingNanos / 1_000_000_000.0));
                    if (remaining.isZero() || remaining.isNegative()) {
                        continue;
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("multiplier", booster.multiplier);
                    entry.put("remaining", DurationParser.format(remaining));
                    entries.add(entry);
                }
                if (!entries.isEmpty()) {
                    conf.set(system, entries);
                }
            });
            yaml = conf.saveToString();
        }

        long saveId = saveCounter.incrementAndGet();
        Runnable write = () -> {
            synchronized (saveLock) {
                // A newer save already wrote the file, don't overwrite it with older contents
                if (saveId < lastWrittenSave) {
                    return;
                }
                try {
                    Files.writeString(boostersYml.toPath(), yaml);
                    lastWrittenSave = saveId;
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save boosters.yml: " + e.getMessage());
                }
            }
        };

        // Async tasks can't be scheduled while the plugin is shutting down
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }
}
