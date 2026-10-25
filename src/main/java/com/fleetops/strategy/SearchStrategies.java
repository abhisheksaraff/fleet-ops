package com.fleetops.strategy;

import com.fleetops.domain.geo.GeoArea;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** The available search strategies, and a way to look one up by name (for the CLI and the AI planner). */
public final class SearchStrategies {

    public static final SearchStrategy LAWNMOWER = new LawnmowerStrategy();
    public static final SearchStrategy SPIRAL = new SpiralStrategy();

    private static final List<SearchStrategy> ALL = List.of(LAWNMOWER, SPIRAL);

    private SearchStrategies() {}

    public static List<SearchStrategy> all() {
        return ALL;
    }

    /** Finds a strategy by name, ignoring case and surrounding spaces. */
    public static SearchStrategy parse(String name) {
        String wanted = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        for (SearchStrategy strategy : ALL) {
            if (strategy.name().equals(wanted)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown search strategy '" + wanted + "'. Valid values: "
                + String.join(", ", ALL.stream().map(SearchStrategy::name).toList()));
    }

    static void requireValid(GeoArea area, double sensorRadiusKm) {
        Objects.requireNonNull(area, "area");
        if (!(sensorRadiusKm > 0.0) || Double.isInfinite(sensorRadiusKm)) {
            throw new IllegalArgumentException("sensorRadiusKm must be positive and finite: " + sensorRadiusKm);
        }
    }
}
