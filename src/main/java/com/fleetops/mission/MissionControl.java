package com.fleetops.mission;

import com.fleetops.command.CommandQueue;
import com.fleetops.command.CommandStatus;
import com.fleetops.command.MissionCommand;
import com.fleetops.command.ReturnToBaseCommand;
import com.fleetops.domain.fleet.Carrier;
import com.fleetops.domain.fleet.Drone;
import com.fleetops.domain.fleet.Robodog;
import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.plan.MissionPlan;
import com.fleetops.plan.PlanCompiler;
import com.fleetops.plan.PlanningContext;
import com.fleetops.plan.PlanningException;
import com.fleetops.plan.ResponseDecision;
import com.fleetops.plan.ResponsePlanner;
import com.fleetops.repository.IncidentRepository;
import com.fleetops.safety.SafetyPolicy;
import com.fleetops.sim.IncidentZone;
import com.fleetops.sim.SimulationEngine;
import com.fleetops.sim.SurvivorSpawner;
import com.fleetops.strategy.SearchStrategy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The single entry point for running a response, a Facade over everything built so far: the
 * planner and plan compiler, the command queue and safety gate, the rescue dispatcher, the
 * simulation engine and the incident repository. The GUI and the CLI call only this class:
 *
 * <pre>
 *   MissionPlan plan = control.plan(incident);   // VERIFIED -> PLANNED, nothing moves yet
 *   Mission mission = control.launch(plan);      // PLANNED  -> IN_PROGRESS, units set off
 *   engine.runUntil(...);                        // ticks; the mission finishes by itself
 *   mission.report();                            // COMPLETED, or back to VERIFIED if not everyone was reached
 * </pre>
 *
 * <p>The control owns the command queue (and registers it with the engine, ahead of its own tick
 * work), so create one control per engine. Several missions can run at once as long as they use
 * different rovers and helicopters.
 *
 * <p>Incidents that were never stored (no id) work too; they are simply not written to the repository.
 */
public final class MissionControl {

    private static final System.Logger LOG = System.getLogger(MissionControl.class.getName());

    private final SimulationEngine engine;
    private final ResponsePlanner planner;
    private final PlanCompiler compiler;
    private final IncidentRepository repository;
    private final SurvivorSpawner spawner;
    private final CommandQueue queue = new CommandQueue();
    private final RescueDispatcher dispatcher;
    private final List<Mission> missions = new ArrayList<>();
    private final List<MissionListener> listeners = new CopyOnWriteArrayList<>();

    /** Uses {@link SafetyPolicy#standard} for the helicopters' pick-ups. */
    public MissionControl(
            SimulationEngine engine,
            ResponsePlanner planner,
            PlanCompiler compiler,
            IncidentRepository repository,
            SurvivorSpawner spawner) {
        this(engine, planner, compiler, repository, spawner, SafetyPolicy::standard);
    }

    /** @param policies builds the safety policy for a helicopter's home base */
    public MissionControl(
            SimulationEngine engine,
            ResponsePlanner planner,
            PlanCompiler compiler,
            IncidentRepository repository,
            SurvivorSpawner spawner,
            Function<GeoPoint, SafetyPolicy> policies) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.spawner = Objects.requireNonNull(spawner, "spawner");
        this.dispatcher = new RescueDispatcher(queue, Objects.requireNonNull(policies, "policies"), this::isSearching);

