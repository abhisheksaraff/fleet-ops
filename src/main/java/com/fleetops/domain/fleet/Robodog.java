package com.fleetops.domain.fleet;

import com.fleetops.domain.geo.GeoPoint;

/**
 * A four-legged ground robot carried by a rover. It is slower than a drone and has a shorter
 * sensor, but it walks tight circles around the spot where the rover drops it off, so it
 * searches rubble and debris that a drone flying past can miss.
 */
public final class Robodog extends Unit implements Sensing {

    public static final double SPEED_KMH = 15.0;
    public static final double BATTERY_DRAIN_PERCENT_PER_KM = 2.0;
    public static final double SENSOR_RADIUS_KM = 0.15;

    public Robodog(String id, GeoPoint position) {
        super(id, UnitType.ROBODOG, position, SPEED_KMH, BATTERY_DRAIN_PERCENT_PER_KM);
    }

    @Override
    public double sensorRadiusKm() {
        return SENSOR_RADIUS_KM;
    }
}
