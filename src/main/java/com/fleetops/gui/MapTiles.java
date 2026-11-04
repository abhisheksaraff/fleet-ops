package com.fleetops.gui;

import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;

/**
 * Factory for the map's tile source, chosen by {@code MAP_TILES}. Only {@code osm} (OpenStreetMap)
 * exists today; supporting another provider means adding a case here and nothing else.
 */
final class MapTiles {

    static final String OSM = "osm";
    static final String ATTRIBUTION = "© OpenStreetMap contributors";

    private MapTiles() {}

    /** @param name the {@code MAP_TILES} value; an unknown name falls back to OpenStreetMap (see {@link #isKnown}) */
    static TileFactory create(String name) {
        // The OpenStreetMap tile policy asks every application to identify itself.
        DefaultTileFactory factory = new DefaultTileFactory(new OSMTileFactoryInfo());
        factory.setUserAgent("FleetOps/0.1 (university course project)");
        return factory;
    }

    static boolean isKnown(String name) {
        return OSM.equalsIgnoreCase(name == null ? "" : name.strip());
    }
}
