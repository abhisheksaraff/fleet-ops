package com.fleetops.command;

import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for the concrete commands. {@link #step} is a Template Method: it fixes the order of
 * events (start once, check the unit is not on hold, take a step, turn any
 * {@link IllegalStateException} into a failed command) and the subclasses only fill in
 * {@link #onStart} and {@link #onStep}. A command that cannot be carried out therefore fails with a
 * reason instead of crashing the simulation.
 */
public abstract class AbstractMissionCommand implements MissionCommand {

    private final Unit unit;
    private CommandStatus status = CommandStatus.PENDING;
    private String detail;

    protected AbstractMissionCommand(Unit unit) {
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    @Override
    public final Unit unit() {
        return unit;
    }

    @Override
    public final CommandStatus status() {
        return status;
    }

    @Override
    public final Optional<String> detail() {
        return Optional.ofNullable(detail);
    }

    @Override
    public final CommandStatus step(SimulationEngine engine) {
        if (status.isFinished()) {
            return status;
        }
        try {
            if (status == CommandStatus.PENDING) {
                status = CommandStatus.RUNNING;
                onStart(engine);
            }
            if (status == CommandStatus.RUNNING) {
                if (unit.state() == UnitState.HOLDING) {
                    fail(unit.id() + " is on hold");
                } else {
                    onStep(engine);
                }
            }
        } catch (IllegalStateException e) {
            fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return status;
    }

    @Override
    public final void cancel(String reason) {
        if (!status.isFinished()) {
            status = CommandStatus.CANCELLED;
            detail = reason;
        }
    }

    /** Called once, at the start of the first step. May call {@link #complete()} or {@link #fail}. */
    protected abstract void onStart(SimulationEngine engine);

    /** Called on every step while the command is running. */
    protected abstract void onStep(SimulationEngine engine);

    protected final void complete() {
        if (status == CommandStatus.RUNNING) {
            status = CommandStatus.DONE;
        }
    }

    protected final void fail(String reason) {
        if (!status.isFinished()) {
            status = CommandStatus.FAILED;
            detail = reason;
        }
    }

    /**
     * Gets the unit ready to work: an idle unit is dispatched, one already working carries on, and a
     * unit that is heading home or on hold makes the command fail.
     *
     * @return false if the command failed
     */
    protected final boolean startWork(SimulationEngine engine) {
        switch (unit.state()) {
            case IDLE -> engine.changeState(unit, Unit::dispatch);
            case EN_ROUTE, OPERATING -> {
                // already out working; nothing to do
            }
            case RETURNING, HOLDING -> {
                fail(unit.id() + " cannot start work while " + unit.state());
                return false;
            }
        }
        return true;
    }

    /** Marks arrival at the work site: a unit that is still en route becomes operating. */
    protected final void arriveIfEnRoute(SimulationEngine engine) {
        if (unit.state() == UnitState.EN_ROUTE) {
            engine.changeState(unit, Unit::arrive);
        }
    }

    /**
     * Moves the unit one step toward the target.
     *
     * @return true on arrival; false if still travelling, or if the command failed because the
     *     battery ran out
     */
    protected final boolean moveOrFail(SimulationEngine engine, GeoPoint target) {
        boolean arrived = engine.moveToward(unit, target);
        if (!arrived && unit.isDepleted()) {
            fail(unit.id() + " ran out of battery");
        }
        return arrived;
    }

    @Override
    public String toString() {
        return describe() + " [" + status + "]";
    }
}
