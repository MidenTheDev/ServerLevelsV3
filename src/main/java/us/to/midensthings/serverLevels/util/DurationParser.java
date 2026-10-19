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
}
