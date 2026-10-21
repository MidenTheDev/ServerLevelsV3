package us.to.midensthings.serverLevels.compat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.Map;

public class PlaceholderAPICompat {

    // Legacy formatting codes and the MiniMessage tags they become
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"), Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"), Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"), Map.entry('r', "<reset>"));

    /***
     * Fills in PlaceholderAPI placeholders, for text that isn't parsed with MiniMessage (like commands)
     * @param p the player to fill placeholders in for, or null for placeholders that don't need a player
     */
    public String parsePlaceholders(OfflinePlayer p, String string) {
        return PlaceholderAPI.setPlaceholders(p, string);
    }

    /***
     * Fills in PlaceholderAPI placeholders in a MiniMessage string. Many placeholders return legacy color codes (§6),
     * which MiniMessage refuses to parse, so those are turned into MiniMessage tags.
     * @param p the player to fill placeholders in for, or null for placeholders that don't need a player
     */
    public String parsePlaceholdersForMiniMessage(OfflinePlayer p, String string) {
        return legacyToMiniMessage(PlaceholderAPI.setPlaceholders(p, string));
    }

    // Turns § codes into MiniMessage tags, including hex colors in the §x§R§R§G§G§B§B format
    static String legacyToMiniMessage(String input) {
        if (input.indexOf('§') == -1) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != '§' || i + 1 >= input.length()) {
                out.append(c);
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));
            if (code == 'x' && isHexColor(input, i)) {
                out.append("<#");
                for (int digit = 0; digit < 6; digit++) {
                    out.append(input.charAt(i + 3 + digit * 2));
                }
                out.append('>');
                i += 13;
                continue;
            }
            String tag = LEGACY_TAGS.get(code);
            if (tag != null) {
                out.append(tag);
            }
            // Unknown codes are dropped, same as Minecraft does
            i++;
        }
        return out.toString();
    }

    // Checks for §x followed by six §<hex digit> pairs, starting at index
    private static boolean isHexColor(String input, int index) {
        if (index + 13 >= input.length()) {
            return false;
        }
        for (int digit = 0; digit < 6; digit++) {
            int pos = index + 2 + digit * 2;
            if (input.charAt(pos) != '§' || Character.digit(input.charAt(pos + 1), 16) == -1) {
                return false;
            }
        }
        return true;
    }
}
