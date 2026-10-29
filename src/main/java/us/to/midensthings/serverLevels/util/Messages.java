package us.to.midensthings.serverLevels.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import us.to.midensthings.serverLevels.ServerLevels;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/***
 * The messages in messages.yml, sent to players and command senders. Messages use MiniMessage and %name% placeholders.
 * Any message missing from the file uses the default from the plugin jar, so new messages work without editing the file.
 */
public final class Messages {
    private static volatile YamlConfiguration messages = new YamlConfiguration();
    private static volatile YamlConfiguration defaults = new YamlConfiguration();

    private Messages() {}

    /***
     * Reads messages.yml, creating it first if it doesn't exist
     */
    public static void load(ServerLevels plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration newDefaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                newDefaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the default messages: " + e.getMessage());
        }
        defaults = newDefaults;
        messages = YamlConfiguration.loadConfiguration(file);
    }

    /***
     * @return the raw message text, or the default if messages.yml doesn't have it
     */
    public static String getRaw(String key) {
        String message = messages.getString(key);
        return message != null ? message : defaults.getString(key, key);
    }

    /***
     * @return a number setting from messages.yml, like the length of the progress bar
     */
    public static int getInt(String key, int fallback) {
        return messages.contains(key) ? messages.getInt(key) : defaults.getInt(key, fallback);
    }

    /***
     * Builds a message with its placeholders filled in
     * @param placeholders pairs of a placeholder name and its value, e.g. "player", "Steve". Text values are shown as typed
     *                     (any MiniMessage tags in them are not applied), Component values are inserted as they are
     * @return the message, or null if it's set to '' (turned off)
     */
    public static Component get(String key, Object... placeholders) {
        String template = getRaw(key);
        if (template.isEmpty()) {
            return null;
        }
        List<TagResolver> resolvers = new ArrayList<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];
            if (value instanceof Component component) {
                String tag = "sl_" + name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                template = template.replace("%" + name + "%", "<" + tag + ">");
                resolvers.add(Placeholder.component(tag, component));
            } else {
                // Escaped, so a value can't add formatting or click actions
                template = template.replace("%" + name + "%", MiniMessage.miniMessage().escapeTags(String.valueOf(value)));
            }
        }
        try {
            return MiniMessage.miniMessage().deserialize(template, TagResolver.resolver(resolvers));
        } catch (ParsingException e) {
            return Component.text(template);
        }
    }

    /***
     * Sends a message, unless it's set to '' in messages.yml. See get for the placeholders
     */
    public static void send(Audience to, String key, Object... placeholders) {
        Component message = get(key, placeholders);
        if (message != null) {
            to.sendMessage(message);
        }
    }
}
