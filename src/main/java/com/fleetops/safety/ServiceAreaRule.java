package com.fleetops.safety;

import com.fleetops.command.MissionCommand;
import com.fleetops.domain.geo.GeoPoint;
import java.util.Locale;
import java.util.Optional;

/** Every point on a command's route must stay within a maximum distance of the base. */
public final class ServiceAreaRule implements SafetyRule {

    private final GeoPoint base;
    private final double maxDistanceKm;

    public ServiceAreaRule(GeoPoint base, double maxDistanceKm) {
        if (base == null) {
            throw new NullPointerException("base");
        }
        if (!(maxDistanceKm > 0) || Double.isInfinite(maxDistanceKm)) {
            throw new IllegalArgumentException("Service radius must be a positive number of km: " + maxDistanceKm);
        }
        this.base = base;
        this.maxDistanceKm = maxDistanceKm;
    }

    @Override
    public String name() {
        return "ServiceArea";
    }

    @Override
    public Optional<String> check(MissionCommand command, GeoPoint start) {
        double farthest = 0.0;
        for (GeoPoint waypoint : command.route()) {
            farthest = Math.max(farthest, base.distanceKmTo(waypoint));
        }
        if (farthest <= maxDistanceKm) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT, "route goes %.1f km from base (limit %.1f km)", farthest, maxDistanceKm));
    }
}
