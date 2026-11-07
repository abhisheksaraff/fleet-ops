package com.fleetops.strategy;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks circles outward from the drop-off point at the centre of the area, so the ground near
 * where the rover stopped is searched first. The circles are evenly spaced and never more than 1.6
 * sensor radii apart, and the outermost circle stays inside the area.
 *
 * <p>Each circle is walked as a polygon whose corners are no more than one sensor radius apart, so
 * the path hugs the circle. It is not one of the {@link SearchStrategies#all()} patterns that the
 * operator picks for drones; the rover's robodogs use it.
 */
public final class RadiusSweepStrategy implements SearchStrategy {

    private static final double MAX_RING_SPACING_IN_SENSOR_RADII = 1.6;

    @Override
    public String name() {
        return "radius-sweep";
    }

    @Override
    public List<GeoPoint> computePath(GeoArea area, double sensorRadiusKm) {
        SearchStrategies.requireValid(area, sensorRadiusKm);
        double radius = area.radiusKm();
        int rings = Math.max(1, (int) Math.ceil(radius / (MAX_RING_SPACING_IN_SENSOR_RADII * sensorRadiusKm)));

        List<GeoPoint> path = new ArrayList<>();
        path.add(area.center());
        for (int ring = 1; ring <= rings; ring++) {
            double ringRadius = radius * ring / rings;
            int corners = Math.max(8, (int) Math.ceil(2 * Math.PI * ringRadius / sensorRadiusKm));
            for (int i = 0; i <= corners; i++) {
                double angle = 2 * Math.PI * i / corners;
                path.add(area.center().offsetByKm(ringRadius * Math.cos(angle), ringRadius * Math.sin(angle)));
            }
        }
        return List.copyOf(path);
    }
}
