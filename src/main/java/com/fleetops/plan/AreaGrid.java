package com.fleetops.plan;

import com.fleetops.domain.geo.GeoArea;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts an incident square into smaller squares so several drones can search different parts at the
 * same time instead of all flying the same pattern.
 */
public final class AreaGrid {

    /** The most squares the incident is cut into (3 x 3). */
    public static final int MAX_CELLS = 9;

    private AreaGrid() {}

    /**
     * How many squares per side to use for a number of drones, chosen so the work divides as evenly
     * as possible: 1 drone keeps the whole area, 2 or 4 drones use 2x2 (2+2 or 1 each), and 3 or
     * more than 4 use 3x3 (3 each for three drones).
     */
    public static int cellsPerSide(int drones) {
        if (drones < 1) {
            throw new IllegalArgumentException("drones must be at least 1: " + drones);
        }
        return switch (drones) {
            case 1 -> 1;
            case 2, 4 -> 2;
            default -> 3;
        };
    }

    /**
     * Splits the area into {@code perSide x perSide} equal squares, ordered like the path of a
     * mower (south row west to east, next row east to west, and so on) so that consecutive squares
     * always touch.
     */
    public static List<GeoArea> split(GeoArea area, int perSide) {
        if (perSide < 1 || perSide > 3) {
            throw new IllegalArgumentException("perSide must be 1 to 3: " + perSide);
        }
        double cellRadius = area.radiusKm() / perSide;
        List<GeoArea> cells = new ArrayList<>();
        for (int row = 0; row < perSide; row++) {
            for (int step = 0; step < perSide; step++) {
                int col = row % 2 == 0 ? step : perSide - 1 - step;
                double north = -area.radiusKm() + (2 * row + 1) * cellRadius;
                double east = -area.radiusKm() + (2 * col + 1) * cellRadius;
                cells.add(new GeoArea(area.center().offsetByKm(north, east), cellRadius));
            }
        }
        return cells;
    }

    /**
     * Hands the squares out to the drones in consecutive groups of (nearly) equal size, so each
     * drone's squares are neighbours.
     *
     * @return one list per drone; drones beyond the number of squares get nothing and are left out
     */
    public static List<List<GeoArea>> assign(List<GeoArea> cells, int drones) {
        if (drones < 1) {
            throw new IllegalArgumentException("drones must be at least 1: " + drones);
        }
        int groups = Math.min(drones, cells.size());
        int base = cells.size() / groups;
        int extra = cells.size() % groups;
        List<List<GeoArea>> result = new ArrayList<>();
        int next = 0;
        for (int g = 0; g < groups; g++) {
            int size = base + (g < extra ? 1 : 0);
            result.add(List.copyOf(cells.subList(next, next + size)));
            next += size;
        }
        return result;
    }
}
