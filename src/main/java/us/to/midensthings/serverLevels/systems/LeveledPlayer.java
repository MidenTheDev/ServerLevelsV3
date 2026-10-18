package us.to.midensthings.serverLevels.systems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.customevents.LevelDownEvent;
import us.to.midensthings.serverLevels.customevents.LevelUpEvent;
import us.to.midensthings.serverLevels.util.NamespacedKeys;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.UUID;

/***
 Represents the data of a player from a specified level system
 */
public class LeveledPlayer {
    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    // The level system the player data belongs to
    String system;
    // the player's UUID
    UUID uuid;
    // player's current exp
    double exp;

    double expMultiplier;
    // players current level
    int level;



    public LeveledPlayer(String system, UUID uuid, double exp, int level, double expMultiplier) {
        this.system = system;
        this.uuid = uuid;
        this.exp = exp;
        this.level = level;
        this.expMultiplier = expMultiplier;
    }

    public LeveledPlayer(String system, String uuid, double exp, int level, double expMultiplier) {
        this.system = system;
        this.uuid = UUID.fromString(uuid);
        this.exp = exp;
        this.level = level;
        this.expMultiplier = expMultiplier;
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

    public static LeveledPlayer fromPlayer(Player p, String levelSYstem) {
        return plugin.getDatabaseManager().getLeveledPlayer(p,levelSYstem);
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
        this.getPlayer().getPersistentDataContainer().set(NamespacedKeys.generatePlayerExpMultiplierKey(this.getSystemName()), PersistentDataType.DOUBLE,expMultiplier);
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
        double expGained = (expToAdd)*(ls.getExpGainMultiplier()+this.expMultiplier);
        this.exp += expGained;
        // Check if exp is under zero, and if it is, set it to zero
        if (exp <= 0) {
            exp = 0;
        }
        // Show player the exp gain title
        Player player = getPlayer();
        if (ls.showExpGain() && player != null) {
            DecimalFormat df = new DecimalFormat("##.##");
            df.setRoundingMode(RoundingMode.DOWN);
            // Show the exp actually gained after multipliers
            Component expGainMsg = MiniMessage.miniMessage().deserialize(ls.getExpGainMsg().replace("%exp%",df.format(expGained)).replace("%system%",system));

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
        adjustLevel();
    }

    /***
     * Saves the current player data to the plugin's database
     */
    public void save() {
        plugin.getDatabaseManager().saveLeveledPlayer(this);
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

    }
}
