package com.fleetops.command;

import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Survivor;
import com.fleetops.domain.incident.SurvivorStatus;
import com.fleetops.sim.SimulationEngine;
import java.util.List;
import java.util.Objects;

/**
 * A helicopter flies to a survivor that a drone has found and picks them up. The command fails
 * straight away if the survivor has not been found (or is already rescued) or the helicopter is
 * full, so no flight is wasted.
 */
public final class PickUpCommand extends AbstractMissionCommand {

    private final Helicopter helicopter;
    private final Survivor survivor;

    public PickUpCommand(Helicopter helicopter, Survivor survivor) {
        super(helicopter);
        this.helicopter = helicopter;
        this.survivor = Objects.requireNonNull(survivor, "survivor");
    }

    @Override
    public String describe() {
        return helicopter.id() + " pick up " + survivor.id();
    }

    @Override
    public List<GeoPoint> route() {
        return List.of(survivor.position());
    }

    @Override
    protected void onStart(SimulationEngine engine) {
        if (survivor.status() != SurvivorStatus.FOUND) {
            fail(survivor.id() + " cannot be picked up while " + survivor.status());
        } else if (!helicopter.hasRoom()) {
            fail(helicopter.id() + " is full (" + helicopter.capacity() + " passengers)");
        } else {
            startWork(engine);
        }
    }

    @Override
    protected void onStep(SimulationEngine engine) {
        if (moveOrFail(engine, survivor.position())) {
            arriveIfEnRoute(engine);
            engine.rescue(helicopter, survivor);
            complete();
        }
    }
}
