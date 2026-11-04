package com.fleetops.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The places incident reports can come from. {@code NEWS_SOURCES} in {@code .env} lists the ones to
 * use; only {@code AppContext} turns a type into a real news source.
 */
public enum NewsSourceType {
    /** Google News search feed (no key needed). */
    GOOGLE,
    /** Public Telegram channels, read through their web preview (no bot token needed). */
    TELEGRAM,
    /** A few invented stories that work offline; the default, so the program runs anywhere. */
    DEMO;

    /** Parses a source name case-insensitively. */
    public static NewsSourceType parse(String raw) {
        String wanted = raw == null ? "" : raw.strip();
        for (NewsSourceType type : values()) {
            if (type.name().equalsIgnoreCase(wanted)) {
                return type;
            }
        }
        String valid = Arrays.stream(values())
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        throw new ConfigException("Unknown NEWS_SOURCES entry '" + wanted + "'. Valid values: " + valid + ".");
    }
}
