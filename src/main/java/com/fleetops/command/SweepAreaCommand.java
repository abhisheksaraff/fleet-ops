package com.fleetops.command;

import com.fleetops.domain.fleet.Drone;
import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import com.fleetops.strategy.SearchStrategy;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A drone flies a search pattern over an incident area and reveals the survivors it passes. The
 * pattern comes from a {@link SearchStrategy}, so the same command works for any of them.
 */
public final class SweepAreaCommand extends AbstractMissionCommand {

    private final Drone drone;
    private final GeoArea area;
    private final SearchStrategy strategy;
    private final List<GeoPoint> path;

    private int nextWaypoint;
    private int survivorsFound;

    public SweepAreaCommand(Drone drone, GeoArea area, SearchStrategy strategy) {
        super(drone);
        this.drone = drone;
        this.area = Objects.requireNonNull(area, "area");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.path = List.copyOf(strategy.computePath(area, drone.sensorRadiusKm()));
    }

    @Override
    public String describe() {
        return drone.id() + " sweep " + String.format(Locale.ROOT, "%.1f", area.sideKm())
                + " km square (" + strategy.name() + ")";
    }

    @Override
    public List<GeoPoint> route() {
        return path;
    }

    /** How many survivors this sweep has revealed so far. */
    public int survivorsFound() {
        return survivorsFound;
    }

    @Override
    protected void onStart(SimulationEngine engine) {
        if (!startWork(engine)) {
            return;
        }
        if (path.isEmpty()) {
            complete();
        }
    }

    @Override
    protected void onStep(SimulationEngine engine) {
        boolean reachedWaypoint = moveOrFail(engine, path.get(nextWaypoint));
        if (isFinished()) {
            return;
        }
        survivorsFound += engine.revealSurvivors(drone, drone.sensorRadiusKm());

        if (reachedWaypoint) {
            if (nextWaypoint == 0) {
                arriveIfEnRoute(engine);
            }
            nextWaypoint++;
            if (nextWaypoint == path.size()) {
                complete();
            }
        }
    }
}
