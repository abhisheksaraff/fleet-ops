package com.fleetops.domain.incident;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** How urgent an incident is. The planner scales the size of the response with this. */
public enum Severity {
    LOW(1),
    MODERATE(2),
    HIGH(3),
    CRITICAL(4);

    private final int level;

    Severity(int level) {
        this.level = level;
    }

    /** Numeric urgency from 1 (low) to 4 (critical). */
    public int level() {
        return level;
    }

    /** Parses a severity name case-insensitively (surrounding spaces are ignored). */
    public static Severity parse(String raw) {
        String wanted = raw == null ? "" : raw.strip();
        for (Severity severity : values()) {
            if (severity.name().equalsIgnoreCase(wanted)) {
                return severity;
            }
        }
        String valid = Arrays.stream(values())
                .map(s -> s.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown severity '" + wanted + "'. Valid values: " + valid);
    }
}
