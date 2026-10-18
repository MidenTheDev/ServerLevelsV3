package us.to.midensthings.serverLevels.customevents;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

public class LevelDownEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private LeveledPlayer leveledPlayer;
    private int newLevel;

    public LevelDownEvent(LeveledPlayer leveledPlayer) {
        this(leveledPlayer, leveledPlayer.getLevel());
    }

    public LevelDownEvent(LeveledPlayer leveledPlayer, int newLevel) {
        this.leveledPlayer = leveledPlayer;
        this.newLevel = newLevel;
    }


    public LeveledPlayer getLeveledPlayer() {
        return leveledPlayer;
    }

    /***
     * @return the level the player dropped to with this level down. When a player loses several levels at once,
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
