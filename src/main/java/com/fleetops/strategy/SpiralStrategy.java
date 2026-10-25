package com.fleetops.strategy;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Flies square rings, starting along the edge of the area and spiralling inward to the centre. It
 * finds survivors near the edges first, which suits floods and fires that spread outward.
 *
 * <p>The rings are evenly spaced and never more than 1.6 sensor radii apart, which keeps even the
 * corner regions between two rings within the sensor radius.
 */
public final class SpiralStrategy implements SearchStrategy {

    private static final double MAX_RING_SPACING_IN_SENSOR_RADII = 1.6;
    private static final double CENTER_TOLERANCE_KM = 1e-9;

    @Override
    public String name() {
        return "spiral";
    }

    @Override
    public List<GeoPoint> computePath(GeoArea area, double sensorRadiusKm) {
        SearchStrategies.requireValid(area, sensorRadiusKm);
        double radius = area.radiusKm();
        int gaps = (int) Math.ceil(radius / (MAX_RING_SPACING_IN_SENSOR_RADII * sensorRadiusKm));
        gaps = Math.max(gaps, 1);

        List<GeoPoint> path = new ArrayList<>();
        for (int ring = 0; ring <= gaps; ring++) {
            double half = radius * (1.0 - (double) ring / gaps);
            if (half < CENTER_TOLERANCE_KM) {
                path.add(area.center());
                continue;
            }
            path.add(area.center().offsetByKm(-half, -half)); // south-west
            path.add(area.center().offsetByKm(-half, half)); // south-east
            path.add(area.center().offsetByKm(half, half)); // north-east
            path.add(area.center().offsetByKm(half, -half)); // north-west
            path.add(area.center().offsetByKm(-half, -half)); // back to the start of the ring
        }
        return List.copyOf(path);
    }
}
