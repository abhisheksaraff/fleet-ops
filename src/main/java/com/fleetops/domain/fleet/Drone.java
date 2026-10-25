package com.fleetops.domain.fleet;

import com.fleetops.domain.geo.GeoPoint;

/**
 * A fast, short-range reconnaissance drone. It flies over an incident area and reveals the
 * survivors within {@link #sensorRadiusKm()} of its path.
 */
public final class Drone extends Unit {

    public static final double SPEED_KMH = 60.0;
    public static final double BATTERY_DRAIN_PERCENT_PER_KM = 1.5;
    public static final double SENSOR_RADIUS_KM = 0.25;

    public Drone(String id, GeoPoint position) {
        super(id, UnitType.DRONE, position, SPEED_KMH, BATTERY_DRAIN_PERCENT_PER_KM);
    }

    /** How far from its flight path the drone can spot a survivor. */
    public double sensorRadiusKm() {
        return SENSOR_RADIUS_KM;
    }
}
