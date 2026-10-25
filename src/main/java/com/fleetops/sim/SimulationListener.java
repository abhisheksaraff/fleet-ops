package com.fleetops.sim;

/**
 * The observer in the Observer pattern: anything that wants to follow the simulation (the map view,
 * the telemetry panel, the CLI log) registers one with {@link SimulationEngine#addListener} and
 * receives every {@link SimulationEvent}. The engine knows nothing about what listeners do, so the
 * simulation never depends on the GUI.
 *
 * <p>Listeners are called synchronously on the thread that runs the simulation. A GUI listener must
 * hand the work over to the Swing event thread itself.
 */
@FunctionalInterface
public interface SimulationListener {

    void onEvent(SimulationEvent event);
}
