package com.fleetops.command;

/** Where a {@link MissionCommand} is in its life. */
public enum CommandStatus {
    /** Queued; has not taken its first step yet. */
    PENDING,
    /** Taking steps. */
    RUNNING,
    /** Finished successfully. */
    DONE,
    /** Stopped because it could not be carried out. */
    FAILED,
    /** Dropped before finishing (abort, or an earlier command for the same unit failed). */
    CANCELLED;

    public boolean isFinished() {
        return this == DONE || this == FAILED || this == CANCELLED;
    }
}
