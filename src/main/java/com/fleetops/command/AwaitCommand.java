package com.fleetops.command;

import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Makes one unit wait until a command of <em>another</em> unit has finished. The queue runs each
 * unit's commands in order but different units side by side, so this is how a plan says "the
 * drones start only after the rover has arrived". The waiting unit does not move or change state.
 *
 * <p>If the awaited command fails or is cancelled, this command fails too, which in turn cancels
 * the rest of the waiting unit's commands. The awaited command must be queued as well, otherwise
 * the wait never ends.
 */
public final class AwaitCommand extends AbstractMissionCommand {

    private final MissionCommand dependency;

    public AwaitCommand(Unit unit, MissionCommand dependency) {
        super(unit);
        this.dependency = Objects.requireNonNull(dependency, "dependency");
        if (dependency.unit() == unit) {
            throw new IllegalArgumentException("A unit cannot wait for its own command: " + dependency.describe());
        }
    }

    @Override
    public String describe() {
        return unit().id() + " wait for: " + dependency.describe();
    }

    @Override
    public List<GeoPoint> route() {
        return List.of();
    }

    @Override
    protected void onStart(SimulationEngine engine) {
        // nothing to prepare: the unit stays exactly as it is
    }

    @Override
    protected void onStep(SimulationEngine engine) {
        switch (dependency.status()) {
            case DONE -> complete();
            case FAILED, CANCELLED -> fail("cannot go on: '" + dependency.describe() + "' " + dependency.status().name().toLowerCase(Locale.ROOT));
            case PENDING, RUNNING -> {
                // keep waiting
            }
        }
    }
}
