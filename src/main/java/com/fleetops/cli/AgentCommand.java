package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.app.FleetLayout;
import com.fleetops.app.SimulationRig;
import com.fleetops.config.AppConfig;
import com.fleetops.config.LlmProviderType;
import com.fleetops.debrief.Debrief;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.mission.Mission;
import com.fleetops.mission.MissionControl;
import com.fleetops.mission.MissionReport;
import com.fleetops.intel.TaskReports;
import com.fleetops.mission.MissionStatus;
import com.fleetops.plan.MissionPlan;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code fleetops agent --input tasks.txt}: runs the whole agent on incident reports written in a
 * file or read from standard input. For every task the AI verifies the report, plans the response,
 * the operator approves, the simulation runs and the AI writes the debrief. The results can also be
 * written as JSON.
 */
@Command(
        name = "agent",
        mixinStandardHelpOptions = true,
        description = "Run the agent on incident reports from a file or standard input: verify, plan, approve, simulate, debrief.")
final class AgentCommand extends AbstractAppCommand {

    private final BufferedReader stdin;

    @Option(names = {"-i", "--input", "-input"}, required = true, paramLabel = "FILE",
            description = "File with incident reports in plain words, separated by a line of three dashes. Use - to read from standard input.")
    String input;

    @Option(names = "--llm", paramLabel = "NAME",
            description = "AI to use: gemini, ollama or mock. Default: LLM_PROVIDER from .env.")
    String llm;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE", description = "Also write the results to this file as JSON.")
    Path output;

    @Option(names = {"-y", "--yes"}, description = "Do not ask for approval of each plan. Required with --input -.")
    boolean yes;

    @Option(names = {"-n", "--dry-run"}, description = "Verify and plan each task, then cancel the plan. Nothing is simulated.")
    boolean dryRun;

    @Option(names = "--progress", description = "Print finds and rescues while each mission runs.")
    boolean progress;

    @Option(names = "--no-debrief", description = "Skip the AI-written debrief after each mission.")
    boolean noDebrief;

    @Option(names = "--depot-km", defaultValue = "30", description = "How far south of each incident the fleet starts (default: ${DEFAULT-VALUE} km).")
    double depotKm;

    @Option(names = "--seed", defaultValue = "42", description = "Random seed for where the survivors are (default: ${DEFAULT-VALUE}).")
    long seed;

    @Option(names = "--max-ticks", defaultValue = "20000", description = "Give up on a mission after this many simulated seconds (default: ${DEFAULT-VALUE}).")
    long maxTicks;

    AgentCommand(Supplier<AppContext> contextSupplier, BufferedReader stdin) {
        super(contextSupplier);
        this.stdin = stdin;
    }

    /** Applies {@code --llm} on top of the configuration from {@code .env}. */
    @Override
    protected AppContext openContext() {
        AppContext base = super.openContext();
        if (llm == null) {
            return base;
        }
        AppConfig changed = base.config().withLlmProvider(LlmProviderType.parse(llm));
        return AppContext.create(changed, base.clock());
    }

