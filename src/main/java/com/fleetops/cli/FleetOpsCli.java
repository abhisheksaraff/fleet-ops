package com.fleetops.cli;

import com.fleetops.app.AppContext;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Entry point and root command of the FleetOps command-line interface.
 *
 * <p>Subcommands are registered in {@link #commandLine(Supplier)}. The {@link AppContext} is passed
 * as a supplier so tests can inject a configuration without touching the real environment.
 */
@Command(
        name = "fleetops",
        mixinStandardHelpOptions = true,
        version = "FleetOps 0.1.0",
        description = "FleetOps - LLM-driven multi-robot rescue mission control (command line).")
public final class FleetOpsCli implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** Builds the full command tree. */
    public static CommandLine commandLine(Supplier<AppContext> contextSupplier) {
        CommandLine cli = new CommandLine(new FleetOpsCli());
        cli.addSubcommand(new ConfigCommand(contextSupplier));
        return cli;
    }

    public static void main(String[] args) {
        System.exit(commandLine(AppContext::fromEnvironment).execute(args));
    }
}
