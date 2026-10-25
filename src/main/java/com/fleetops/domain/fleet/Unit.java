package com.fleetops.domain.fleet;

import com.fleetops.domain.geo.GeoPoint;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A single robot: the leaf of the fleet Composite and the base class of {@link Drone},
 * {@link Helicopter} and {@link Carrier}.
 *
 * <p>A unit has a position, a battery and a {@link UnitState}. Its lifecycle events
 * ({@link #dispatch()}, {@link #arrive()}, {@link #recall()}, {@link #dock()}, {@link #hold()})
 * delegate to the current state. Movement and battery use are driven by the simulation (added in a
 * later commit) through {@link #moveTo(GeoPoint)} and {@link #consumeBattery(double)}.
 */
public abstract class Unit implements FleetComponent {

    private final String id;
    private final UnitType type;
    private final double speedKmh;
    private final double batteryDrainPercentPerKm;

    private GeoPoint position;
    private double batteryPercent = 100.0;
    private UnitState state = UnitState.IDLE;

    protected Unit(String id, UnitType type, GeoPoint position, double speedKmh, double batteryDrainPercentPerKm) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (!(speedKmh > 0.0)) {
            throw new IllegalArgumentException("speedKmh must be positive: " + speedKmh);
        }
        if (!(batteryDrainPercentPerKm > 0.0)) {
            throw new IllegalArgumentException("batteryDrainPercentPerKm must be positive: " + batteryDrainPercentPerKm);
        }
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.speedKmh = speedKmh;
        this.batteryDrainPercentPerKm = batteryDrainPercentPerKm;
    }

    // ---- identity and specs --------------------------------------------------------------------

    @Override
    public String id() {
        return id;
    }

    public UnitType type() {
        return type;
    }

    public double speedKmh() {
        return speedKmh;
    }

    /** Battery percentage used per kilometre travelled. */
    public double batteryDrainPercentPerKm() {
        return batteryDrainPercentPerKm;
    }

    // ---- position and battery ------------------------------------------------------------------

    public GeoPoint position() {
        return position;
    }

    public void moveTo(GeoPoint destination) {
        this.position = Objects.requireNonNull(destination, "destination");
    }

    public double batteryPercent() {
        return batteryPercent;
    }

    /** How far the unit can still travel on its current charge. */
    public double rangeKm() {
        return batteryPercent / batteryDrainPercentPerKm;
    }

    public boolean isDepleted() {
        return batteryPercent <= 0.0;
    }

    /** Uses up battery; the level never drops below zero. */
    public void consumeBattery(double percent) {
        if (!(percent >= 0.0)) {
            throw new IllegalArgumentException("percent must not be negative: " + percent);
        }
        batteryPercent = Math.max(0.0, batteryPercent - percent);
    }

    public void recharge() {
        batteryPercent = 100.0;
    }

    // ---- lifecycle (delegated to the current state) --------------------------------------------

    public UnitState state() {
        return state;
    }

    /** True when the unit is idle and can be given new work. */
    public boolean isAvailable() {
        return state.isAvailable();
    }

    public void dispatch() {
        state = state.dispatch();
    }

    public void arrive() {
        state = state.arrive();
    }

    public void dock() {
        state = state.dock();
    }

    @Override
    public void recall() {
        state = state.recall();
    }

    @Override
    public void hold() {
        state = state.hold();
    }

    // ---- FleetComponent ------------------------------------------------------------------------

    @Override
    public List<Unit> units() {
        return List.of(this);
    }

    @Override
    public String toString() {
        return id + "[" + state + ", " + String.format(Locale.ROOT, "%.0f", batteryPercent) + "% battery]";
    }
}
