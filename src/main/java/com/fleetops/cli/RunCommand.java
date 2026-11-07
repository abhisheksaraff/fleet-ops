package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.app.FleetLayout;
import com.fleetops.app.SimulationRig;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.mission.Mission;
import com.fleetops.mission.MissionControl;
import com.fleetops.mission.MissionReport;
import com.fleetops.mission.MissionStatus;
import com.fleetops.plan.MissionPlan;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code fleetops run <id>}: the AI plans a response, the operator approves it, and the simulation
 * plays the mission out (rover, drones, helicopters) with a report at the end.
 */
@Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "Plan and simulate the rescue mission for one verified incident.")
final class RunCommand extends AbstractAppCommand {

    private final BufferedReader input;

    @Parameters(index = "0", paramLabel = "ID", description = "Incident id, as listed by 'fleetops incidents'.")
    long id;

    @Option(names = "--rovers", defaultValue = "1", description = "Rovers in the fleet (default: ${DEFAULT-VALUE}).")
    int rovers;

    @Option(names = "--drones", defaultValue = "6", description = "Drones on each rover (default: ${DEFAULT-VALUE}).")
    int drones;

    @Option(names = "--helicopters", defaultValue = "2", description = "Helicopters in the fleet (default: ${DEFAULT-VALUE}).")
    int helicopters;

    @Option(names = "--depot-km", defaultValue = "30", description = "How far south of the incident the fleet starts (default: ${DEFAULT-VALUE} km).")
    double depotKm;

    @Option(names = "--seed", defaultValue = "42", description = "Random seed for where the survivors are (default: ${DEFAULT-VALUE}).")
    long seed;

    @Option(names = "--max-ticks", defaultValue = "20000", description = "Give up after this many simulated seconds (default: ${DEFAULT-VALUE}).")
    long maxTicks;

    @Option(names = "--abort-after", paramLabel = "SECONDS", description = "Emergency-stop the mission after this many simulated seconds.")
    Long abortAfter;

    @Option(names = {"-n", "--dry-run"}, description = "Show the plan, then cancel it. Nothing is simulated.")
    boolean dryRun;

    @Option(names = {"-y", "--yes"}, description = "Do not ask for approval of the plan.")
    boolean yes;

    @Option(names = "--progress", description = "Print finds and rescues while the mission runs.")
    boolean progress;

    @Option(names = "--no-debrief", description = "Skip the AI-written debrief that follows the report.")
    boolean noDebrief;

    @Option(names = {"-v", "--verbose"}, description = "With --progress, also print every unit state change.")
    boolean verbose;

    RunCommand(Supplier<AppContext> contextSupplier, BufferedReader input) {
        super(contextSupplier);
        this.input = input;
    }

    @Override
    protected int execute(AppContext app, PrintWriter out, PrintWriter err) throws IOException {
        FleetLayout layout;
        try {
            layout = new FleetLayout(rovers, drones, helicopters);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return ExitCodes.USAGE;
        }
        if (depotKm < 0 || depotKm > 500 || Double.isNaN(depotKm)) {
            err.println("--depot-km must be between 0 and 500: " + depotKm);
            return ExitCodes.USAGE;
        }
        if (maxTicks < 1) {
            err.println("--max-ticks must be at least 1: " + maxTicks);
            return ExitCodes.USAGE;
        }

        Optional<Incident> found = app.repository().findById(id);
        if (found.isEmpty()) {
            err.println("No incident with id " + id + ".");
            return ExitCodes.NOT_FOUND;
        }
        Incident incident = found.get();
        if (incident.state() != IncidentState.VERIFIED) {
            err.println("Incident #" + id + " is " + incident.state() + "; only VERIFIED incidents can be run.");
            return ExitCodes.FAILED;
        }

        GeoPoint depot = incident.area().center().offsetByKm(-depotKm, 0);
        SimulationRig rig = SimulationRig.create(app, layout, depot, seed);
        MissionControl control = rig.control();

        MissionPlan plan = control.plan(incident);
        out.println(plan.describe());

        if (dryRun) {
            control.cancelPlan(plan);
            out.println("Dry run: plan cancelled, nothing was simulated.");
            return ExitCodes.OK;
        }
        if (!yes && !CliFormat.confirm(input, out, "Launch this mission?")) {
            control.cancelPlan(plan);
            out.println("Plan not approved; the incident stays VERIFIED.");
            return ExitCodes.OK;
        }

        if (progress) {
            rig.engine().addListener(new MissionProgress(rig.engine(), out, verbose));
        }
        Mission mission;
        try {
            mission = control.launch(plan);
        } catch (RuntimeException e) {
            control.cancelPlan(plan);
            throw e;
        }
        out.println("Mission launched with " + mission.helicopters().size() + " helicopter(s).");

        runToEnd(rig, mission);

        MissionReport report = mission.report().orElseThrow(
                () -> new IllegalStateException("The mission did not finish within " + maxTicks + " seconds."));
        out.println(report.describe());
        if (!noDebrief) {
            out.println();
            out.println(app.debriefer().debrief(report).describe());
        }
        return report.status() == MissionStatus.COMPLETED ? ExitCodes.OK : ExitCodes.FAILED;
    }

    private void runToEnd(SimulationRig rig, Mission mission) {
        MissionControl control = rig.control();
        try {
            if (abortAfter != null) {
                long first = Math.min(abortAfter, maxTicks);
                control.runUntilSettled(first);
                if (!mission.isFinished()) {
                    control.abort(mission, "stopped by the operator after " + abortAfter + " s");
                }
            }
            if (!mission.isFinished() || !control.queue().isIdle()) {
                control.runUntilSettled(maxTicks);
            }
        } finally {
            if (!mission.isFinished()) {
                // Do not leave the incident IN_PROGRESS in the database if the run was cut short.
                control.abort(mission, "the run ended before the mission finished");
                control.runUntilSettled(maxTicks);
            }
        }
    }
}
