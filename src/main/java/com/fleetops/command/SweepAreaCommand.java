package com.fleetops.command;

import com.fleetops.domain.fleet.Sensing;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.sim.SimulationEngine;
import com.fleetops.strategy.SearchStrategy;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A drone or robodog follows a search pattern over an incident area and reveals the survivors it
 * passes. The pattern comes from a {@link SearchStrategy}, so the same command works for any of
 * them, and for any unit that can sense survivors.
 */
public final class SweepAreaCommand extends AbstractMissionCommand {

    private final Unit unit;
    private final Sensing sensor;
    private final GeoArea area;
    private final SearchStrategy strategy;
    private final List<GeoPoint> path;

    private int nextWaypoint;
    private int survivorsFound;

    public <U extends Unit & Sensing> SweepAreaCommand(U searcher, GeoArea area, SearchStrategy strategy) {
        super(searcher);
        this.unit = searcher;
        this.sensor = searcher;
        this.area = Objects.requireNonNull(area, "area");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.path = List.copyOf(strategy.computePath(area, searcher.sensorRadiusKm()));
    }

    @Override
    public String describe() {
        return unit.id() + " sweep " + String.format(Locale.ROOT, "%.1f", area.sideKm())
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
        survivorsFound += engine.revealSurvivors(unit, sensor.sensorRadiusKm());

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
