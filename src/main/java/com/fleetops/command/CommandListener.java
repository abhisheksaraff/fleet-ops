package com.fleetops.command;

/**
 * Notified by the {@link CommandQueue} each time a command finishes, fails or is cancelled. The GUI
 * and CLI use it to show what happened to each part of a plan. A listener may enqueue new commands;
 * they start on the next tick.
 */
@FunctionalInterface
public interface CommandListener {

    void onCommandFinished(CommandResult result);
}
