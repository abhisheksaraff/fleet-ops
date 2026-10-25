package com.fleetops.command;

import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import java.util.List;
import java.util.Optional;

/**
 * One job for one unit, as an object: the Command pattern. The AI planner and the operator both
 * produce {@code MissionCommand}s, and the queue, the safety gate and the GUI handle them all the
 * same way without knowing what each one does.
 *
 * <p>A command is carried out in small steps, one per simulation tick, so robots move gradually,
 * burn battery along the way, and can be stopped at any moment. Before it runs, a command can be
 * inspected: {@link #route()} and the estimates below tell the safety gate what it would do.
 */
public interface MissionCommand {

    /** The unit that carries out this command. */
    Unit unit();

    /** One-line, human-readable description, for example {@code DRONE_1 sweep 4.0 km square (spiral)}. */
    String describe();

    /** The places the unit will fly through, in order. */
    List<GeoPoint> route();

    CommandStatus status();

    /** Why the command failed or was cancelled; empty otherwise. */
    Optional<String> detail();

    /**
     * Advances the command by one tick.
     *
     * @return the status afterwards: {@code RUNNING}, or a finished status
     */
    CommandStatus step(SimulationEngine engine);

    /** Drops the command if it has not finished yet. */
    void cancel(String reason);

    default boolean isFinished() {
        return status().isFinished();
    }

    /** Distance the unit would fly if it began at {@code start}. */
    default double estimatedDistanceKm(GeoPoint start) {
        return Routes.lengthKm(start, route());
    }

    /** Battery the unit would use if it began at {@code start}. */
    default double requiredBatteryPercent(GeoPoint start) {
        return estimatedDistanceKm(start) * unit().batteryDrainPercentPerKm();
    }

    /** Where the unit ends up if it began at {@code start}. */
    default GeoPoint endPosition(GeoPoint start) {
        List<GeoPoint> route = route();
        return route.isEmpty() ? start : route.get(route.size() - 1);
    }
}
