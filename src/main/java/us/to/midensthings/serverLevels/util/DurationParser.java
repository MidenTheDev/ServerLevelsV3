package us.to.midensthings.serverLevels.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * Parses durations written like 1d5h4m2s, 30m or 45s.
 */
public final class DurationParser {
    // The whole string must be one or more number + unit pairs
    private static final Pattern FULL_FORMAT = Pattern.compile("(\\d+[dhms])+");
    private static final Pattern PART = Pattern.compile("(\\d+)([dhms])");

    private DurationParser() {}

    /***
     * @param input a duration made of number + unit pairs, where the units are d, h, m and s. Case and spaces are ignored
     * @return the parsed duration, or null if the input isn't a valid duration
     */
    public static Duration parse(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.replace(" ", "").toLowerCase(Locale.ROOT);
        if (!FULL_FORMAT.matcher(normalized).matches()) {
            return null;
        }

        Duration duration = Duration.ZERO;
        Matcher matcher = PART.matcher(normalized);
        try {
            while (matcher.find()) {
                long amount = Long.parseLong(matcher.group(1));
                duration = switch (matcher.group(2)) {
                    case "d" -> duration.plusDays(amount);
                    case "h" -> duration.plusHours(amount);
                    case "m" -> duration.plusMinutes(amount);
                    default -> duration.plusSeconds(amount);
                };
            }
        } catch (ArithmeticException | NumberFormatException e) {
            // Number too large to fit in a duration
            return null;
        }
        return duration;
    }

    /***
     * Formats a duration in the same style parse() reads, e.g. "1d 5h 4m 2s". Rounds down to whole seconds.
     */
    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds == 0) {
            return "0s";
        }
        StringBuilder formatted = new StringBuilder();
        appendPart(formatted, seconds / 86400, "d");
        appendPart(formatted, (seconds % 86400) / 3600, "h");
        appendPart(formatted, (seconds % 3600) / 60, "m");
        appendPart(formatted, seconds % 60, "s");
        return formatted.toString();
    }

    private static void appendPart(StringBuilder formatted, long amount, String unit) {
        if (amount == 0) {
            return;
        }
        if (!formatted.isEmpty()) {
            formatted.append(' ');
        }
        formatted.append(amount).append(unit);
    }
}
