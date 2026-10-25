package com.fleetops.sim;

import com.fleetops.domain.fleet.FleetComponent;
import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.Survivor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runs the simulation: advances time in fixed ticks, applies the physics (movement, battery use,
 * spotting and rescuing survivors) and announces everything that happens to its
 * {@link SimulationListener}s (the subject of the Observer pattern).
 *
 * <p>The engine has no GUI or AI code. Mission commands change the world only through the methods
 * here, which is what guarantees that every change is also published as an event. Work that must
 * happen on every tick is registered as a {@link TickTask}.
 *
 * <p>One tick is {@link #tickSeconds()} seconds of simulated time (1 second by default), small
 * enough that a drone never skips over a survivor between two steps.
 */
public final class SimulationEngine {

    public static final double DEFAULT_TICK_SECONDS = 1.0;

    /** A helicopter must be within this distance of a survivor to pick them up. */
    public static final double PICKUP_RANGE_KM = 0.05;

    private static final double ARRIVAL_TOLERANCE_KM = 1e-6;
    private static final System.Logger LOG = System.getLogger(SimulationEngine.class.getName());

    private final World world;
    private final double tickSeconds;
    private final List<SimulationListener> listeners = new CopyOnWriteArrayList<>();
    private final List<TickTask> tickTasks = new CopyOnWriteArrayList<>();
    private long tickCount;

    public SimulationEngine(World world) {
        this(world, DEFAULT_TICK_SECONDS);
    }

    public SimulationEngine(World world, double tickSeconds) {
        if (!(tickSeconds > 0.0)) {
            throw new IllegalArgumentException("tickSeconds must be positive: " + tickSeconds);
        }
        this.world = Objects.requireNonNull(world, "world");
        this.tickSeconds = tickSeconds;
    }

    public World world() {
        return world;
    }

    public double tickSeconds() {
        return tickSeconds;
    }

    public long currentTick() {
        return tickCount;
    }

    /** Simulated time that has passed since the start. */
    public double elapsedSeconds() {
        return tickCount * tickSeconds;
    }

    // ---- observers and tick tasks --------------------------------------------------------------

    public void addListener(SimulationListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(SimulationListener listener) {
        listeners.remove(listener);
    }

    public void addTickTask(TickTask task) {
        tickTasks.add(Objects.requireNonNull(task, "task"));
    }

    public void removeTickTask(TickTask task) {
        tickTasks.remove(task);
    }

    // ---- running -------------------------------------------------------------------------------

    /** Advances the simulation by one tick: runs every tick task, then announces the tick. */
    public void tick() {
        tickCount++;
        for (TickTask task : tickTasks) {
            task.onTick(this);
        }
        publish(new SimulationEvent.TickCompleted(tickCount));
    }

    public void runTicks(long ticks) {
        for (long i = 0; i < ticks; i++) {
            tick();
        }
    }

    /**
     * Ticks until the condition becomes true or {@code maxTicks} have run.
     *
     * @return the number of ticks that were run
     */
    public long runUntil(BooleanSupplier done, long maxTicks) {
        long run = 0;
        while (run < maxTicks && !done.getAsBoolean()) {
            tick();
            run++;
        }
        return run;
    }

    // ---- world changes (each one publishes events) ---------------------------------------------

    /** Scatters survivors inside the incident's area and adds the area to the world. */
    public IncidentZone addIncident(Incident incident, SurvivorSpawner spawner) {
        IncidentZone zone = new IncidentZone(incident, spawner.spawn(incident));
        world.addZone(zone);
        publish(new SimulationEvent.ZoneAdded(zone));
        return zone;
    }

    /**
     * Moves a unit toward a target for one tick, limited by its speed and remaining battery, and
     * uses up battery in proportion to the distance flown. A carrier takes its docked drones along.
     *
     * @return true if the unit is at the target after this step; false if it still has distance to
     *     cover, or cannot get there because its battery is empty
     */
    public boolean moveToward(Unit unit, GeoPoint target) {
        GeoPoint from = unit.position();
        if (from.equals(target)) {
            return true;
        }
        double distanceKm = from.distanceKmTo(target);
        double reachKm = Math.min(unit.speedKmh() * tickSeconds / 3600.0, unit.rangeKm());

        // Within a millimetre counts as arrived, and the unit is snapped exactly onto the target.
        boolean arrives = distanceKm <= reachKm + ARRIVAL_TOLERANCE_KM;
        if (!arrives && reachKm <= 0.0) {
            return false;
        }

        double travelledKm;
        GeoPoint next;
        if (arrives) {
            travelledKm = distanceKm;
            next = target;
        } else {
            double fraction = reachKm / distanceKm;
            travelledKm = reachKm;
            next = from.offsetByKm(from.northKmTo(target) * fraction, from.eastKmTo(target) * fraction);
        }

        List<Unit> affected = unit.units();
        Map<Unit, GeoPoint> before = new LinkedHashMap<>();
        affected.forEach(u -> before.put(u, u.position()));

        unit.consumeBattery(travelledKm * unit.batteryDrainPercentPerKm());
        unit.moveTo(next);

        for (Unit moved : affected) {
            if (!moved.position().equals(before.get(moved))) {
                publish(new SimulationEvent.UnitMoved(moved, before.get(moved), moved.position()));
            }
        }
        return arrives;
    }

    /** Applies a lifecycle event to one unit (for example {@code Unit::dispatch}) and announces the change. */
    public void changeState(Unit unit, Consumer<Unit> event) {
        UnitState before = unit.state();
        event.accept(unit);
        if (unit.state() != before) {
            publish(new SimulationEvent.UnitStateChanged(unit, before, unit.state()));
        }
    }

    /** Sends a unit, carrier or whole fleet back to base; each unit that changes state is announced. */
    public void recall(FleetComponent target) {
        broadcast(target, FleetComponent::recall);
    }

    /** Emergency stop for a unit, carrier or whole fleet; each unit that changes state is announced. */
    public void hold(FleetComponent target) {
        broadcast(target, FleetComponent::hold);
    }

    /**
     * Reveals every hidden survivor within {@code radiusKm} of the scanning unit.
     *
     * @return how many survivors were newly found
     */
    public int revealSurvivors(Unit scanner, double radiusKm) {
        int found = 0;
        for (Survivor survivor : world.survivors()) {
            if (scanner.position().distanceKmTo(survivor.position()) <= radiusKm && survivor.reveal()) {
                found++;
                publish(new SimulationEvent.SurvivorFound(survivor, scanner));
            }
        }
        return found;
    }

    /**
     * A helicopter picks up a survivor. When this was the last survivor of an incident area, a
     * {@link SimulationEvent.ZoneCleared} event follows.
     *
     * @throws IllegalStateException if the helicopter is too far away, is full, or the survivor has
     *     not been found yet
     */
    public void rescue(Helicopter helicopter, Survivor survivor) {
        double distanceKm = helicopter.position().distanceKmTo(survivor.position());
        if (distanceKm > PICKUP_RANGE_KM) {
            throw new IllegalStateException(helicopter.id() + " is too far from " + survivor.id() + " to pick them up");
        }
        helicopter.board(survivor);
        publish(new SimulationEvent.SurvivorRescued(survivor, helicopter));
        world.zoneOf(survivor)
                .filter(IncidentZone::isCleared)
                .ifPresent(zone -> publish(new SimulationEvent.ZoneCleared(zone)));
    }

    // ---- internals -----------------------------------------------------------------------------

    /**
     * Applies a fleet-wide action and announces every unit it changed. States are compared before and
     * after, because a carrier's action also reaches its drones.
     */
    private void broadcast(FleetComponent target, Consumer<FleetComponent> action) {
        List<Unit> units = target.units();
        Map<Unit, UnitState> before = new LinkedHashMap<>();
        units.forEach(unit -> before.put(unit, unit.state()));

        action.accept(target);

        for (Unit unit : units) {
            if (unit.state() != before.get(unit)) {
                publish(new SimulationEvent.UnitStateChanged(unit, before.get(unit), unit.state()));
            }
        }
    }

    /** One failing listener must not stop the others, or the simulation. */
    private void publish(SimulationEvent event) {
        for (SimulationListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Simulation listener failed on " + event, e);
            }
        }
    }
}
