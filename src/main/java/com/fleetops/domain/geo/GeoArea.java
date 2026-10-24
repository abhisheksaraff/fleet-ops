package com.fleetops.domain.geo;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * A square incident area on the map: a centre point plus a "radius" (half the side length) in
 * kilometres. The square is aligned with north/east, so on a map it is drawn as the rectangle
 * between {@link #southWest()} and {@link #northEast()}.
 */
public record GeoArea(GeoPoint center, double radiusKm) {

    /** Tolerance (in km) so points computed on the boundary still count as inside. */
    private static final double EDGE_TOLERANCE_KM = 1e-9;

    public GeoArea {
        Objects.requireNonNull(center, "center");
        if (!(radiusKm > 0.0) || Double.isInfinite(radiusKm)) {
            throw new IllegalArgumentException("radiusKm must be positive and finite: " + radiusKm);
        }
    }

    public double sideKm() {
        return 2 * radiusKm;
    }

    public double areaKm2() {
        return sideKm() * sideKm();
    }

    public boolean contains(GeoPoint point) {
        double north = center.northKmTo(point);
        double east = center.eastKmTo(point);
        return Math.abs(north) <= radiusKm + EDGE_TOLERANCE_KM
                && Math.abs(east) <= radiusKm + EDGE_TOLERANCE_KM;
    }

    public GeoPoint southWest() {
        return center.offsetByKm(-radiusKm, -radiusKm);
    }

    public GeoPoint northEast() {
        return center.offsetByKm(radiusKm, radiusKm);
    }

    /**
     * A uniformly random point inside the square. Pass a seeded generator to get the same points
     * every time (used to place survivors reproducibly).
     */
    public GeoPoint randomPoint(RandomGenerator random) {
        double north = (random.nextDouble() * 2 - 1) * radiusKm;
        double east = (random.nextDouble() * 2 - 1) * radiusKm;
        return center.offsetByKm(north, east);
    }
}
