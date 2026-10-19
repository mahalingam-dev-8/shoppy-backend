package com.shoppy.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses JWT_EXPIRATION in the format the NestJS version used ("1d", "12h", "30m", "45s",
 * "500ms", or a bare number of milliseconds), plus ISO-8601 durations such as "PT1H".
 */
final class DurationParser {

    private static final Pattern SHORT_FORM = Pattern.compile("^(\\d+)\\s*(ms|s|m|h|d)?$");

    private DurationParser() {}

    static Duration parse(String value) {
        String trimmed = value.trim();
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("P")) {
            return Duration.parse(trimmed.toUpperCase(Locale.ROOT));
        }
        Matcher matcher = SHORT_FORM.matcher(trimmed.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid JWT_EXPIRATION: " + value);
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2) == null ? "ms" : matcher.group(2);
        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Invalid JWT_EXPIRATION: " + value);
        };
    }
}
