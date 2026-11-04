package com.fleetops.cli;

/** Process exit codes used by every command, so scripts can tell failures apart. */
public final class ExitCodes {

    /** The command did what was asked. */
    public static final int OK = 0;
    /** The configuration ({@code .env}) is wrong or the database cannot be opened. */
    public static final int CONFIG_ERROR = 1;
    /** Picocli's own code for unknown options or missing arguments. */
    public static final int USAGE = 2;
    /** The incident asked for does not exist. */
    public static final int NOT_FOUND = 3;
    /** The command ran but the work failed or was stopped (no plan, mission not completed, ...). */
    public static final int FAILED = 4;

    private ExitCodes() {}
}
