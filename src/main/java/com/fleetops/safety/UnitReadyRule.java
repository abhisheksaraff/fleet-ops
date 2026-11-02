package com.fleetops.safety;

import com.fleetops.command.MissionCommand;
import com.fleetops.command.ReturnToBaseCommand;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoPoint;
import java.util.Optional;

/**
 * The unit must be able to take the job at all: it needs some battery, and a unit that is on hold
 * or already heading home may only be told to return to base.
 */
public final class UnitReadyRule implements SafetyRule {

    @Override
    public String name() {
        return "UnitReady";
    }

    @Override
    public Optional<String> check(MissionCommand command, GeoPoint start) {
        Unit unit = command.unit();
        if (unit.isDepleted()) {
            return Optional.of(unit.id() + " has no battery left");
        }
        boolean busyOrStopped = unit.state() == UnitState.HOLDING || unit.state() == UnitState.RETURNING;
        if (busyOrStopped && !(command instanceof ReturnToBaseCommand)) {
            return Optional.of(unit.id() + " is " + unit.state() + " and can only return to base");
        }
        return Optional.empty();
    }
}
