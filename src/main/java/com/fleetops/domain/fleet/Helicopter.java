package com.fleetops.domain.fleet;

import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Survivor;
import java.util.ArrayList;
import java.util.List;

/**
 * A fast, long-range rescue helicopter that picks up survivors that a drone has found. It can
 * carry a limited number of people per trip.
 */
public final class Helicopter extends Unit {

    public static final double SPEED_KMH = 180.0;
    public static final double BATTERY_DRAIN_PERCENT_PER_KM = 0.3;
    public static final int DEFAULT_CAPACITY = 4;

    private final int capacity;
    private final List<Survivor> passengers = new ArrayList<>();

    public Helicopter(String id, GeoPoint position) {
        this(id, position, DEFAULT_CAPACITY);
    }

    public Helicopter(String id, GeoPoint position, int capacity) {
        super(id, UnitType.HELICOPTER, position, SPEED_KMH, BATTERY_DRAIN_PERCENT_PER_KM);
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int passengerCount() {
        return passengers.size();
    }

    public boolean hasRoom() {
        return passengers.size() < capacity;
    }

    public List<Survivor> passengers() {
        return List.copyOf(passengers);
    }

    /**
     * Picks a survivor up, which marks them as rescued.
     *
     * @throws IllegalStateException if the helicopter is full or the survivor has not been found
     */
    public void board(Survivor survivor) {
        if (!hasRoom()) {
            throw new IllegalStateException(id() + " is full (" + capacity + " passengers)");
        }
        survivor.markRescued();
        passengers.add(survivor);
    }

    /** Drops everyone off at base and returns how many people were delivered. */
    public int unloadAll() {
        int delivered = passengers.size();
        passengers.clear();
        return delivered;
    }
}
