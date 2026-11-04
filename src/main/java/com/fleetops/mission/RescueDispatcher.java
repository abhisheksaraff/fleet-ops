package com.fleetops.mission;

import com.fleetops.command.CommandListener;
import com.fleetops.command.CommandQueue;
import com.fleetops.command.CommandResult;
import com.fleetops.command.CommandStatus;
import com.fleetops.command.MissionCommand;
import com.fleetops.command.PickUpCommand;
import com.fleetops.command.ReturnToBaseCommand;
import com.fleetops.domain.fleet.Fleet;
import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Survivor;
import com.fleetops.domain.incident.SurvivorStatus;
import com.fleetops.plan.PlanningContext;
import com.fleetops.safety.SafetyPolicy;
import com.fleetops.sim.IncidentZone;
import com.fleetops.sim.SimulationEvent;
import com.fleetops.sim.SimulationListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Sends helicopters to the people the drones find. It is an observer of the simulation: every
 * {@link SimulationEvent.SurvivorFound} puts the survivor on a waiting list, and on every tick
 * {@link #dispatch()} hands the waiting survivors to the helicopters of that area's team.
 *
 * <p>The rules, applied separately to each area's team:
 *
 * <ul>
 *   <li>A free helicopter with room is matched with the <em>nearest</em> waiting survivor it can
 *       reach safely. The safety policy is asked with the helicopter's real position and battery
 *       before anything is queued, so a pick-up that would strand a helicopter is never sent.
 *   <li>A full helicopter flies home to unload. So does one that cannot safely reach anyone
 *       (it recharges there and tries again).
 *   <li>While drones are still searching, a helicopter with passengers waits for more survivors;
 *       when the search is over it takes them home.
 *   <li>If nobody on the team could ever reach a waiting survivor (too far, or no helicopter left that
 *       can fly), the survivor is given up on instead of waiting forever.
 *   <li>A pick-up that fails is tried again once; after {@value #MAX_ATTEMPTS} failures the survivor is
 *       given up on.
 * </ul>
 *
 * <p>Return flights are never safety-checked: going home is always the best option left.
 */
public final class RescueDispatcher implements SimulationListener, CommandListener {

    /** How many failed pick-ups a survivor can have before the dispatcher gives up on them. */
    public static final int MAX_ATTEMPTS = 2;

    private final CommandQueue queue;
    private final Function<GeoPoint, SafetyPolicy> policies;
    private final Predicate<IncidentZone> searching;

    private final Map<IncidentZone, Team> teams = new LinkedHashMap<>();
    private final Map<Helicopter, GeoPoint> homes = new LinkedHashMap<>();
    private final Map<MissionCommand, Survivor> pickUps = new IdentityHashMap<>();
    private final Map<Survivor, Integer> failures = new IdentityHashMap<>();

    /**
     * @param queue where pick-up and return commands are queued
     * @param policies builds the safety policy for a helicopter's home base
     * @param searching tells whether drones are still searching an area (helicopters then wait for them)
     */
    public RescueDispatcher(CommandQueue queue, Function<GeoPoint, SafetyPolicy> policies, Predicate<IncidentZone> searching) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.searching = Objects.requireNonNull(searching, "searching");
    }

    // ---- teams ---------------------------------------------------------------------------------

    /**
     * Starts serving an area with a team of helicopters. Each helicopter's current position is
     * remembered as its home the first time it is seen, and survivors who are already found join the
     * waiting list.
     *
     * @throws IllegalArgumentException if the team is empty, the area is already served, or a
     *     helicopter already serves another area
     */
    public void watch(IncidentZone zone, Collection<Helicopter> helicopters) {
        Objects.requireNonNull(zone, "zone");
        if (helicopters.isEmpty()) {
            throw new IllegalArgumentException("A team needs at least one helicopter");
        }
        if (teams.containsKey(zone)) {
            throw new IllegalArgumentException(zone + " is already being served");
        }
        for (Helicopter helicopter : helicopters) {
            if (isServing(helicopter)) {
                throw new IllegalArgumentException(helicopter.id() + " already serves another area");
            }
        }
        Team team = new Team(zone, List.copyOf(helicopters));
        team.helicopters.forEach(h -> homes.putIfAbsent(h, h.position()));
        teams.put(zone, team);
        for (Survivor survivor : zone.survivors()) {
            addWaiting(team, survivor);
        }
    }

    /**
     * Stops taking new survivors for an area (used when its mission is aborted). The team stays, so
     * passengers already on board are still brought home.
     */
    public void stop(IncidentZone zone) {
        Team team = teams.get(zone);
        if (team != null) {
            team.stopped = true;
            team.waiting.clear();
        }
    }

    /** Ends service for an area; team helicopters that are away from base are sent home. */
    public void release(IncidentZone zone) {
        Team team = teams.remove(zone);
        if (team != null) {
            for (Helicopter helicopter : team.helicopters) {
                if (isFree(helicopter) && !isHomeAndEmpty(helicopter)) {
                    sendHome(helicopter);
                }
            }
        }
    }

    /** True if the helicopter is on the team of an area that is still being served. */
    public boolean isServing(Helicopter helicopter) {
        return teams.values().stream().anyMatch(team -> team.helicopters.contains(helicopter));
    }

    public List<Helicopter> teamOf(IncidentZone zone) {
        Team team = teams.get(zone);
        return team == null ? List.of() : team.helicopters;
    }

    /**
     * The idle, charged helicopters that serve no area, nearest to {@code near} first, at most
     * {@code count} of them.
     */
    public List<Helicopter> freeHelicopters(Fleet fleet, GeoPoint near, int count) {
        return fleet.units().stream()
                .filter(Helicopter.class::isInstance)
                .map(Helicopter.class::cast)
                .filter(h -> !isServing(h) && PlanningContext.isReady(h) && queue.pendingFor(h).isEmpty())
                .sorted(Comparator.comparingDouble((Helicopter h) -> h.position().distanceKmTo(near)).thenComparing(Helicopter::id))
                .limit(Math.max(0, count))
                .toList();
    }

    // ---- what is going on ----------------------------------------------------------------------

    /** Found survivors of an area who have no helicopter on the way yet. */
    public List<Survivor> waiting(IncidentZone zone) {
        Team team = teams.get(zone);
        return team == null ? List.of() : List.copyOf(team.waiting);
    }

    /** Survivors the dispatcher gave up on. */
    public List<Survivor> abandoned(IncidentZone zone) {
        Team team = teams.get(zone);
        return team == null ? List.of() : List.copyOf(team.abandoned);
    }

    /**
     * People of an area who are found and not yet safe: waiting for a helicopter, being flown to, or
     * on board a helicopter that has not reached base. A helicopter that can no longer move does not
     * count (nothing more can be done for its passengers).
     */
    public int outstanding(IncidentZone zone) {
        Team team = teams.get(zone);
        if (team == null) {
            return 0;
        }
        int count = 0;
        for (Survivor survivor : zone.survivors()) {
            if (team.waiting.contains(survivor) || pickUps.containsValue(survivor) || isAboard(team, survivor)) {
                count++;
            }
        }
        return count;
    }

    /** True if the command is a pick-up of one of the area's survivors. */
    public boolean isPickUpFor(MissionCommand command, IncidentZone zone) {
        Survivor survivor = pickUps.get(command);
        return survivor != null && zone.survivors().contains(survivor);
    }

    // ---- observers -----------------------------------------------------------------------------

    @Override
    public void onEvent(SimulationEvent event) {
        if (event instanceof SimulationEvent.SurvivorFound found) {
            Team team = teamServing(found.survivor());
            if (team != null) {
                addWaiting(team, found.survivor());
            }
        }
    }

    @Override
    public void onCommandFinished(CommandResult result) {
        Survivor survivor = pickUps.remove(result.command());
        if (survivor == null || result.succeeded() || survivor.status() != SurvivorStatus.FOUND) {
            return;
        }
        Team team = teamServing(survivor);
        if (team == null) {
            return;
        }
        if (result.status() == CommandStatus.FAILED) {
            int failed = failures.merge(survivor, 1, Integer::sum);
            if (failed >= MAX_ATTEMPTS) {
                team.abandoned.add(survivor);
                return;
            }
        }
        addWaiting(team, survivor);
    }

    // ---- dispatching ---------------------------------------------------------------------------

    /** Matches waiting survivors with free helicopters and sends helicopters home when needed. Call once per tick. */
    public void dispatch() {
        for (Team team : new ArrayList<>(teams.values())) {
            dispatch(team);
        }
    }

    private void dispatch(Team team) {
        team.waiting.removeIf(survivor -> survivor.status() != SurvivorStatus.FOUND);

        for (Helicopter helicopter : team.helicopters) {
            if (isFree(helicopter) && !helicopter.hasRoom()) {
                sendHome(helicopter);
            }
        }

        while (!team.waiting.isEmpty()) {
            Match best = bestMatch(team);
            if (best == null) {
                break;
            }
            team.waiting.remove(best.survivor());
            MissionCommand pickUp = policyFor(best.helicopter()).verify(new PickUpCommand(best.helicopter(), best.survivor()));
            pickUps.put(pickUp, best.survivor());
            queue.enqueue(pickUp);
        }

        if (!team.waiting.isEmpty()) {
            // Nobody could safely take them just now: helicopters away from base go home to recharge.
            for (Helicopter helicopter : team.helicopters) {
                if (isFree(helicopter) && !isHomeAndEmpty(helicopter)) {
                    sendHome(helicopter);
                }
            }
            boolean nobodyCanHelp = team.helicopters.stream()
                    .filter(this::canAct)
                    .allMatch(h -> isFree(h) && isHomeAndEmpty(h));
            if (nobodyCanHelp) {
                team.abandoned.addAll(team.waiting);
                team.waiting.clear();
            }
        } else if (team.stopped || !searching.test(team.zone)) {
            for (Helicopter helicopter : team.helicopters) {
                if (isFree(helicopter) && !isHomeAndEmpty(helicopter)) {
                    sendHome(helicopter);
                }
            }
        }
    }

    /** The closest (helicopter, survivor) pair for which the pick-up passes the safety policy. */
    private Match bestMatch(Team team) {
        Match best = null;
        for (Helicopter helicopter : team.helicopters) {
            if (!isFree(helicopter) || !helicopter.hasRoom()) {
                continue;
            }
            SafetyPolicy policy = policyFor(helicopter);
            for (Survivor survivor : team.waiting) {
                double distanceKm = helicopter.position().distanceKmTo(survivor.position());
                if (best != null && distanceKm >= best.distanceKm()) {
                    continue;
                }
                if (policy.evaluate(new PickUpCommand(helicopter, survivor), helicopter.position()).isSafe()) {
                    best = new Match(helicopter, survivor, distanceKm);
                }
            }
        }
        return best;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void addWaiting(Team team, Survivor survivor) {
        if (team.stopped
                || survivor.status() != SurvivorStatus.FOUND
                || team.abandoned.contains(survivor)
                || team.waiting.contains(survivor)
                || pickUps.containsValue(survivor)) {
            return;
        }
        team.waiting.add(survivor);
    }

    private Team teamServing(Survivor survivor) {
        for (Team team : teams.values()) {
            if (team.zone.survivors().contains(survivor)) {
                return team;
            }
        }
        return null;
    }

    private boolean isAboard(Team team, Survivor survivor) {
        for (Helicopter helicopter : team.helicopters) {
            if (canAct(helicopter) && helicopter.passengers().contains(survivor)) {
                return true;
            }
        }
        return false;
    }

    /** Has nothing queued, has battery, and is not stopped or already heading home. */
    private boolean isFree(Helicopter helicopter) {
        if (helicopter.isDepleted() || !queue.pendingFor(helicopter).isEmpty()) {
            return false;
        }
        return switch (helicopter.state()) {
            case IDLE, EN_ROUTE, OPERATING -> true;
            case RETURNING, HOLDING -> false;
        };
    }

    /** Free, or busy with queued commands: i.e. not stranded. */
    private boolean canAct(Helicopter helicopter) {
        return !queue.pendingFor(helicopter).isEmpty() || isFree(helicopter);
    }

    private boolean isHomeAndEmpty(Helicopter helicopter) {
        return helicopter.state() == UnitState.IDLE
                && helicopter.position().equals(homes.get(helicopter))
                && helicopter.passengerCount() == 0;
    }

    private void sendHome(Helicopter helicopter) {
        queue.enqueue(new ReturnToBaseCommand(helicopter, homes.get(helicopter)));
    }

    private SafetyPolicy policyFor(Helicopter helicopter) {
        return policies.apply(homes.get(helicopter));
    }

    private record Match(Helicopter helicopter, Survivor survivor, double distanceKm) {}

    /** The helicopters serving one area, and who is waiting for them. */
    private static final class Team {
        final IncidentZone zone;
        final List<Helicopter> helicopters;
        final List<Survivor> waiting = new ArrayList<>();
        final Set<Survivor> abandoned = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean stopped;

        Team(IncidentZone zone, List<Helicopter> helicopters) {
            this.zone = zone;
            this.helicopters = helicopters;
        }
    }
}
