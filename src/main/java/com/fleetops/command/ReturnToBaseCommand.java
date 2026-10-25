package com.fleetops.command;

import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import java.util.List;
import java.util.Objects;

/**
 * Sends a unit home. It works from any state, including after an emergency hold. On arrival the
 * unit docks and recharges, and a helicopter drops off its passengers.
 */
public final class ReturnToBaseCommand extends AbstractMissionCommand {

    private final GeoPoint base;
    private int delivered;

    public ReturnToBaseCommand(Unit unit, GeoPoint base) {
        super(unit);
        this.base = Objects.requireNonNull(base, "base");
    }

    @Override
    public String describe() {
        return unit().id() + " return to base";
    }

    @Override
    public List<GeoPoint> route() {
        return List.of(base);
    }

    /** How many people a helicopter dropped off at base (0 for other units, or before arrival). */
    public int delivered() {
        return delivered;
    }

    @Override
    protected void onStart(SimulationEngine engine) {
        Unit unit = unit();
        if (unit.state() == UnitState.IDLE) {
            if (unit.position().equals(base)) {
                complete(); // already home
                return;
            }
            engine.changeState(unit, Unit::dispatch);
        }
        engine.recall(unit);
    }

    @Override
    protected void onStep(SimulationEngine engine) {
        if (moveOrFail(engine, base)) {
            Unit unit = unit();
            engine.changeState(unit, Unit::dock);
            if (unit instanceof Helicopter helicopter) {
                delivered = helicopter.unloadAll();
            }
            unit.recharge();
            complete();
        }
    }
}
