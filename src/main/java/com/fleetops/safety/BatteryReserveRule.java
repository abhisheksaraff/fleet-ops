package com.fleetops.safety;

import com.fleetops.command.MissionCommand;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.geo.GeoPoint;
import java.util.Locale;
import java.util.Optional;

/**
 * A unit must be able to finish the job <em>and</em> fly home afterwards, with a safety margin
 * left over. A command that itself ends at the base only needs enough battery to get there.
 */
public final class BatteryReserveRule implements SafetyRule {

    /** A point this close (km) to the base counts as being at the base. */
    private static final double AT_BASE_KM = 0.001;

    private final GeoPoint base;
    private final double reservePercent;

    public BatteryReserveRule(GeoPoint base, double reservePercent) {
        if (base == null) {
            throw new NullPointerException("base");
        }
        if (reservePercent < 0 || reservePercent >= 100 || Double.isNaN(reservePercent)) {
            throw new IllegalArgumentException("Reserve must be from 0 up to (not including) 100 percent: " + reservePercent);
        }
        this.base = base;
        this.reservePercent = reservePercent;
    }

    @Override
    public String name() {
        return "BatteryReserve";
    }

    @Override
    public Optional<String> check(MissionCommand command, GeoPoint start) {
        Unit unit = command.unit();
        double job = command.requiredBatteryPercent(start);
        GeoPoint end = command.endPosition(start);
        double endToBaseKm = end.distanceKmTo(base);
        boolean endsAtBase = endToBaseKm <= AT_BASE_KM;

        double homeLeg = endsAtBase ? 0.0 : endToBaseKm * unit.batteryDrainPercentPerKm();
        double margin = endsAtBase ? 0.0 : reservePercent;
        double needed = job + homeLeg + margin;
        if (needed <= unit.batteryPercent()) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT,
                "%s needs %.1f%% battery (job %.1f%% + trip home %.1f%% + reserve %.1f%%) but has %.1f%%",
                unit.id(), needed, job, homeLeg, margin, unit.batteryPercent()));
    }
}
