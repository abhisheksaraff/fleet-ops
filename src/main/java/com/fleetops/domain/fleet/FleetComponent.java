package com.fleetops.domain.fleet;

import java.util.List;
import java.util.Optional;

/**
 * The common interface of the Composite pattern used for the fleet.
 *
 * <p>A single {@link Unit} (the leaf), a {@link Carrier} holding drones, and a whole {@link Fleet}
 * are all {@code FleetComponent}s, so callers can ask any of them for its units, average battery,
 * or tell it to {@link #recall()} or {@link #hold()} without caring how it is structured inside.
 * Only {@link #units()}, {@link #recall()}, {@link #hold()} and {@link #id()} are implemented by the
 * classes; everything else is derived from {@code units()}.
 */
public interface FleetComponent {

    String id();

    /** Every unit in this component, including the component itself if it is a unit. */
    List<Unit> units();

    /** Sends everything in this component back to base (idle units stay where they are). */
    void recall();

    /** Puts everything in this component into a safe hold (idle units stay idle). */
    void hold();

    default int unitCount() {
        return units().size();
    }

    /** The units that are idle and ready to be given work. */
    default List<Unit> availableUnits() {
        return units().stream().filter(Unit::isAvailable).toList();
    }

    default Optional<Unit> findUnit(String unitId) {
        return units().stream().filter(unit -> unit.id().equals(unitId)).findFirst();
    }

    /** Mean battery level of all units, or 0 when there are none. */
    default double averageBatteryPercent() {
        return units().stream().mapToDouble(Unit::batteryPercent).average().orElse(0.0);
    }
}
