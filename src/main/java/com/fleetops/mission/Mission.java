package com.fleetops.mission;

import com.fleetops.command.MissionCommand;
import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.incident.Incident;
import com.fleetops.plan.MissionPlan;
import com.fleetops.sim.IncidentZone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One response that is under way: a {@link MissionPlan} that was launched, the incident area it
 * works on and the helicopters serving it. {@link MissionControl} creates and advances missions;
 * everyone else only reads them.
 *
 * <p>A mission passes through two working phases and then ends:
 *
 * <ol>
 *   <li><b>searching</b> - the rover drives to the area and the drones sweep it while the
 *       helicopters pick up whoever is found;
 *   <li><b>returning</b> - the rover drives home with its drones and the last passengers are
 *       delivered;
 *   <li>finished, with a {@link MissionReport}.
 * </ol>
 */
public final class Mission {

    /** The working phases of a mission; package-private because only {@link MissionControl} moves it along. */
    enum Phase {
        SEARCHING,
        RETURNING,
        FINISHED
    }

    private final MissionPlan plan;
    private final IncidentZone zone;
    private final List<Helicopter> helicopters;
    private final long startedAtTick;
    private final double startedAtSeconds;
    private final List<String> notes = new ArrayList<>();

    private MissionStatus status = MissionStatus.RUNNING;
    private Phase phase = Phase.SEARCHING;
    private List<MissionCommand> searchCommands;
    private MissionCommand homeCommand;
    private String abortReason;
    private MissionReport report;

    Mission(MissionPlan plan, IncidentZone zone, List<Helicopter> helicopters, long startedAtTick, double startedAtSeconds) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.helicopters = List.copyOf(helicopters);
        this.startedAtTick = startedAtTick;
        this.startedAtSeconds = startedAtSeconds;
        this.searchCommands = plan.commands();
    }

    public MissionPlan plan() {
        return plan;
    }

    public Incident incident() {
        return plan.incident();
    }

    /** The incident area with its survivors. */
    public IncidentZone zone() {
        return zone;
    }

    /** The helicopters reserved for this mission's pick-ups. */
    public List<Helicopter> helicopters() {
        return helicopters;
    }

    public MissionStatus status() {
        return status;
    }

    public boolean isFinished() {
        return status.isFinished();
    }

    /** True once the operator asked to stop; the mission still runs on until its units are home. */
    public boolean isAbortRequested() {
        return abortReason != null;
    }

    public Optional<String> abortReason() {
        return Optional.ofNullable(abortReason);
    }

    /** The outcome, once the mission has finished. */
    public Optional<MissionReport> report() {
        return Optional.ofNullable(report);
    }

    public long startedAtTick() {
        return startedAtTick;
    }

    public double startedAtSeconds() {
        return startedAtSeconds;
    }

    /** One line for a status display. */
    public String describe() {
        String state = status == MissionStatus.RUNNING
                ? (isAbortRequested() ? "ABORTING" : phase.name())
                : status.name();
        return String.format(Locale.ROOT, "%s: %s (%d/%d rescued)", incident().title(), state, zone.rescuedCount(), zone.totalCount());
    }

    @Override
    public String toString() {
        return "Mission[" + describe() + "]";
    }

    // ---- used by MissionControl ----------------------------------------------------------------

    Phase phase() {
        return phase;
    }

    List<MissionCommand> searchCommands() {
        return searchCommands;
    }

    MissionCommand homeCommand() {
        return homeCommand;
    }

    List<String> notes() {
        return notes;
    }

    /** Replaces the commands whose end means the searching phase is over (used when aborting). */
    void replaceSearchCommands(List<MissionCommand> commands) {
        this.searchCommands = List.copyOf(commands);
    }

    void beginReturning(MissionCommand home) {
        this.homeCommand = Objects.requireNonNull(home, "home");
        this.phase = Phase.RETURNING;
    }

    void requestAbort(String reason) {
        this.abortReason = reason == null || reason.isBlank() ? "stopped by the operator" : reason.strip();
    }

    void finish(MissionStatus finalStatus, MissionReport finalReport) {
        this.status = finalStatus;
        this.report = finalReport;
        this.phase = Phase.FINISHED;
    }
}
