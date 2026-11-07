package com.fleetops.plan;

import com.fleetops.domain.fleet.Carrier;
import com.fleetops.domain.fleet.Drone;
import com.fleetops.domain.fleet.Fleet;
import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.fleet.Robodog;
import com.fleetops.domain.fleet.Unit;
import java.util.List;
import com.fleetops.domain.incident.Incident;
import java.util.Objects;

/**
 * Everything a planner needs to know: the incident, and how many drones, robodogs and helicopters
 * are ready.
 *
 * @param incident the incident to respond to
 * @param availableDrones drones docked on a carrier and ready to launch
 * @param availableHelicopters helicopters that are idle
 * @param availableRobodogs robodogs docked on a carrier and ready to be dropped off
 */
public record PlanningContext(Incident incident, int availableDrones, int availableHelicopters, int availableRobodogs) {

    public PlanningContext {
        Objects.requireNonNull(incident, "incident");
        if (availableDrones < 0 || availableHelicopters < 0 || availableRobodogs < 0) {
            throw new IllegalArgumentException("Available counts must not be negative");
        }
    }

    /** A context without robodogs. */
    public PlanningContext(Incident incident, int availableDrones, int availableHelicopters) {
        this(incident, availableDrones, availableHelicopters, 0);
    }

    /** A drone, robodog or helicopter below this battery level stays on the ground until it is recharged. */
    public static final double MIN_LAUNCH_BATTERY_PERCENT = 50.0;

    /** True if the unit is idle and has enough battery to be sent out. */
    public static boolean isReady(Unit unit) {
        return unit.isAvailable() && unit.batteryPercent() >= MIN_LAUNCH_BATTERY_PERCENT;
    }

    /** The drones on a carrier that are ready to launch. */
    public static List<Drone> readyDrones(Carrier carrier) {
        return carrier.drones().stream().filter(PlanningContext::isReady).toList();
    }

    /** The robodogs on a carrier that are ready to be dropped off. */
    public static List<Robodog> readyRobodogs(Carrier carrier) {
        return carrier.robodogs().stream().filter(PlanningContext::isReady).toList();
    }

    /** Counts the ready units in the fleet. */
    public static PlanningContext of(Incident incident, Fleet fleet) {
        Objects.requireNonNull(fleet, "fleet");
        int drones = fleet.units().stream().filter(u -> u instanceof Carrier).mapToInt(u -> readyDrones((Carrier) u).size()).sum();
        int helicopters = (int) fleet.units().stream().filter(u -> u instanceof Helicopter && isReady(u)).count();
        int robodogs = fleet.units().stream().filter(u -> u instanceof Carrier).mapToInt(u -> readyRobodogs((Carrier) u).size()).sum();
        return new PlanningContext(incident, drones, helicopters, robodogs);
    }
}
