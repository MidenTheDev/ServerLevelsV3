package us.to.midensthings.serverLevels.customevents;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

/***
 * Called after a player gains exp in a level system, once multipliers and boosters have been applied.
 * Always called on the main thread. Any level ups from the gain have already happened by the time this is called.
 */
public class ExpGainEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final LeveledPlayer leveledPlayer;
    private final double expGained;
    private final double baseExp;

    public ExpGainEvent(LeveledPlayer leveledPlayer, double expGained, double baseExp) {
        this.leveledPlayer = leveledPlayer;
        this.expGained = expGained;
        this.baseExp = baseExp;
    }

    public LeveledPlayer getLeveledPlayer() {
        return leveledPlayer;
    }

    /***
     * @return the exp the player actually gained, after the system's, the player's and any booster's multipliers
     */
    public double getExpGained() {
        return expGained;
    }

    /***
     * @return the exp before any multipliers were applied
     */
    public double getBaseExp() {
        return baseExp;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}
