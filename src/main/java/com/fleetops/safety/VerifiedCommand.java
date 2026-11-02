package com.fleetops.safety;

import com.fleetops.command.CommandStatus;
import com.fleetops.command.MissionCommand;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorator that puts a safety check in front of any {@link MissionCommand}. The queue, the GUI
 * and the planner treat it like any other command, but just before the first step it runs the
 * {@link SafetyPolicy} against the unit's <em>actual</em> position and battery (not a guess made
 * when the plan was written). If the policy objects, the wrapped command is never started: it is
 * cancelled, this command fails with the reasons, and the queue drops the rest of that unit's lane.
 */
public final class VerifiedCommand implements MissionCommand {

    private final MissionCommand delegate;
    private final SafetyPolicy policy;

    private boolean checked;
    private SafetyReport report;
    private String rejection;

    public VerifiedCommand(MissionCommand delegate, SafetyPolicy policy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.policy = Objects.requireNonNull(policy, "policy");
        if (delegate instanceof VerifiedCommand) {
            throw new IllegalArgumentException("Command is already verified: " + delegate.describe());
        }
    }

    /** The command being guarded. */
    public MissionCommand delegate() {
        return delegate;
    }

    /** The safety report, once the check has run (before that, empty). */
    public Optional<SafetyReport> report() {
        return Optional.ofNullable(report);
    }

    /** True if the safety check stopped this command. */
    public boolean wasRejected() {
        return rejection != null;
    }

    @Override
    public Unit unit() {
        return delegate.unit();
    }

    @Override
    public String describe() {
        return delegate.describe();
    }

    @Override
    public List<GeoPoint> route() {
        return delegate.route();
    }

    @Override
    public CommandStatus status() {
        return rejection != null ? CommandStatus.FAILED : delegate.status();
    }

    @Override
    public Optional<String> detail() {
        return rejection != null ? Optional.of(rejection) : delegate.detail();
    }

    @Override
    public CommandStatus step(SimulationEngine engine) {
        if (isFinished()) {
            return status();
        }
        if (!checked) {
            checked = true;
            report = policy.evaluate(delegate, delegate.unit().position());
            if (!report.isSafe()) {
                rejection = "rejected by safety check: " + report.summary();
                delegate.cancel(rejection);
                return status();
            }
        }
        return delegate.step(engine);
    }

    @Override
    public void cancel(String reason) {
        if (rejection == null) {
            delegate.cancel(reason);
        }
    }

    @Override
    public double estimatedDistanceKm(GeoPoint start) {
        return delegate.estimatedDistanceKm(start);
    }

    @Override
    public double requiredBatteryPercent(GeoPoint start) {
        return delegate.requiredBatteryPercent(start);
    }

    @Override
    public GeoPoint endPosition(GeoPoint start) {
        return delegate.endPosition(start);
    }

    @Override
    public String toString() {
        return describe() + " [" + status() + "]";
    }
}
