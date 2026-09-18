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

    public LevelDownEvent(LeveledPlayer leveledPlayer) {
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
