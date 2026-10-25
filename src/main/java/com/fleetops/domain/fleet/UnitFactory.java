package com.fleetops.domain.fleet;

import com.fleetops.domain.geo.GeoPoint;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates units and gives each one a unique, readable id ({@code DRONE_1}, {@code HELI_1},
 * {@code ROVER_1}, ...). Code that decides what to deploy, such as the AI planner choosing "2
 * helicopters and 4 drones", asks the factory instead of calling constructors, so adding a new unit
 * type only touches this class and {@link UnitType}.
 */
public final class UnitFactory {

    private final Map<UnitType, AtomicInteger> counters = new EnumMap<>(UnitType.class);

    public UnitFactory() {
        for (UnitType type : UnitType.values()) {
            counters.put(type, new AtomicInteger());
        }
    }

    /** Creates a unit of the given type; a carrier created this way starts with no drones. */
    public Unit create(UnitType type, GeoPoint position) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case DRONE -> createDrone(position);
            case HELICOPTER -> createHelicopter(position);
            case CARRIER -> createCarrier(position, 0);
        };
    }

    public Drone createDrone(GeoPoint position) {
        return new Drone(nextId(UnitType.DRONE), position);
    }

    public Helicopter createHelicopter(GeoPoint position) {
        return new Helicopter(nextId(UnitType.HELICOPTER), position);
    }

    /** Creates a rover already loaded with the given number of new, fully charged drones. */
    public Carrier createCarrier(GeoPoint position, int droneCount) {
        if (droneCount < 0 || droneCount > Carrier.DEFAULT_DRONE_CAPACITY) {
            throw new IllegalArgumentException(
                    "droneCount must be between 0 and " + Carrier.DEFAULT_DRONE_CAPACITY + ": " + droneCount);
        }
        Carrier carrier = new Carrier(nextId(UnitType.CARRIER), position, Carrier.DEFAULT_DRONE_CAPACITY);
        for (int i = 0; i < droneCount; i++) {
            carrier.addDrone(createDrone(position));
        }
        return carrier;
    }

    private String nextId(UnitType type) {
        return type.idPrefix() + "_" + counters.get(type).incrementAndGet();
    }
}
