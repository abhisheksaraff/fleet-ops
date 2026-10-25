package com.fleetops.command;

import com.fleetops.domain.geo.GeoPoint;
import java.util.List;
import java.util.Locale;

/** Small helpers for working with routes (ordered lists of waypoints). */
public final class Routes {

    private Routes() {}

    /** Total length in km of flying from {@code start} through every waypoint in order. */
    public static double lengthKm(GeoPoint start, List<GeoPoint> route) {
        double total = 0.0;
        GeoPoint previous = start;
        for (GeoPoint waypoint : route) {
            total += previous.distanceKmTo(waypoint);
            previous = waypoint;
        }
        return total;
    }

    /** Formats a point for descriptions, e.g. {@code 43.6500, -79.3800}. */
    public static String format(GeoPoint point) {
        return String.format(Locale.ROOT, "%.4f, %.4f", point.lat(), point.lon());
    }
}
