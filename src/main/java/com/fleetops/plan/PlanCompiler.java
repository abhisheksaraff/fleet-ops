package com.fleetops.plan;

import com.fleetops.command.AwaitCommand;
import com.fleetops.command.FlyToCommand;
import com.fleetops.command.MissionCommand;
import com.fleetops.command.ReturnToBaseCommand;
import com.fleetops.command.SweepAreaCommand;
import com.fleetops.domain.fleet.Carrier;
import com.fleetops.domain.fleet.Drone;
import com.fleetops.domain.fleet.Fleet;
import com.fleetops.domain.fleet.Robodog;
import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.safety.SafetyPolicy;
import com.fleetops.safety.SafetyReport;
import com.fleetops.safety.VerifiedCommand;
import com.fleetops.strategy.RadiusSweepStrategy;
import com.fleetops.strategy.SearchStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turns a {@link ResponseDecision} into concrete commands for the units that are actually ready:
 *
 * <ol>
 *   <li>the rover drives to the middle of the incident square's south edge;
 *   <li>once it has arrived (an {@link AwaitCommand} in each drone's lane), every chosen drone
 *       sweeps its share of the square and then flies back to the rover;
 *   <li>the chosen robodogs are dropped off along the south edge, each walks circles outward from
 *       its drop-off point, and then walks back to the rover.
 * </ol>
 *
 * <p>Every movement is wrapped in a {@link VerifiedCommand} so the safety rules are applied at the
 * moment each one starts. The compiler also checks battery use ahead of time: if the chosen number
 * of drones could not finish the search and get home, it adds drones (up to what is ready) and
 * says so in the plan's notes; if even that is not enough, it refuses the plan.
 */
public final class PlanCompiler {

    /** Extra battery (percentage points) the ahead-of-time check keeps in hand beyond the safety reserve. */
    static final double FEASIBILITY_MARGIN_PERCENT = 1.0;

    /** The most a robodog walks away from its drop-off point (km). */
    static final double MAX_ROBODOG_RADIUS_KM = 0.4;

    private static final SearchStrategy RADIUS_SWEEP = new RadiusSweepStrategy();

    private final Function<GeoPoint, SafetyPolicy> policies;

    /** Uses {@link SafetyPolicy#standard} for every movement. */
    public PlanCompiler() {
        this(SafetyPolicy::standard);
    }

    /** @param policies builds the safety policy for a given base point */
    public PlanCompiler(Function<GeoPoint, SafetyPolicy> policies) {
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public MissionPlan compile(Incident incident, ResponseDecision decision, Fleet fleet) {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(fleet, "fleet");

        Carrier carrier = fleet.units().stream()
                .filter(u -> u instanceof Carrier c && c.isAvailable() && !c.isDepleted() && !PlanningContext.readyDrones(c).isEmpty())
                .map(Carrier.class::cast)
                .findFirst()
                .orElseThrow(() -> new PlanningException("No rover with ready drones is available"));
        List<Drone> ready = PlanningContext.readyDrones(carrier).stream()
                .sorted(Comparator.comparingDouble(Drone::batteryPercent).reversed().thenComparing(Drone::id))
                .toList();

        GeoArea area = incident.area();
        GeoPoint depot = carrier.position();
        GeoPoint staging = area.center().offsetByKm(-area.radiusKm(), 0);
        SearchStrategy strategy = decision.strategy();

        List<String> notes = new ArrayList<>();
        int grounded = carrier.drones().size() - ready.size();
        if (grounded > 0) {
            notes.add(grounded + " drone(s) stay docked: busy or below " + (int) PlanningContext.MIN_LAUNCH_BATTERY_PERCENT + "% battery.");
        }
        int wanted = Math.min(decision.drones(), AreaGrid.MAX_CELLS);
        if (wanted < decision.drones()) {
            notes.add("The square is cut into at most " + AreaGrid.MAX_CELLS + " parts, so " + decision.drones() + " drones were reduced to " + wanted + ".");
        }
        if (ready.size() < wanted) {
            notes.add("Only " + ready.size() + " drone(s) are ready on " + carrier.id() + "; " + wanted + " were requested.");
            wanted = ready.size();
        }
        int maxDrones = Math.min(ready.size(), AreaGrid.MAX_CELLS);

        int drones = wanted;
        Map<Drone, List<GeoArea>> sweeps = null;
        String lastProblem = "";
        for (; drones <= maxDrones; drones++) {
            Map<Drone, List<GeoArea>> candidate = assignments(area, ready, drones);
            Optional<String> problem = infeasible(candidate, strategy, staging);
            if (problem.isEmpty()) {
                sweeps = candidate;
                break;
            }
            lastProblem = problem.get();
        }
        if (sweeps == null) {
            throw new PlanningException("The incident area is too large for the ready drones' batteries: " + lastProblem);
        }
        if (drones > wanted) {
            notes.add("Raised the number of drones from " + wanted + " to " + drones + " so each can finish its share and get home.");
        }

        Map<Robodog, GeoArea> dogAreas = robodogAreas(area, staging, PlanningContext.readyRobodogs(carrier), decision.robodogs(), notes);

        SafetyPolicy roverPolicy = policies.apply(depot);
        SafetyPolicy dronePolicy = policies.apply(staging);
        FlyToCommand driveThere = new FlyToCommand(carrier, staging);
        SafetyReport check = roverPolicy.evaluate(driveThere, depot);
        if (!check.isSafe()) {
            // Refuse now rather than launch a mission whose first command the safety gate will stop.
            throw new PlanningException(String.format(Locale.ROOT,
                    "%s cannot reach '%s' (%.0f km from its base): %s",
                    carrier.id(), incident.title(), depot.distanceKmTo(staging), check.summary()));
        }
        List<MissionCommand> commands = new ArrayList<>();
        VerifiedCommand drive = roverPolicy.verify(driveThere);
        commands.add(drive);
        for (Map.Entry<Drone, List<GeoArea>> entry : sweeps.entrySet()) {
            Drone drone = entry.getKey();
            commands.add(new AwaitCommand(drone, drive));
            for (GeoArea cell : entry.getValue()) {
                commands.add(dronePolicy.verify(new SweepAreaCommand(drone, cell, strategy)));
            }
            commands.add(dronePolicy.verify(new ReturnToBaseCommand(drone, staging)));
        }
        for (Map.Entry<Robodog, GeoArea> entry : dogAreas.entrySet()) {
            Robodog dog = entry.getKey();
            commands.add(new AwaitCommand(dog, drive));
            commands.add(dronePolicy.verify(new FlyToCommand(dog, entry.getValue().center())));
            commands.add(dronePolicy.verify(new SweepAreaCommand(dog, entry.getValue(), RADIUS_SWEEP)));
            commands.add(dronePolicy.verify(new ReturnToBaseCommand(dog, staging)));
        }
        return new MissionPlan(incident, decision.withDrones(drones).withRobodogs(dogAreas.size()), carrier, depot, staging,
                sweeps, dogAreas, commands, notes);
    }

    /**
     * Chooses the drop-off points of the robodogs: evenly along the south edge of the square, just
     * far enough inside that each circle stays within the square. A robodog that could not walk its
     * circle and get back with its battery reserve is left on the rover, with a note.
     */
    private static Map<Robodog, GeoArea> robodogAreas(
            GeoArea area, GeoPoint staging, List<Robodog> ready, int requested, List<String> notes) {
        Map<Robodog, GeoArea> result = new LinkedHashMap<>();
        if (requested <= 0) {
            return result;
        }
        int wanted = requested;
        if (ready.size() < wanted) {
            notes.add("Only " + ready.size() + " robodog(s) are ready on the rover; " + requested + " were requested.");
            wanted = ready.size();
        }
        if (wanted == 0) {
            return result;
        }
        List<Robodog> chosen = ready.stream()
                .sorted(Comparator.comparingDouble(Robodog::batteryPercent).reversed().thenComparing(Robodog::id))
                .limit(wanted)
                .toList();
        double half = area.radiusKm();
        double radius = Math.min(MAX_ROBODOG_RADIUS_KM, half / wanted);
        double reserve = SafetyPolicy.DEFAULT_RESERVE_PERCENT + FEASIBILITY_MARGIN_PERCENT;
        for (int i = 0; i < chosen.size(); i++) {
            Robodog dog = chosen.get(i);
            double east = -half + half * (2 * i + 1) / wanted;
            GeoPoint dropOff = area.center().offsetByKm(-half + radius, east);
            GeoArea circle = new GeoArea(dropOff, radius);
            double walk = new FlyToCommand(dog, dropOff).requiredBatteryPercent(staging);
            SweepAreaCommand sweep = new SweepAreaCommand(dog, circle, RADIUS_SWEEP);
            walk += sweep.requiredBatteryPercent(dropOff);
            double home = sweep.endPosition(dropOff).distanceKmTo(staging) * dog.batteryDrainPercentPerKm();
            if (walk + home + reserve > dog.batteryPercent()) {
                notes.add(dog.id() + " stays on the rover: its battery could not cover the walk, a circle and the way back.");
                continue;
            }
            result.put(dog, circle);
        }
        return result;
    }

    private static Map<Drone, List<GeoArea>> assignments(GeoArea area, List<Drone> ready, int drones) {
        List<GeoArea> cells = AreaGrid.split(area, AreaGrid.cellsPerSide(drones));
        List<List<GeoArea>> shares = AreaGrid.assign(cells, drones);
        Map<Drone, List<GeoArea>> result = new LinkedHashMap<>();
        for (int i = 0; i < shares.size(); i++) {
            result.put(ready.get(i), shares.get(i));
        }
        return result;
    }

    /**
     * Follows each drone's route from the staging point and checks that after every sweep it would
     * still have enough battery to fly home and keep the safety reserve (the same test the safety
     * gate applies later).
     *
     * @return the first problem found, or empty if every drone can do its share
     */
    private static Optional<String> infeasible(Map<Drone, List<GeoArea>> sweeps, SearchStrategy strategy, GeoPoint staging) {
        double reserve = SafetyPolicy.DEFAULT_RESERVE_PERCENT + FEASIBILITY_MARGIN_PERCENT;
        for (Map.Entry<Drone, List<GeoArea>> entry : sweeps.entrySet()) {
            Drone drone = entry.getKey();
            GeoPoint position = staging;
            double used = 0.0;
            for (GeoArea cell : entry.getValue()) {
                SweepAreaCommand sweep = new SweepAreaCommand(drone, cell, strategy);
                used += sweep.requiredBatteryPercent(position);
                position = sweep.endPosition(position);
                double home = position.distanceKmTo(staging) * drone.batteryDrainPercentPerKm();
                if (used + home + reserve > drone.batteryPercent()) {
                    return Optional.of(String.format(Locale.ROOT, "%s with %.0f%% battery would need %.0f%% for its %d square(s) plus the way home and the reserve",
                            drone.id(), drone.batteryPercent(), used + home + reserve, entry.getValue().size()));
                }
            }
        }
        return Optional.empty();
    }
}
