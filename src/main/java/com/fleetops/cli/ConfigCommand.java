package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.config.ConfigException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** {@code fleetops config}: shows the effective configuration so setup problems are easy to spot. */
@Command(
        name = "config",
        mixinStandardHelpOptions = true,
        description = "Show the effective configuration. API keys are never printed.")
final class ConfigCommand implements Callable<Integer> {

    private final Supplier<AppContext> contextSupplier;

    @Spec
    CommandSpec spec;

    ConfigCommand(Supplier<AppContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try {
            out.println(contextSupplier.get().config().describe());
            return 0;
        } catch (ConfigException e) {
            err.println("Configuration error: " + e.getMessage());
            return 1;
        }
    }
}
