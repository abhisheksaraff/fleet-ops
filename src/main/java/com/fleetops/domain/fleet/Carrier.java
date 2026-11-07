package com.fleetops.domain.fleet;

import com.fleetops.domain.geo.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The ground rover that drives to an incident area and launches the drones and robodogs. It is the composite
 * node of the fleet: a {@code Carrier} is itself a {@link Unit}, and it also contains the drones and
 * robodogs it carries, so asking it for its {@link #units()} returns the rover plus every one of
 * them, and
 * {@link #recall()} / {@link #hold()} apply to all of them.
 *
 * <p>A drone or robodog that is {@code IDLE} counts as docked on the carrier and travels with it.
 */
public final class Carrier extends Unit {

    public static final double SPEED_KMH = 40.0;
    public static final double BATTERY_DRAIN_PERCENT_PER_KM = 0.5;
    public static final int DEFAULT_DRONE_CAPACITY = 10;
    public static final int DEFAULT_ROBODOG_CAPACITY = 4;

    private final int droneCapacity;
    private final List<Drone> drones = new ArrayList<>();
    private final List<Robodog> robodogs = new ArrayList<>();

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

    public int robodogCapacity() {
        return DEFAULT_ROBODOG_CAPACITY;
    }

    public List<Robodog> robodogs() {
        return List.copyOf(robodogs);
    }

    /** Robodogs that are docked and ready to be dropped off. */
    public List<Robodog> availableRobodogs() {
        return robodogs.stream().filter(Robodog::isAvailable).toList();
    }

    /**
     * Loads an idle robodog onto the carrier and moves it to the carrier's position.
     *
     * @throws IllegalStateException if the carrier has no room or the robodog is not idle
     * @throws IllegalArgumentException if a robodog with the same id is already on board
     */
    public void addRobodog(Robodog robodog) {
        Objects.requireNonNull(robodog, "robodog");
        if (robodogs.size() >= DEFAULT_ROBODOG_CAPACITY) {
            throw new IllegalStateException(id() + " cannot carry more than " + DEFAULT_ROBODOG_CAPACITY + " robodogs");
        }
        if (!robodog.isAvailable()) {
            throw new IllegalStateException(robodog.id() + " must be idle to be loaded, but is " + robodog.state());
        }
        if (robodogs.stream().anyMatch(d -> d.id().equals(robodog.id()))) {
            throw new IllegalArgumentException(id() + " already carries " + robodog.id());
        }
        robodog.moveTo(position());
        robodogs.add(robodog);
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
        for (Robodog robodog : robodogs) {
            if (robodog.state() == UnitState.IDLE) {
                robodog.moveTo(destination);
            }
        }
    }

    @Override
    public List<Unit> units() {
        List<Unit> all = new ArrayList<>();
        all.add(this);
        all.addAll(drones);
        all.addAll(robodogs);
        return List.copyOf(all);
    }

    @Override
    public void recall() {
        super.recall();
        drones.forEach(Drone::recall);
        robodogs.forEach(Robodog::recall);
    }

    @Override
    public void hold() {
        super.hold();
        drones.forEach(Drone::hold);
        robodogs.forEach(Robodog::hold);
    }
}
