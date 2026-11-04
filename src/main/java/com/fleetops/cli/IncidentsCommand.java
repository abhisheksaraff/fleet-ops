package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.domain.incident.IncidentType;
import java.io.PrintWriter;
import java.util.List;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code fleetops incidents}: lists the stored incidents. */
@Command(
        name = "incidents",
        mixinStandardHelpOptions = true,
        description = "List stored incidents (active ones by default).")
final class IncidentsCommand extends AbstractAppCommand {

    @Option(names = {"-a", "--all"}, description = "Include completed, rejected and duplicate incidents.")
    boolean all;

    @Option(names = {"-s", "--state"}, paramLabel = "STATE", description = "Only incidents in this state (e.g. VERIFIED, COMPLETED).")
    String state;

    @Option(names = {"-t", "--type"}, paramLabel = "TYPE", description = "Only this type (flood, famine, fire, earthquake).")
    String type;

    IncidentsCommand(Supplier<AppContext> contextSupplier) {
        super(contextSupplier);
    }

    @Override
    protected int execute(AppContext app, PrintWriter out, PrintWriter err) {
        IncidentState wantedState = null;
        IncidentType wantedType = null;
        try {
            if (state != null) {
                wantedState = IncidentState.valueOf(state.strip().toUpperCase(java.util.Locale.ROOT));
            }
            if (type != null) {
                wantedType = IncidentType.parse(type);
            }
        } catch (IllegalArgumentException e) {
            err.println("Invalid filter: " + e.getMessage());
            return ExitCodes.USAGE;
        }
        final IncidentState s = wantedState;
        final IncidentType t = wantedType;
        List<Incident> found = app.repository().findAll().stream()
                .filter(i -> s != null ? i.state() == s : all || i.isActive())
                .filter(i -> t == null || i.type() == t)
                .toList();
        if (found.isEmpty()) {
            out.println("No incidents. Run 'fleetops scan' to look for some.");
        } else {
            out.println(CliFormat.table(found));
        }
        return ExitCodes.OK;
    }
}