        // Order matters: commands move the units first, then missions react to what just happened.
        engine.addTickTask(queue);
        engine.addTickTask(this::onTick);
        engine.addListener(dispatcher);
        queue.addListener(dispatcher);
    }

    // ---- accessors -----------------------------------------------------------------------------

    public SimulationEngine engine() {
        return engine;
    }

    /** The queue that runs every mission's commands (also usable for manual operator commands). */
    public CommandQueue queue() {
        return queue;
    }

    public RescueDispatcher dispatcher() {
        return dispatcher;
    }

    public void addListener(MissionListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(MissionListener listener) {
        listeners.remove(listener);
    }

    /** Every mission launched so far, oldest first. */
    public List<Mission> missions() {
        return List.copyOf(missions);
    }

    public List<Mission> activeMissions() {
        return missions.stream().filter(mission -> !mission.isFinished()).toList();
    }

    public Optional<Mission> missionFor(Incident incident) {
        return missions.stream().filter(mission -> mission.incident() == incident).reduce((first, second) -> second);
    }

    // ---- planning and launching ----------------------------------------------------------------

    /**
     * Asks the planner what to send and compiles the answer into commands. The incident moves from
     * VERIFIED to PLANNED; nothing is queued or moved yet, so the operator can look at the plan first.
     *
     * @throws MissionException if the incident is not verified
     * @throws PlanningException if no plan can be made (for example, no drone is ready)
     */
    public MissionPlan plan(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        if (incident.state() != IncidentState.VERIFIED) {
            throw new MissionException("'" + incident.title() + "' can only be planned when it is VERIFIED, but it is " + incident.state());
        }
        ResponseDecision decision = planner.decide(PlanningContext.of(incident, engine.world().fleet()));
        MissionPlan plan = compiler.compile(incident, decision, engine.world().fleet());
        incident.plan();
        try {
            save(incident);
        } catch (RuntimeException e) {
            incident.cancel(); // PLANNED -> VERIFIED, so the in-memory state matches what was stored
            throw e;
        }
        return plan;
    }

    /** How many drones and helicopters are ready to be sent right now: the limits for what an operator may ask for. */
    public PlanningContext availability(Incident incident) {
        return PlanningContext.of(Objects.requireNonNull(incident, "incident"), engine.world().fleet());
    }

    /**
     * The operator changes a plan that is waiting for approval. The planner's decision is edited and
     * compiled again, so the new plan goes through the same checks as the original: the drones must
     * reach the area and get home with their battery reserve, and the rover must be able to get there.
     * The incident stays PLANNED and nothing is queued; the original plan is not touched, so the
     * operator can always go back to it.
     *
     * @param plan the plan to start from (normally the planner's own)
     * @throws MissionException if the incident is no longer PLANNED
     * @throws PlanningException if the change cannot be carried out (not enough ready drones or
     *     helicopters, or the batteries cannot cover the area)
     */
    public MissionPlan revise(MissionPlan plan, int drones, SearchStrategy strategy, int helicopters) {
        return revise(plan, drones, strategy, helicopters, plan.decision().robodogs());
    }

    /** Like {@link #revise(MissionPlan, int, SearchStrategy, int)}, and the robodog count can be changed too (0 is allowed). */
    public MissionPlan revise(MissionPlan plan, int drones, SearchStrategy strategy, int helicopters, int robodogs) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(strategy, "strategy");
        Incident incident = plan.incident();
        if (incident.state() != IncidentState.PLANNED) {
            throw new MissionException("'" + incident.title() + "' has no plan waiting for approval (it is " + incident.state() + ")");
        }
        if (drones < 1) {
            throw new PlanningException("At least one drone is needed");
        }
        if (helicopters < 1) {
            throw new PlanningException("At least one helicopter is needed so people who are found can be picked up");
        }
        int readyHelicopters = availability(incident).availableHelicopters();
        if (helicopters > readyHelicopters) {
            throw new PlanningException("Only " + readyHelicopters + " helicopter(s) are ready; " + helicopters + " were requested");
        }
        if (robodogs < 0) {
            throw new PlanningException("The number of robodogs must not be negative");
        }
        int readyRobodogs = availability(incident).availableRobodogs();
        if (robodogs > readyRobodogs) {
            throw new PlanningException("Only " + readyRobodogs + " robodog(s) are ready; " + robodogs + " were requested");
        }
        return compiler.compile(incident, plan.decision().edited(drones, strategy, helicopters, robodogs), engine.world().fleet());
    }

    /** The operator turns a plan down: the incident goes back to VERIFIED and can be planned again. */
    public void cancelPlan(MissionPlan plan) {
        Incident incident = Objects.requireNonNull(plan, "plan").incident();
        if (incident.state() != IncidentState.PLANNED) {
            throw new MissionException("'" + incident.title() + "' has no plan waiting for approval (it is " + incident.state() + ")");
        }
        incident.cancel();
        save(incident);
    }

    /**
     * Approves a plan: puts the incident area in the world (if it is not there yet), queues the
     * plan's commands, reserves helicopters for the pick-ups and starts the mission. The incident
     * moves from PLANNED to IN_PROGRESS.
     *
     * @throws MissionException if the plan is out of date: the incident is not PLANNED, a unit is
     *     already committed to another mission or is no longer idle, or no helicopter is free
     */
    public Mission launch(MissionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Incident incident = plan.incident();
        if (incident.state() != IncidentState.PLANNED) {
            throw new MissionException("'" + incident.title() + "' cannot be launched while it is " + incident.state() + "; plan it first");
        }
        checkUnitsFree(plan);
        int wanted = plan.decision().helicopters();
        if (wanted == 0) {
            throw new MissionException("The plan for '" + incident.title() + "' assigns no helicopter, so nobody who is found could be picked up");
        }
        List<Helicopter> team = dispatcher.freeHelicopters(engine.world().fleet(), plan.staging(), wanted);
        if (team.isEmpty()) {
            throw new MissionException("No helicopter is free to pick up survivors at '" + incident.title() + "'");
        }

        IncidentZone zone = engine.world().zoneFor(incident).orElseGet(() -> engine.addIncident(incident, spawner));
        incident.start();
        try {
            save(incident);
        } catch (RuntimeException e) {
            incident.cancel(); // IN_PROGRESS -> VERIFIED; nothing was queued yet
            throw e;
        }
        queue.enqueueAll(plan.commands());
        dispatcher.watch(zone, team);

        Mission mission = new Mission(plan, zone, team, engine.currentTick(), engine.elapsedSeconds());
        if (team.size() < wanted) {
            mission.notes().add("Only " + team.size() + " of the " + wanted + " planned helicopters were free.");
        }
        missions.add(mission);
        return mission;
    }

    /** Plans and launches in one step. */
    public Mission start(Incident incident) {
        return launch(plan(incident));
    }

    // ---- stopping ------------------------------------------------------------------------------

    /**
     * Emergency stop for one mission. Its rover and drones are cancelled and held where they are,
     * pick-ups still on the way are cancelled, and then everything is brought home: drones fly back
     * to the rover and the rover drives to its depot, while helicopters deliver whoever is on board.
     * The mission ends as ABORTED once that is done and the incident returns to VERIFIED.
     * Other missions are not affected. Does nothing if the mission is finished or already aborting.
     */
    public void abort(Mission mission, String reason) {
        Objects.requireNonNull(mission, "mission");
        if (mission.isFinished() || mission.isAbortRequested()) {
            return;
        }
        mission.requestAbort(reason);
        IncidentZone zone = mission.zone();
        String why = "mission aborted: " + mission.abortReason().orElse("stopped");
        long tick = engine.currentTick();

        dispatcher.stop(zone);
        if (mission.phase() != Mission.Phase.SEARCHING) {
            // Already on the way home: let it get there, just stop picking anyone new up.
            queue.cancelWhere(command -> dispatcher.isPickUpFor(command, zone), why, tick);
            return;
        }

        Carrier carrier = mission.plan().carrier();
        Set<Unit> units = new HashSet<>(carrier.units());
        queue.cancelWhere(command -> units.contains(command.unit()) || dispatcher.isPickUpFor(command, zone), why, tick);
        engine.hold(carrier);

        List<MissionCommand> recovery = new ArrayList<>();
        for (Unit carried : carrier.units()) {
            if ((carried instanceof Drone || carried instanceof Robodog) && carried.state() != UnitState.IDLE) {
                ReturnToBaseCommand back = new ReturnToBaseCommand(carried, carrier.position());
                queue.enqueue(back);
                recovery.add(back);
            }
        }
        mission.replaceSearchCommands(recovery);
    }

    /** Aborts every running mission. */
    public void abortAll(String reason) {
        activeMissions().forEach(mission -> abort(mission, reason));
    }

    // ---- running -------------------------------------------------------------------------------

    /**
     * Ticks until every mission has finished and every queued command is done (so the units are home).
     *
     * @return the number of ticks that were run
     */
    public long runUntilSettled(long maxTicks) {
        return engine.runUntil(() -> activeMissions().isEmpty() && queue.isIdle(), maxTicks);
    }

    // ---- per-tick work -------------------------------------------------------------------------

    private void onTick(SimulationEngine ignored) {
        dispatcher.dispatch();
        for (Mission mission : activeMissions()) {
            advance(mission);
        }
    }

    private void advance(Mission mission) {
        switch (mission.phase()) {
            case SEARCHING -> {
                if (mission.searchCommands().stream().allMatch(MissionCommand::isFinished)) {
                    // The drones are docked on the rover (or could not be saved): bring everything home.
                    ReturnToBaseCommand home = new ReturnToBaseCommand(mission.plan().carrier(), mission.plan().depot());
                    queue.enqueue(home);
                    mission.beginReturning(home);
                }
            }
            case RETURNING -> {
                if (mission.homeCommand().isFinished() && dispatcher.outstanding(mission.zone()) == 0) {
                    finish(mission);
                }
            }
            case FINISHED -> {
                // not reached: finished missions are not advanced
            }
        }
    }

    private boolean isSearching(IncidentZone zone) {
        return missions.stream().anyMatch(mission -> mission.zone() == zone
                && !mission.isFinished()
                && !mission.isAbortRequested()
                && mission.phase() == Mission.Phase.SEARCHING);
    }

    private void finish(Mission mission) {
        IncidentZone zone = mission.zone();
        Incident incident = mission.incident();
        boolean everyoneRescued = zone.rescuedCount() == zone.totalCount();
        MissionStatus status = mission.isAbortRequested()
                ? MissionStatus.ABORTED
                : everyoneRescued ? MissionStatus.COMPLETED : MissionStatus.INCOMPLETE;

        List<String> notes = new ArrayList<>(mission.notes());
        mission.abortReason().ifPresent(reason -> notes.add("Aborted: " + reason + "."));
        for (MissionCommand command : mission.plan().commands()) {
            if (command.status() == CommandStatus.FAILED) {
                notes.add(command.describe() + " failed: " + command.detail().orElse("no reason given"));
            }
        }
        for (Unit unit : mission.plan().carrier().units()) {
            if (unit.state() != UnitState.IDLE) {
                notes.add(String.format(Locale.ROOT, "%s did not get home (%s, %.0f%% battery).", unit.id(), unit.state(), unit.batteryPercent()));
            }
        }
        for (Helicopter helicopter : mission.helicopters()) {
            if (helicopter.passengerCount() > 0) {
                notes.add(helicopter.id() + " is stuck with " + helicopter.passengerCount() + " passenger(s) on board.");
            }
        }
        if (zone.foundCount() > 0) {
            notes.add(zone.foundCount() + " people were found but no helicopter could reach them safely.");
        }
        if (zone.hiddenCount() > 0 && !mission.isAbortRequested()) {
            notes.add(zone.hiddenCount() + " people were never found.");
        }

        try {
            if (status == MissionStatus.COMPLETED) {
                incident.complete();
            } else {
                incident.cancel(); // back to VERIFIED: it can be planned again for the people who are left
            }
            save(incident);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not update " + incident, e);
            notes.add("The incident record could not be updated: " + e.getMessage());
        }

        MissionReport report = new MissionReport(
                incident,
                status,
                zone.totalCount(),
                zone.rescuedCount(),
                zone.foundCount(),
                zone.hiddenCount(),
                engine.currentTick() - mission.startedAtTick(),
                engine.elapsedSeconds() - mission.startedAtSeconds(),
                notes);
        mission.finish(status, report);
        dispatcher.release(zone);

        for (MissionListener listener : listeners) {
            try {
                listener.onMissionFinished(mission, report);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Mission listener failed on " + mission, e);
            }
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** Rejects a plan whose rover or drones are committed elsewhere or are no longer idle. */
    private void checkUnitsFree(MissionPlan plan) {
        Set<Unit> committed = new HashSet<>();
        for (Mission mission : activeMissions()) {
            committed.addAll(mission.plan().carrier().units());
        }
        List<Unit> needed = new ArrayList<>();
        needed.add(plan.carrier());
        needed.addAll(plan.drones());
        needed.addAll(plan.robodogs());
        for (Unit unit : needed) {
            if (committed.contains(unit)) {
                throw new MissionException(unit.id() + " is already on another mission");
            }
            if (!unit.isAvailable()) {
                throw new MissionException(unit.id() + " is " + unit.state() + " and not free for this plan; plan the incident again");
            }
        }
    }

    private void save(Incident incident) {
        if (incident.id().isPresent()) {
            repository.update(incident);
        }
    }
}
