package com.fleetops.strategy;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import java.util.List;

/**
 * How a drone covers an incident area: the Strategy pattern. Each implementation turns an area and
 * the drone's sensor radius into a list of waypoints to fly through. The sweep command works with
 * any strategy, so a new search pattern is a new class and nothing else changes.
 *
 * <p>Every implementation must guarantee that each point of the area lies within the sensor radius
 * of some part of the path it returns.
 */
public interface SearchStrategy {

    /** Short lowercase name, for example {@code "lawnmower"}. */
    String name();

    /**
     * Waypoints to fly, in order. The path stays inside the area and starts at its first waypoint.
     *
     * @param sensorRadiusKm how far from its path a drone can spot a survivor
     */
    List<GeoPoint> computePath(GeoArea area, double sensorRadiusKm);
}
