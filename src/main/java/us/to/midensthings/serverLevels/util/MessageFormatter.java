package us.to.midensthings.serverLevels.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import org.bukkit.OfflinePlayer;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.compat.PlaceholderAPICompat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Turns configurable messages into components, filling in PlaceholderAPI placeholders when it's installed
 */
public final class MessageFormatter {
    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    // Messages that already failed to parse, so a broken message only logs once instead of on every exp gain
    private static final Set<String> warnedMessages = ConcurrentHashMap.newKeySet();

    private MessageFormatter() {}

    /***
     * Fills in PlaceholderAPI placeholders and parses the message with MiniMessage.
     * If the message can't be parsed, it's shown as plain text and a warning is logged.
     * @param p the player to fill placeholders in for, or null for placeholders that don't need a player
     */
    public static Component format(OfflinePlayer p, String message) {
        if (plugin.enabledCompats.contains("PlaceholderAPI")) {
            message = new PlaceholderAPICompat().parsePlaceholdersForMiniMessage(p, message);
        }
        try {
            return MiniMessage.miniMessage().deserialize(message);
        } catch (ParsingException e) {
            if (warnedMessages.add(message)) {
                plugin.getLogger().warning("Could not format message \"" + message + "\": " + e.getMessage());
            }
            return Component.text(message);
        }
    }

    /***
     * Fills in PlaceholderAPI placeholders in text that isn't parsed with MiniMessage, like commands
     * @param p the player to fill placeholders in for, or null for placeholders that don't need a player
     */
    public static String formatPlain(OfflinePlayer p, String text) {
        if (plugin.enabledCompats.contains("PlaceholderAPI")) {
            return new PlaceholderAPICompat().parsePlaceholders(p, text);
        }
        return text;
    }
}
