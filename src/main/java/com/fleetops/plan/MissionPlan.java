package com.fleetops.plan;

import com.fleetops.command.MissionCommand;
import com.fleetops.command.Routes;
import com.fleetops.domain.fleet.Carrier;
import com.fleetops.domain.fleet.Drone;
import com.fleetops.domain.fleet.Robodog;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A response plan turned into queue-ready commands. Nothing has been run yet: the plan can be
 * shown to an operator first and then handed to a {@code CommandQueue}.
 *
 * @param incident the incident being answered
 * @param decision what was decided (the drone count here is the one actually used)
 * @param carrier the rover that carries the drones
 * @param depot where the rover starts and finally returns to
 * @param staging where the rover parks to launch the drones: the middle of the incident square's south edge
 * @param sweeps the squares each drone searches, in order
 * @param robodogSweeps the small square each robodog searches around its drop-off point
 * @param commands every command, ready to enqueue
 * @param notes things the compiler changed or noticed, for the operator
 * @param minDrones the fewest drones that can search this area on one battery each (with this search pattern)
 * @param maxDrones the most drones that can be used: those that are ready, up to the number of parts the square is cut into
 */
public record MissionPlan(
        Incident incident,
        ResponseDecision decision,
        Carrier carrier,
        GeoPoint depot,
        GeoPoint staging,
        Map<Drone, List<GeoArea>> sweeps,
        Map<Robodog, GeoArea> robodogSweeps,
        List<MissionCommand> commands,
        List<String> notes,
        int minDrones,
        int maxDrones) {

    public MissionPlan {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(depot, "depot");
        Objects.requireNonNull(staging, "staging");
        sweeps = Map.copyOf(sweeps);
        robodogSweeps = Collections.unmodifiableMap(new LinkedHashMap<>(robodogSweeps));
        commands = List.copyOf(commands);
        notes = List.copyOf(notes);
    }

    /** The drones that take part, in the order they were chosen. */
    public List<Drone> drones() {
        return commands.stream().map(MissionCommand::unit).filter(Drone.class::isInstance).map(Drone.class::cast).distinct().toList();
    }

    /** The robodogs that take part, in the order they were chosen. */
    public List<Robodog> robodogs() {
        return List.copyOf(robodogSweeps.keySet());
    }

    /** The commands one unit will carry out, in order. */
    public List<MissionCommand> commandsFor(Unit unit) {
        return commands.stream().filter(c -> c.unit() == unit).toList();
    }

    /** A multi-line description for the console or the report. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Plan for %s (%s, %s) by %s%n", incident.title(), incident.type().keyword(), incident.severity(), decision.plannedBy()));
        text.append("  Why: ").append(decision.rationale()).append('\n');
        text.append(String.format(Locale.ROOT, "  %s drives %.1f km to %s, the south edge of the %.1f km square%n",
                carrier.id(), depot.distanceKmTo(staging), Routes.format(staging), incident.area().sideKm()));
        for (Map.Entry<Drone, List<GeoArea>> entry : sweeps.entrySet()) {
            text.append(String.format(Locale.ROOT, "  %s sweeps %d square(s) with %s%n", entry.getKey().id(), entry.getValue().size(), decision.strategy().name()));
        }
        for (Map.Entry<Robodog, GeoArea> entry : robodogSweeps.entrySet()) {
            text.append(String.format(Locale.ROOT, "  %s searches a circle of %.2f km around %s%n",
                    entry.getKey().id(), entry.getValue().radiusKm(), Routes.format(entry.getValue().center())));
        }
        text.append(String.format(Locale.ROOT, "  %d helicopter(s) on standby for pick-ups%n", decision.helicopters()));
        notes.forEach(note -> text.append("  Note: ").append(note).append('\n'));
        return text.toString().stripTrailing();
    }
}
