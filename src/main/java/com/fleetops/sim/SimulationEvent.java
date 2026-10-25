package com.fleetops.sim;

import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Survivor;

/**
 * Everything the {@link SimulationEngine} announces to its {@link SimulationListener}s.
 *
 * <p>The interface is sealed, so a listener that uses a {@code switch} over events gets a compile
 * error if a new event type is added and the listener does not handle it.
 */
public sealed interface SimulationEvent {

    /** One simulation step finished. */
    record TickCompleted(long tick) implements SimulationEvent {}

    /** A new incident area (with its hidden survivors) joined the world. */
    record ZoneAdded(IncidentZone zone) implements SimulationEvent {}

    /** A unit changed position (a carrier's docked drones are reported individually). */
    record UnitMoved(Unit unit, GeoPoint from, GeoPoint to) implements SimulationEvent {}

    /** A unit's lifecycle state changed. */
    record UnitStateChanged(Unit unit, UnitState from, UnitState to) implements SimulationEvent {}

    /** A scanning unit spotted a hidden survivor. */
    record SurvivorFound(Survivor survivor, Unit foundBy) implements SimulationEvent {}

    /** A helicopter picked a survivor up. */
    record SurvivorRescued(Survivor survivor, Helicopter rescuedBy) implements SimulationEvent {}

    /** The last survivor of an incident area was rescued. */
    record ZoneCleared(IncidentZone zone) implements SimulationEvent {}
}
