package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.domain.incident.IncidentType;
import com.fleetops.intel.IngestOutcome;
import com.fleetops.intel.IngestReport;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code fleetops scan}: reads the news, lets the AI verify it and stores new incidents. */
@Command(
        name = "scan",
        mixinStandardHelpOptions = true,
        description = "Look for one new news item per topic, have the AI verify it and store new incidents.")
final class ScanCommand extends AbstractAppCommand {

    @Option(names = {"-t", "--topic"}, split = ",", paramLabel = "TYPE",
            description = "Only scan these topics (flood, famine, fire, earthquake). Default: NEWS_TOPICS from .env.")
    List<String> topics = new ArrayList<>();

    @Option(names = {"-v", "--verbose"}, description = "Also list rejected and failed items with the reason.")
    boolean verbose;

    ScanCommand(Supplier<AppContext> contextSupplier) {
        super(contextSupplier);
    }

    @Override
    protected int execute(AppContext app, PrintWriter out, PrintWriter err) {
        List<IncidentType> wanted = new ArrayList<>();
        try {
            for (String name : topics) {
                wanted.add(IncidentType.parse(name));
            }
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return ExitCodes.USAGE;
        }
        if (wanted.isEmpty()) {
            wanted.addAll(app.config().newsTopics());
        }

        IngestReport report = app.intelligence().scan(wanted);
        for (IngestOutcome outcome : report.outcomes()) {
            boolean shown = outcome.status() == IngestOutcome.Status.CREATED || verbose;
            if (!shown) {
                continue;
            }
            String id = outcome.incident().map(CliFormat::id).orElse("-");
            out.printf("%-9s #%-3s %s%n", outcome.status(), id, CliFormat.clip(outcome.item().title(), 70));
            if (outcome.status() != IngestOutcome.Status.CREATED) {
                out.println("            " + outcome.reason());
            }
        }
        report.sourceFailures().forEach((type, why) ->
                err.println("Could not read " + type.keyword() + " news: " + why));
        out.println(report.summary());
        return report.created().isEmpty() && !report.sourceFailures().isEmpty() ? ExitCodes.FAILED : ExitCodes.OK;
    }
}
