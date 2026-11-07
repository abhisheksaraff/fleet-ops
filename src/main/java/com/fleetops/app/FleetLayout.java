package com.fleetops.app;

import com.fleetops.domain.fleet.Carrier;

/**
 * How many units a simulation starts with.
 *
 * @param rovers ground rovers, each carrying drones
 * @param dronesPerRover drones loaded on each rover
 * @param helicopters rescue helicopters
 * @param robodogsPerRover robodogs loaded on each rover (may be 0)
 */
public record FleetLayout(int rovers, int dronesPerRover, int helicopters, int robodogsPerRover) {

    public static final int MAX_ROVERS = 10;
    public static final int MAX_HELICOPTERS = 20;

    /** One rover with six drones and two robodogs, and two helicopters. */
    public static final FleetLayout DEFAULT = new FleetLayout(1, 6, 2, 2);

    /** A layout without robodogs. */
    public FleetLayout(int rovers, int dronesPerRover, int helicopters) {
        this(rovers, dronesPerRover, helicopters, 0);
    }

    public FleetLayout {
        if (rovers < 1 || rovers > MAX_ROVERS) {
            throw new IllegalArgumentException("rovers must be between 1 and " + MAX_ROVERS + ": " + rovers);
        }
        if (dronesPerRover < 1 || dronesPerRover > Carrier.DEFAULT_DRONE_CAPACITY) {
            throw new IllegalArgumentException(
                    "drones per rover must be between 1 and " + Carrier.DEFAULT_DRONE_CAPACITY + ": " + dronesPerRover);
        }
        if (helicopters < 1 || helicopters > MAX_HELICOPTERS) {
            throw new IllegalArgumentException("helicopters must be between 1 and " + MAX_HELICOPTERS + ": " + helicopters);
        }
        if (robodogsPerRover < 0 || robodogsPerRover > Carrier.DEFAULT_ROBODOG_CAPACITY) {
            throw new IllegalArgumentException(
                    "robodogs per rover must be between 0 and " + Carrier.DEFAULT_ROBODOG_CAPACITY + ": " + robodogsPerRover);
        }
    }
}
