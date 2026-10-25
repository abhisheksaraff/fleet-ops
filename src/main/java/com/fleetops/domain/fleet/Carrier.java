package com.fleetops.domain.fleet;

import com.fleetops.domain.geo.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The ground rover that drives to an incident area and launches the drones. It is the composite
 * node of the fleet: a {@code Carrier} is itself a {@link Unit}, and it also contains the drones it
 * carries, so asking it for its {@link #units()} returns the rover plus every drone, and
 * {@link #recall()} / {@link #hold()} apply to all of them.
 *
 * <p>A drone that is {@code IDLE} counts as docked on the carrier and travels with it.
 */
public final class Carrier extends Unit {

    public static final double SPEED_KMH = 40.0;
    public static final double BATTERY_DRAIN_PERCENT_PER_KM = 0.5;
    public static final int DEFAULT_DRONE_CAPACITY = 10;

    private final int droneCapacity;
    private final List<Drone> drones = new ArrayList<>();

    public Carrier(String id, GeoPoint position, int droneCapacity) {
        super(id, UnitType.CARRIER, position, SPEED_KMH, BATTERY_DRAIN_PERCENT_PER_KM);
        if (droneCapacity <= 0) {
            throw new IllegalArgumentException("droneCapacity must be positive: " + droneCapacity);
        }
        this.droneCapacity = droneCapacity;
    }

    public int droneCapacity() {
        return droneCapacity;
    }

    public List<Drone> drones() {
        return List.copyOf(drones);
    }

    /** Drones that are docked and ready to launch. */
    public List<Drone> availableDrones() {
        return drones.stream().filter(Drone::isAvailable).toList();
    }

    /**
     * Loads an idle drone onto the carrier and moves it to the carrier's position.
     *
     * @throws IllegalStateException if the carrier is full or the drone is not idle
     * @throws IllegalArgumentException if a drone with the same id is already on board
     */
    public void addDrone(Drone drone) {
        Objects.requireNonNull(drone, "drone");
        if (drones.size() >= droneCapacity) {
            throw new IllegalStateException(id() + " is full (" + droneCapacity + " drones)");
        }
        if (!drone.isAvailable()) {
            throw new IllegalStateException(drone.id() + " must be idle to be loaded, but is " + drone.state());
        }
        if (drones.stream().anyMatch(d -> d.id().equals(drone.id()))) {
            throw new IllegalArgumentException(id() + " already carries " + drone.id());
        }
        drone.moveTo(position());
        drones.add(drone);
    }

    /** Moves the rover, and carries every docked (idle) drone along with it. */
    @Override
    public void moveTo(GeoPoint destination) {
        super.moveTo(destination);
        for (Drone drone : drones) {
            if (drone.state() == UnitState.IDLE) {
                drone.moveTo(destination);
            }
        }
    }

    @Override
    public List<Unit> units() {
        List<Unit> all = new ArrayList<>();
        all.add(this);
        all.addAll(drones);
        return List.copyOf(all);
    }

    @Override
    public void recall() {
        super.recall();
        drones.forEach(Drone::recall);
    }

    @Override
    public void hold() {
        super.hold();
        drones.forEach(Drone::hold);
    }
}
