package com.fleetops.cli;

import com.fleetops.sim.SimulationEngine;
import com.fleetops.sim.SimulationEvent;
import com.fleetops.sim.SimulationListener;
import java.io.PrintWriter;
import java.util.Locale;

/** Observer that prints the important simulation events (finds, rescues, state changes) as they happen. */
final class MissionProgress implements SimulationListener {

    private final SimulationEngine engine;
    private final PrintWriter out;
    private final boolean everyState;

    /**
     * @param everyState also print each unit state change (verbose); otherwise only finds and rescues
     */
    MissionProgress(SimulationEngine engine, PrintWriter out, boolean everyState) {
        this.engine = engine;
        this.out = out;
        this.everyState = everyState;
    }

    @Override
    public void onEvent(SimulationEvent event) {
        String text = switch (event) {
            case SimulationEvent.SurvivorFound e -> e.foundBy().id() + " found " + e.survivor().id();
            case SimulationEvent.SurvivorRescued e -> e.rescuedBy().id() + " rescued " + e.survivor().id();
            case SimulationEvent.ZoneCleared e -> "area cleared: everyone is rescued";
            case SimulationEvent.UnitStateChanged e when everyState ->
                    e.unit().id() + ": " + e.from() + " -> " + e.to();
            default -> null;
        };
        if (text != null) {
            out.printf(Locale.ROOT, "[%6.0fs] %s%n", engine.elapsedSeconds(), text);
        }
    }
}