    @Override
    protected int execute(AppContext app, PrintWriter out, PrintWriter err) throws IOException {
        boolean fromStdin = input.equals("-");
        if (fromStdin && !yes && !dryRun) {
            err.println("--input - reads the tasks from standard input, so plans cannot be approved by typing. "
                    + "Add --yes to approve them automatically, or --dry-run to only plan.");
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

        String text;
        try {
            text = fromStdin ? readAll(stdin) : Files.readString(Path.of(input), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("Cannot read the task file " + input + ": " + e.getMessage());
            return ExitCodes.USAGE;
        }
        List<String> tasks = TaskReports.parse(text);
        if (tasks.isEmpty()) {
            err.println("The task file has no tasks. Write one incident report per task and separate tasks with a line of ---.");
            return ExitCodes.USAGE;
        }

        out.println("Agent: " + tasks.size() + " task(s), AI: " + app.config().llmProvider().name().toLowerCase());
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean failed = false;
        for (int i = 0; i < tasks.size(); i++) {
            out.println();
            Map<String, Object> row = new LinkedHashMap<>();
            failed |= runTask(app, i + 1, tasks.size(), tasks.get(i), row, out, err);
            rows.add(row);
        }

        out.println();
        out.println(summary(rows));
        if (output != null) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("llm", app.config().llmProvider().name().toLowerCase());
            root.put("tasks", rows);
            try {
                Files.writeString(output, com.fleetops.ai.Json.write(root) + "\n", StandardCharsets.UTF_8);
                out.println("Results written to " + output);
            } catch (IOException e) {
                err.println("Cannot write " + output + ": " + e.getMessage());
                return ExitCodes.FAILED;
            }
        }
        return failed ? ExitCodes.FAILED : ExitCodes.OK;
    }

    /** Runs one task. Returns true if it failed (the AI was unavailable, or the mission did not complete). */
    private boolean runTask(AppContext app, int number, int total, String task, Map<String, Object> row,
            PrintWriter out, PrintWriter err) throws IOException {
        row.put("task", number);
        row.put("text", CliFormat.clip(task, 200));
        out.printf("Task %d of %d: %s%n", number, total, CliFormat.clip(task, 70));

        try {
            TaskReports.Submission result = TaskReports.submit(app.intelligence(), app.repository(), task, app.clock().instant());
            row.put("status", result.status().name());
            row.put("reason", result.reason());
            out.printf("  %s: %s%n", result.status(), result.reason());
            if (!result.usable()) {
                return result.status() == TaskReports.Submission.Status.FAILED;
            }

            Incident incident = result.incident().orElseThrow();
            row.put("incidentId", incident.id().orElse(null));
            row.put("type", incident.type().keyword());
            row.put("title", incident.title());
            row.put("people", incident.estimatedPeople());
            out.printf("  Incident #%s: %s (%d people)%n", CliFormat.id(incident), incident.title(), incident.estimatedPeople());
            return runMission(app, incident, row, out);
        } catch (RuntimeException e) {
            row.put("status", "ERROR");
            row.put("reason", String.valueOf(e.getMessage()));
            err.println("  Error: " + e.getMessage());
            return true;
        }
    }

    private boolean runMission(AppContext app, Incident incident, Map<String, Object> row, PrintWriter out) throws IOException {
        GeoPoint depot = incident.area().center().offsetByKm(-depotKm, 0);
        SimulationRig rig = SimulationRig.create(app, FleetLayout.DEFAULT, depot, seed);
        MissionControl control = rig.control();

        MissionPlan plan = control.plan(incident);
        out.println(plan.describe());
        Map<String, Object> planRow = new LinkedHashMap<>();
        planRow.put("drones", plan.decision().drones());
        planRow.put("pattern", plan.decision().strategy().name());
        planRow.put("helicopters", plan.decision().helicopters());
        planRow.put("robodogs", plan.decision().robodogs());
        planRow.put("plannedBy", plan.decision().plannedBy());
        planRow.put("rationale", plan.decision().rationale());
        row.put("plan", planRow);

        if (dryRun) {
            control.cancelPlan(plan);
            row.put("mission", "DRY_RUN");
            out.println("Dry run: plan cancelled, nothing was simulated.");
            return false;
        }
        if (!yes && !CliFormat.confirm(stdin, out, "Launch this mission?")) {
            control.cancelPlan(plan);
            row.put("mission", "NOT_APPROVED");
            out.println("Plan not approved; the incident stays VERIFIED.");
            return false;
        }

        if (progress) {
            rig.engine().addListener(new MissionProgress(rig.engine(), out, false));
        }
        Mission mission;
        try {
            mission = control.launch(plan);
        } catch (RuntimeException e) {
            control.cancelPlan(plan);
            throw e;
        }
        out.println("Mission launched with " + mission.helicopters().size() + " helicopter(s).");
        try {
            control.runUntilSettled(maxTicks);
        } finally {
            if (!mission.isFinished()) {
                control.abort(mission, "the run ended before the mission finished");
                control.runUntilSettled(maxTicks);
            }
        }

        MissionReport report = mission.report().orElseThrow(
                () -> new IllegalStateException("The mission did not finish within " + maxTicks + " seconds."));
        out.println(report.describe());
        Map<String, Object> missionRow = new LinkedHashMap<>();
        missionRow.put("status", report.status().name());
        missionRow.put("people", report.people());
        missionRow.put("rescued", report.rescued());
        missionRow.put("foundNotRescued", report.foundNotRescued());
        missionRow.put("notFound", report.notFound());
        missionRow.put("minutes", Math.round(report.seconds() / 6.0) / 10.0);
        row.put("mission", missionRow);

        if (!noDebrief) {
            Debrief debrief = app.debriefer().debrief(report);
            out.println();
            out.println(debrief.describe());
            Map<String, Object> debriefRow = new LinkedHashMap<>();
            debriefRow.put("summary", debrief.summary());
            debriefRow.put("writtenBy", debrief.writtenBy());
            row.put("debrief", debriefRow);
        }
        return report.status() != MissionStatus.COMPLETED;
    }

    /** Reads everything from a reader, for {@code --input -}. */
    private static String readAll(BufferedReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            text.append(line).append('\n');
        }
        return text.toString();
    }

    private static String summary(List<Map<String, Object>> rows) {
        long verified = rows.stream().filter(r -> "VERIFIED".equals(r.get("status"))).count();
        long rejected = rows.stream().filter(r -> "REJECTED".equals(r.get("status"))).count();
        long completed = rows.stream()
                .filter(r -> r.get("mission") instanceof Map<?, ?> m && "COMPLETED".equals(m.get("status"))).count();
        return rows.size() + " task(s): " + verified + " verified, " + rejected + " rejected, " + completed + " mission(s) completed";
    }
}
