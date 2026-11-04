package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.domain.incident.Incident;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code fleetops reset}: removes stored incidents so a demo can start again. Also the way out when
 * a program that was killed mid-mission left an incident marked IN_PROGRESS.
 */
@Command(
        name = "reset",
        mixinStandardHelpOptions = true,
        description = "Delete stored incidents (all of them, or only finished ones with --finished).")
final class ResetCommand extends AbstractAppCommand {

    private final BufferedReader input;

    @Option(names = {"-f", "--finished"}, description = "Only delete completed, rejected and duplicate incidents.")
    boolean finishedOnly;

    @Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
    boolean yes;

    ResetCommand(Supplier<AppContext> contextSupplier, BufferedReader input) {
        super(contextSupplier);
        this.input = input;
    }

    @Override
    protected int execute(AppContext app, PrintWriter out, PrintWriter err) throws IOException {
        List<Incident> all = app.repository().findAll();
        long affected = all.stream().filter(i -> !finishedOnly || !i.isActive()).count();
        if (affected == 0) {
            out.println("Nothing to delete.");
            return ExitCodes.OK;
        }
        String what = finishedOnly ? "finished incident(s)" : "incident(s)";
        if (!yes && !CliFormat.confirm(input, out, "Delete " + affected + " " + what + "?")) {
            out.println("Nothing was deleted.");
            return ExitCodes.OK;
        }
        int removed = finishedOnly ? app.repository().deleteFinished() : app.repository().deleteAll();
        out.println("Deleted " + removed + " " + what + ".");
        return ExitCodes.OK;
    }
}
