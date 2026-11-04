package com.fleetops.cli;

import com.fleetops.app.AppContext;
import java.io.PrintWriter;
import java.util.function.Supplier;
import picocli.CommandLine.Command;

/** {@code fleetops config}: shows the effective configuration so setup problems are easy to spot. */
@Command(
        name = "config",
        mixinStandardHelpOptions = true,
        description = "Show the effective configuration. API keys are never printed.")
final class ConfigCommand extends AbstractAppCommand {

    ConfigCommand(Supplier<AppContext> contextSupplier) {
        super(contextSupplier);
    }

    @Override
    protected int execute(AppContext app, PrintWriter out, PrintWriter err) {
        out.println(app.config().describe());
        return ExitCodes.OK;
    }
}
