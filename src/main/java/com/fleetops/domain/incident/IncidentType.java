package com.fleetops.domain.incident;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** The kinds of disaster the system looks for in news and Telegram feeds. */
public enum IncidentType {
    FLOOD,
    FAMINE,
    FIRE,
    EARTHQUAKE;

    /** The lowercase word used as a news search keyword, e.g. {@code "flood"}. */
    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a type name case-insensitively (surrounding spaces are ignored). */
    public static IncidentType parse(String raw) {
        String wanted = raw == null ? "" : raw.strip();
        for (IncidentType type : values()) {
            if (type.name().equalsIgnoreCase(wanted)) {
                return type;
            }
        }
        String valid = Arrays.stream(values()).map(IncidentType::keyword).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown incident type '" + wanted + "'. Valid values: " + valid);
    }
}
