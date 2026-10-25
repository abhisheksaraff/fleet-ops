package com.fleetops.command;

import java.util.Objects;

/**
 * The outcome of one command, recorded by the {@link CommandQueue} when the command finishes.
 *
 * @param command the command that finished
 * @param status {@code DONE}, {@code FAILED} or {@code CANCELLED}
 * @param detail the reason for a failure or cancellation; empty for a successful command
 * @param tick the simulation tick at which it finished
 */
public record CommandResult(MissionCommand command, CommandStatus status, String detail, long tick) {

    public CommandResult {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
    }

    public boolean succeeded() {
        return status == CommandStatus.DONE;
    }
}
