package com.fleetops.command;

import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import java.util.List;
import java.util.Objects;

/** Flies (or drives) a unit to a point: for example the rover to the edge of an incident area. */
public final class FlyToCommand extends AbstractMissionCommand {

    private final GeoPoint target;

    public FlyToCommand(Unit unit, GeoPoint target) {
        super(unit);
        this.target = Objects.requireNonNull(target, "target");
    }

    @Override
    public String describe() {
        return unit().id() + " go to " + Routes.format(target);
    }

    @Override
    public List<GeoPoint> route() {
        return List.of(target);
    }

    @Override
    protected void onStart(SimulationEngine engine) {
        startWork(engine);
    }

    @Override
    protected void onStep(SimulationEngine engine) {
        if (moveOrFail(engine, target)) {
            arriveIfEnRoute(engine);
            complete();
        }
    }
}
