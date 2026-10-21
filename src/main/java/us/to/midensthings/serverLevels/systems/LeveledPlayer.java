package us.to.midensthings.serverLevels.systems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.compat.PlaceholderAPICompat;
import us.to.midensthings.serverLevels.customevents.LevelDownEvent;
import us.to.midensthings.serverLevels.customevents.LevelUpEvent;
import us.to.midensthings.serverLevels.util.NamespacedKeys;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/***
 Represents the data of a player from a specified level system
 */
public class LeveledPlayer {
    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    // Formats exp for the exp gain message. Reused instead of created on every gain, one per thread since DecimalFormat isn't thread safe
    private static final ThreadLocal<DecimalFormat> EXP_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.DOWN);
        return df;
    });

    // The level system the player data belongs to
    String system;
    // the player's UUID
    UUID uuid;
    // player's current exp
    double exp;

    double expMultiplier;
    // players current level
    int level;

    // Whether the player's exp gains from gameplay are paused. Stored in the database
    private boolean paused;

    // Whether exp, level or paused changed since the last save, so autosave can skip players with nothing new
    private volatile boolean dirty = false;



    public LeveledPlayer(String system, UUID uuid, double exp, int level, double expMultiplier) {
        this(system, uuid, exp, level, expMultiplier, false);
    }

    public LeveledPlayer(String system, String uuid, double exp, int level, double expMultiplier) {
        this(system, UUID.fromString(uuid), exp, level, expMultiplier, false);
    }

    public LeveledPlayer(String system, String uuid, double exp, int level, double expMultiplier, boolean paused) {
        this(system, UUID.fromString(uuid), exp, level, expMultiplier, paused);
    }

    public LeveledPlayer(String system, UUID uuid, double exp, int level, double expMultiplier, boolean paused) {
        this.system = system;
        this.uuid = uuid;
        this.exp = exp;
        this.level = level;
        this.expMultiplier = expMultiplier;
        this.paused = paused;
    }

    /***
     * @return whether the player's exp gains from gameplay are paused in this system
     */
    public boolean isPaused() {
        return paused;
    }

    /***
     * Pauses or unpauses the player's exp gains from gameplay in this system. While paused, gainExp does nothing,
     * but incrementExp and the other setters still work, so admins can still change the player's exp and level.
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
        dirty = true;
    }

    /***
     * Gives the player exp earned through gameplay, like mining or killing mobs. Does nothing while the player is paused.
     * Use incrementExp to change exp regardless of pauses.
     * @return whether the exp was given
     */
    public boolean gainExp(double expToAdd) {
        if (paused) {
            return false;
        }
        incrementExp(expToAdd);
        return true;
    }

    /***
     * Gets the player's data in the level system. Online players come from the cache, offline players are loaded from the database.
     * For offline players this waits for the database, so avoid calling it on the main thread for them. Use fromUuidAsync instead.
     * @return the player's data, or null if they aren't in the level system or the system doesn't exist
     */
    public static LeveledPlayer fromUuid(UUID uuid, String systemName) {
        if (plugin.getLevelSystemRegistry().getLevelSystem(systemName) == null) {
            // System doesn't exist
            return null;
        }
        LeveledPlayer lp = plugin.getLevelSystemRegistry().getLevelSystem(systemName).getPlayerCache().getLeveledPlayer(uuid);
        if (lp != null || Bukkit.getPlayer(uuid) != null) {
            return lp;   // online and not cached → not in this system
        }
        return plugin.getDatabaseManager().getLeveledPlayer(uuid, systemName);  // offline lookup (API use)
    }

    /***
     * Gets the player's data in the level system without blocking. Online players come from the cache and complete right away,
     * offline players are loaded from the database in the background.
     * <p>
     * The future may complete on the database thread. Use the Bukkit scheduler to get back to the main thread before using
     * most of the Bukkit API. Offline players' data isn't cached, so call save() after changing it.
     * @return a future with the player's data, or null if they aren't in the level system or the system doesn't exist
     */
    public static CompletableFuture<LeveledPlayer> fromUuidAsync(UUID uuid, String systemName) {
        LevelSystem system = plugin.getLevelSystemRegistry().getLevelSystem(systemName);
        if (system == null) {
            // System doesn't exist
            return CompletableFuture.completedFuture(null);
        }
        LeveledPlayer lp = system.getPlayerCache().getLeveledPlayer(uuid);
        if (lp != null || Bukkit.getPlayer(uuid) != null) {
            return CompletableFuture.completedFuture(lp);   // online and not cached → not in this system
        }
        return plugin.getDatabaseManager().getLeveledPlayerAsync(uuid, systemName);
    }


    public String getSystemName() {
        return system;
    }

    public LevelSystem getSystem() {
        return plugin.getLevelSystemRegistry().getLevelSystem(system);
    }

    public UUID getUuid() {
        return uuid;
    }

    public static LeveledPlayer fromPlayer(Player p, String systemName) {
        return fromUuid(p.getUniqueId(),systemName);
    }

    public int getLevel() {
        return level;
    }

    /***
     * Sets the leveled player's level, kept between 0 and the system's max level.
     * Does NOT adjust exp. Use LeveledPlayer.adjustExp()
     * @param level
     */
    public void setLevel(int level) {
        this.level = Math.clamp(level, 0, getSystem().getMaxLevel());
        dirty = true;
    }

    public double getExp() {
        return exp;
    }

    /***
     * Sets the leveled player's exp to the specified value.
     * Does NOT trigger a level up. Use LeveledPlayer.adjustLevel()
     * @param exp
     */
    public void setExp(double exp) {
        this.exp = exp;
        dirty = true;
    }

    /***
     * Gets the player's exp gain multiplier. This number is added to the level system's
     * multipler and then the resulting number is multiplied with any exp the player is gaining via the incrementExp method.
     * @return the multiplier
     */
    public double getExpMultiplier() {
        return expMultiplier;
    }

    /***
     * Sets the player's exp gain multiplier. This number is added to the level system's
     * multipler and then the resulting number is multiplied with any exp the player is gaining via the incrementExp method.
     * @return the multiplier
     */
    public void setExpMultiplier(double expMultiplier) {
        this.expMultiplier = expMultiplier;
        Player player = getPlayer();
        if (player == null) {
            // Can only store the multiplier on online players
            return;
        }
        player.getPersistentDataContainer().set(NamespacedKeys.generatePlayerExpMultiplierKey(this.getSystemName()), PersistentDataType.DOUBLE,expMultiplier);
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    /***
     * Increases the player's exp by the given value.
     * Triggers level ups. Does NOT save to database.
     * @param expToAdd
     */
    public void incrementExp(double expToAdd) {
        LevelSystem ls = getSystem();
        // An active booster multiplies the total, so a 2x booster doubles whatever the player would normally get
        double expGained = (expToAdd)*(ls.getExpGainMultiplier()+this.expMultiplier)*plugin.getBoosterManager().getMultiplier(system);
        this.exp += expGained;
        dirty = true;
        // Check if exp is under zero, and if it is, set it to zero
        if (exp <= 0) {
            exp = 0;
        }
        // Show player the exp gain title
        Player player = getPlayer();
        if (ls.showExpGain() && player != null) {
            // Show the exp actually gained after multipliers
            String expMsg = ls.getExpGainMsg().replace("%exp%",EXP_FORMAT.get().format(expGained)).replace("%system%",system);
            if (plugin.enabledCompats.contains("PlaceholderAPI")) {
                PlaceholderAPICompat papiComp = new PlaceholderAPICompat();
                expMsg = papiComp.parsePlaceholders(player,expMsg);
            }
            Component expGainMsg = MiniMessage.miniMessage().deserialize(expMsg);

            player.sendActionBar(expGainMsg);

        }
        adjustLevel();
    }

    /***
     * Decreases the player's exp by the given value, without exp multipliers.
     * Exp will not go below zero. Triggers level downs. Does NOT save to database.
     * @param expToRemove
     */
    public void decrementExp(double expToRemove) {
        this.exp = Math.max(0, this.exp - expToRemove);
        dirty = true;
        adjustLevel();
    }

    /***
     * Saves the current player data to the plugin's database.
     * Cached players are autosaved, and saved when they log off, so this is only needed to save right away.
     */
    public void save() {
        // Clear before saving, so a change made while the save is being queued marks the player dirty again
        dirty = false;
        plugin.getDatabaseManager().saveLeveledPlayer(this);
    }

    /***
     * Saves the player data to the database only if it changed since the last save.
     * @return true if the player was saved
     */
    public boolean saveIfDirty() {
        if (!dirty) {
            return false;
        }
        save();
        return true;
    }

    /***
     * @return whether the player's exp or level changed since they were last saved
     */
    public boolean isDirty() {
        return dirty;
    }

    /***
     * Adjusts this player's level according to their exp value.
     * Does nothing if the player is already the appropriate level
     */
    public void adjustLevel() {
        LevelSystem ls = getSystem();
        // Negative exp would otherwise make the level down loop below run forever
        if (exp < 0) {
            exp = 0;
        }
        // check if level was DECREASED
        double expForCurrentLevel = ls.getExpRequirement(level-1);
        if (exp < expForCurrentLevel) {

            // While the player has less exp than was required for their current level.
            while (level > 0 && exp < expForCurrentLevel) {
                // decrease level
                level--;
                dirty = true;
                LevelDownEvent levelDownEvent = new LevelDownEvent(this, level);
                runOnMainThread(levelDownEvent::callEvent);

                // update exp requirement to reflect new level
                expForCurrentLevel = ls.getExpRequirement(level-1);
            }
        } else {
            // Player must have more exp than required for their current level, check if they have enough to level up
            double expToNextLevel = ls.getExpRequirement(level);

            // While the player has more or equal exp than is required for the next level, up to the max level
            while (level < ls.getMaxLevel() && exp >= expToNextLevel) {
                // increase level
                level++;
                dirty = true;
                int newLevel = level;
                ls.sendLevelupMessage(getPlayer(),newLevel);
                LevelUpEvent levelUpEvent = new LevelUpEvent(this, newLevel);
                runOnMainThread(levelUpEvent::callEvent);
                // Milestones dispatch commands, which must happen on the main thread
                runOnMainThread(() -> ls.runMilestone(newLevel, getPlayer()));

                // update the exp requirement to reflect new level
                expToNextLevel = ls.getExpRequirement(level);
            }
        }



    }

    // Runs immediately when already on the main thread, otherwise schedules it for the next tick
    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }


    /***
     * Adjusts this player's exp according to their current level.
     * This will always reset their exp to the minimum required for their current level.
     */
    public void adjustExp() {
        LevelSystem ls = getSystem();

        double minExpForCurrentLevel = ls.getExpRequirement(level-1);
        exp = minExpForCurrentLevel;
        dirty = true;

    }
}
