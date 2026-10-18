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
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

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


    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
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
        this.exp += (expToAdd)*(ls.getExpGainMultiplier()+this.expMultiplier);
        // Show player the exp gain title

        if (ls.showExpGain()) {
            DecimalFormat df = new DecimalFormat("##.##");
            df.setRoundingMode(RoundingMode.DOWN);
            Player player = Bukkit.getPlayer(uuid);
            Component expGainMsg = MiniMessage.miniMessage().deserialize(ls.getExpGainMsg().replace("%exp%",df.format(expToAdd)).replace("%system%",system));

            player.sendActionBar(expGainMsg);

        }
        adjustLevel();
    }

    /***
     * Saves the current player data to the plugin's database
     */
    public void saveToDatabase() {
        plugin.getDatabaseManager().saveLeveledPlayer(this);
    }

    /***
     * Adjusts this player's level according to their exp value.
     * Does nothing if the player is already the appropriate level
     */
    public void adjustLevel() {
        LevelSystem ls = getSystem();

        // check if level was DECREASED
        double expForCurrentLevel = ls.getExpRequirement(level-1);
        if (exp < expForCurrentLevel) {

            // While the player has less exp than was required for their current level.
            while (exp < expForCurrentLevel) {
                // decrease level
                level--;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    LevelDownEvent levelDownEvent = new LevelDownEvent(this);
                    levelDownEvent.callEvent();
                });


                // update exp requirement to reflect new level
                expForCurrentLevel = ls.getExpRequirement(level-1);
            }
        } else {
            // Player must have more exp than required for their current level, check if they have enough to level up
            double expToNextLevel = ls.getExpRequirement(level);

            // While the player has more or equal exp than is required for the next level
            while (exp >= expToNextLevel) {
                // increase level
                level++;
                ls.sendLevelupMessage(Bukkit.getPlayer(uuid),level);
                // Call event on main thread
                Bukkit.getScheduler().runTask(plugin,() -> {
                    LevelUpEvent levelUpEvent = new LevelUpEvent(this);
                    levelUpEvent.callEvent();
                });
                ls.runMilestone(level, Bukkit.getPlayer(uuid));

                // update the exp requirement to reflect new level
                expToNextLevel = ls.getExpRequirement(level);
            }
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
