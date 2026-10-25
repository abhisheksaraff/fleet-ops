package com.fleetops.strategy;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Sweeps the area in parallel east-west lanes, turning at each edge like a lawnmower.
 *
 * <p>The first and last lanes sit 0.9 sensor radii inside the edges, and lanes are never more than
 * 1.8 sensor radii apart, so neighbouring sensor swaths overlap slightly and nothing is missed.
 */
public final class LawnmowerStrategy implements SearchStrategy {

    /** Share of the sensor radius used for the edge inset and the lane overlap. */
    private static final double COVERAGE_FACTOR = 0.9;

    @Override
    public String name() {
        return "lawnmower";
    }

    @Override
    public List<GeoPoint> computePath(GeoArea area, double sensorRadiusKm) {
        SearchStrategies.requireValid(area, sensorRadiusKm);
        double radius = area.radiusKm();
        double inset = Math.min(COVERAGE_FACTOR * sensorRadiusKm, radius);
        double firstLane = -radius + inset;
        double lastLane = radius - inset;

        int lanes;
        double spacing;
        if (lastLane - firstLane < 1e-9) {
            lanes = 1; // a single lane through the middle already covers a very small area
            firstLane = 0.0;
            spacing = 0.0;
        } else {
            double maxSpacing = 2 * COVERAGE_FACTOR * sensorRadiusKm;
            lanes = (int) Math.ceil((lastLane - firstLane) / maxSpacing) + 1;
            spacing = (lastLane - firstLane) / (lanes - 1);
        }

        List<GeoPoint> path = new ArrayList<>(lanes * 2);
        for (int lane = 0; lane < lanes; lane++) {
            double north = firstLane + lane * spacing;
            double startEast = (lane % 2 == 0) ? -radius : radius;
            path.add(area.center().offsetByKm(north, startEast));
            path.add(area.center().offsetByKm(north, -startEast));
        }
        return List.copyOf(path);
    }
}
