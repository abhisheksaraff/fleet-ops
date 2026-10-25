package com.fleetops.domain.fleet;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** The kinds of unit in the fleet. */
public enum UnitType {
    DRONE("DRONE"),
    HELICOPTER("HELI"),
    CARRIER("ROVER");

    private final String idPrefix;

    UnitType(String idPrefix) {
        this.idPrefix = idPrefix;
    }

    /** Prefix used in unit ids, for example {@code HELI} in {@code HELI_1}. */
    public String idPrefix() {
        return idPrefix;
    }

    /**
     * Parses a type case-insensitively. Both the full name and the id prefix are accepted, so
     * {@code "helicopter"}, {@code "heli"}, {@code "carrier"} and {@code "rover"} all work (useful
     * for AI-generated plans).
     */
    public static UnitType parse(String raw) {
        String wanted = raw == null ? "" : raw.strip();
        for (UnitType type : values()) {
            if (type.name().equalsIgnoreCase(wanted) || type.idPrefix.equalsIgnoreCase(wanted)) {
                return type;
            }
        }
        String valid = Arrays.stream(values())
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown unit type '" + wanted + "'. Valid values: " + valid);
    }
}
