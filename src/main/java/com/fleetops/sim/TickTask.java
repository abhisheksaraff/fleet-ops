package com.fleetops.sim;

/**
 * Work the engine performs on every tick, in the order the tasks were registered. The mission
 * command queue (added in a later commit) is a tick task: on each tick it advances the commands
 * that are currently running.
 */
@FunctionalInterface
public interface TickTask {

    void onTick(SimulationEngine engine);
}
