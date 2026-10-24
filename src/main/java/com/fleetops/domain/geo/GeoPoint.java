package com.fleetops.domain.geo;

/**
 * An immutable WGS-84 coordinate in decimal degrees.
 *
 * <p>The simulation works in kilometres on a flat local plane around a point, which is accurate
 * enough for incident areas a few kilometres wide. {@link #distanceKmTo(GeoPoint)} uses the
 * haversine formula and is accurate over any distance.
 */
public record GeoPoint(double lat, double lon) {

    public static final double EARTH_RADIUS_KM = 6371.0088;
    public static final double KM_PER_DEGREE_LAT = Math.PI * EARTH_RADIUS_KM / 180.0;

    public GeoPoint {
        // Written as !(in range) so that NaN is rejected as well.
        if (!(lat >= -90.0 && lat <= 90.0)) {
            throw new IllegalArgumentException("Latitude must be within [-90, 90]: " + lat);
        }
        if (!(lon >= -180.0 && lon <= 180.0)) {
            throw new IllegalArgumentException("Longitude must be within [-180, 180]: " + lon);
        }
    }

    /** Great-circle distance to another point, in kilometres. */
    public double distanceKmTo(GeoPoint other) {
        double dLat = Math.toRadians(other.lat - lat);
        double dLon = Math.toRadians(other.lon - lon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat))
                        * Math.cos(Math.toRadians(other.lat))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Kilometres {@code other} lies north (positive) or south (negative) of this point. */
    public double northKmTo(GeoPoint other) {
        return (other.lat - lat) * KM_PER_DEGREE_LAT;
    }

    /** Kilometres {@code other} lies east (positive) or west (negative) of this point. */
    public double eastKmTo(GeoPoint other) {
        double dLon = wrapLongitudeDifference(other.lon - lon);
        return dLon * KM_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat));
    }

    /**
     * Returns the point reached by moving the given distances on the local flat plane.
     *
     * @throws IllegalArgumentException if the result would fall outside valid latitudes
     */
    public GeoPoint offsetByKm(double northKm, double eastKm) {
        double newLat = lat + northKm / KM_PER_DEGREE_LAT;
        double newLon = lon + eastKm / (KM_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));
        return new GeoPoint(newLat, wrapLongitude(newLon));
    }

    private static double wrapLongitude(double lon) {
        if (lon >= -180.0 && lon <= 180.0) {
            return lon;
        }
        return ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

    private static double wrapLongitudeDifference(double diff) {
        if (diff > 180.0) {
            return diff - 360.0;
        }
        if (diff < -180.0) {
            return diff + 360.0;
        }
        return diff;
    }
}
