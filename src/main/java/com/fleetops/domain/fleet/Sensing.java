package com.fleetops.domain.fleet;

/** A unit that can spot survivors around itself, such as a {@link Drone} or a {@link Robodog}. */
public interface Sensing {

    /** How far from itself the unit can spot a survivor. */
    double sensorRadiusKm();
}
