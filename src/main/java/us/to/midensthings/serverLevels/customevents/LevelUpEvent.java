package us.to.midensthings.serverLevels.customevents;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

public class LevelUpEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private LeveledPlayer leveledPlayer;

    public LevelUpEvent(LeveledPlayer leveledPlayer) {
        this.leveledPlayer = leveledPlayer;
    }


    public LeveledPlayer getLeveledPlayer() {
        return leveledPlayer;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}
