package com.fleetops.cli;

import com.fleetops.app.AppContext;
import com.fleetops.config.ConfigException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Template Method for every command that needs the application: {@link #call()} fixes the steps
 * (build the context, run the command, translate failures into exit codes, close the context) and
 * subclasses only supply {@link #execute}. Error handling and cleanup therefore cannot be forgotten
 * by an individual command.
 */
abstract class AbstractAppCommand implements Callable<Integer> {

    private final Supplier<AppContext> contextSupplier;

    @Spec
    CommandSpec spec;

    AbstractAppCommand(Supplier<AppContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    /** The command's work. Returns the exit code. */
    protected abstract int execute(AppContext app, PrintWriter out, PrintWriter err) throws Exception;

    @Override
    public final Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try (AppContext app = contextSupplier.get()) {
            int code = execute(app, out, err);
            out.flush();
            return code;
        } catch (ConfigException e) {
            err.println("Configuration error: " + e.getMessage());
            return ExitCodes.CONFIG_ERROR;
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return ExitCodes.FAILED;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            return ExitCodes.FAILED;
        } finally {
            out.flush();
            err.flush();
        }
    }
}
