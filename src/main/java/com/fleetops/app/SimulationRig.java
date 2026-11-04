package com.fleetops.app;

import com.fleetops.domain.fleet.Fleet;
import com.fleetops.domain.fleet.UnitFactory;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.mission.MissionControl;
import com.fleetops.plan.PlanCompiler;
import com.fleetops.sim.SimulationEngine;
import com.fleetops.sim.SurvivorSpawner;
import com.fleetops.sim.World;
import java.util.Objects;
import java.util.Random;

/**
 * A ready-to-run simulation: a fleet parked at a base, the world and engine around it, and a
 * {@link MissionControl} wired to the application's planner and incident store. The CLI builds one
 * per command and the GUI builds one per session, so both run exactly the same simulation.
 */
public final class SimulationRig {

    private final FleetLayout layout;
    private final GeoPoint depot;
    private final Fleet fleet;
    private final SimulationEngine engine;
    private final MissionControl control;

    private SimulationRig(FleetLayout layout, GeoPoint depot, Fleet fleet, SimulationEngine engine, MissionControl control) {
        this.layout = layout;
        this.depot = depot;
        this.fleet = fleet;
        this.engine = engine;
        this.control = control;
    }

    /**
     * @param app supplies the planner and the incident store
     * @param layout how many rovers, drones and helicopters to start with
     * @param depot where every unit starts and finally returns to
     * @param seed makes the placement of survivors repeatable
     */
    public static SimulationRig create(AppContext app, FleetLayout layout, GeoPoint depot, long seed) {
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(depot, "depot");

        UnitFactory factory = new UnitFactory();
        Fleet fleet = new Fleet("fleet");
        for (int i = 0; i < layout.rovers(); i++) {
            fleet.add(factory.createCarrier(depot, layout.dronesPerRover()));
        }
        for (int i = 0; i < layout.helicopters(); i++) {
            fleet.add(factory.createHelicopter(depot));
        }
        SimulationEngine engine = new SimulationEngine(new World(fleet));
        MissionControl control = new MissionControl(
                engine, app.planner(), new PlanCompiler(), app.repository(), new SurvivorSpawner(new Random(seed)));
        return new SimulationRig(layout, depot, fleet, engine, control);
    }

    public FleetLayout layout() {
        return layout;
    }

    public GeoPoint depot() {
        return depot;
    }

    public Fleet fleet() {
        return fleet;
    }

    public SimulationEngine engine() {
        return engine;
    }

    public MissionControl control() {
        return control;
    }
}
