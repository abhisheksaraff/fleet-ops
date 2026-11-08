package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.domain.incident.Incident;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** {@code fleetops show <id>}: all details of one incident. */
@Command(name = "show", mixinStandardHelpOptions = true, description = "Show one incident in detail.")
final class ShowCommand extends AbstractAppCommand {

    @Parameters(index = "0", paramLabel = "ID", description = "Incident id, as listed by 'fleetops incidents'.")
    long id;

    ShowCommand(Supplier<AppContext> contextSupplier) {
        super(contextSupplier);
    }

    @Override
    protected int execute(AppContext app, PrintWriter out, PrintWriter err) {
        Optional<Incident> found = app.repository().findById(id);
        if (found.isEmpty()) {
            err.println("No incident with id " + id + ".");
            return ExitCodes.NOT_FOUND;
        }
        Incident i = found.get();
        out.println("Incident #" + CliFormat.id(i) + ": " + i.title());
        out.println("  Type      : " + i.type().keyword());
        out.println("  State     : " + i.state());
        out.println("  Severity  : " + i.severity());
        out.println("  People    : about " + i.estimatedPeople());
        out.println("  Area      : centre " + CliFormat.point(i.area().center())
                + ", " + String.format(java.util.Locale.ROOT, "%.1f km square", i.area().sideKm()));
        out.println("  Reported  : " + i.reportedAt());
        out.println("  Source    : " + CliFormat.clip(i.sourceUrl().orElse("-"), 80));
        out.println("  Summary   : " + i.summary());
        return ExitCodes.OK;
    }
}
