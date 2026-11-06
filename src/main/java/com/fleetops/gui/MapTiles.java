package com.fleetops.gui;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

/**
 * Factory for the map's tile source, chosen by {@code MAP_TILES}. Only {@code osm} (OpenStreetMap)
 * exists today; supporting another provider means adding a case here and nothing else.
 */
final class MapTiles {

    static final String OSM = "osm";
    static final String ATTRIBUTION = "© OpenStreetMap contributors";
    static final String OSM_TILE_URL = "https://tile.openstreetmap.org";

    private MapTiles() {}

    /** @param name the {@code MAP_TILES} value; an unknown name falls back to OpenStreetMap (see {@link #isKnown}) */
    static TileFactory create(String name) {
        DefaultTileFactory factory = new DefaultTileFactory(new OsmTiles());
        // The OpenStreetMap tile policy asks every application to identify itself.
        factory.setUserAgent("FleetOps/0.1 (university course project)");
        return factory;
    }

    static boolean isKnown(String name) {
        return OSM.equalsIgnoreCase(name == null ? "" : name.strip());
    }

    /**
     * OpenStreetMap's standard tiles over HTTPS. JXMapViewer2 2.8's own {@code OSMTileFactoryInfo}
     * still uses {@code http://}, which OpenStreetMap only answers with a redirect to https that Java
     * does not follow, so no tile would ever load.
     */
    static final class OsmTiles extends TileFactoryInfo {

        private static final int MAX_ZOOM = 19;

        OsmTiles() {
            super("OpenStreetMap", 0, MAX_ZOOM, MAX_ZOOM, 256, true, true, OSM_TILE_URL, "x", "y", "z");
        }

        @Override
        public String getTileUrl(int x, int y, int zoom) {
            // JXMapViewer counts zoom levels the other way round from the tile servers.
            return OSM_TILE_URL + "/" + (MAX_ZOOM - zoom) + "/" + x + "/" + y + ".png";
        }
    }
}
