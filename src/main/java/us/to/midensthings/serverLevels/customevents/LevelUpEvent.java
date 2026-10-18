package us.to.midensthings.serverLevels.customevents;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

public class LevelUpEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private LeveledPlayer leveledPlayer;
    private int newLevel;

    public LevelUpEvent(LeveledPlayer leveledPlayer) {
        this(leveledPlayer, leveledPlayer.getLevel());
    }

    public LevelUpEvent(LeveledPlayer leveledPlayer, int newLevel) {
        this.leveledPlayer = leveledPlayer;
        this.newLevel = newLevel;
    }


    public LeveledPlayer getLeveledPlayer() {
        return leveledPlayer;
    }

    /***
     * @return the level the player reached with this level up. When a player gains several levels at once,
     * one event fires per level, and the LeveledPlayer will already be at the final level.
     */
    public int getNewLevel() {
        return newLevel;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}
