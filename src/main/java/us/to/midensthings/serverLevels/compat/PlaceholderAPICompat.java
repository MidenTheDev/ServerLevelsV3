package us.to.midensthings.serverLevels.compat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public class PlaceholderAPICompat {

    public String parsePlaceholders(Player p, String string) {
        return PlaceholderAPI.setPlaceholders(p,string);
    }
}
