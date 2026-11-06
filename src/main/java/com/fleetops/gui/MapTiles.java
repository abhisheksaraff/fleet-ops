package com.fleetops.gui;

import java.util.Locale;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

/**
 * Factory for the map's tile source, chosen by {@code MAP_TILES}: {@code dark} (CARTO's Dark Matter,
 * the default, which suits the dark interface) or {@code osm} (the standard OpenStreetMap look).
 * Supporting another provider means adding a case here and nothing else.
 */
final class MapTiles {

    static final String DARK = "dark";
    static final String OSM = "osm";
    static final String OSM_TILE_URL = "https://tile.openstreetmap.org";
    static final String OSM_ATTRIBUTION = "© OpenStreetMap contributors";
    static final String DARK_ATTRIBUTION = "© OpenStreetMap contributors © CARTO";

    private static final int MAX_ZOOM = 19;

    private MapTiles() {}

    /** @param name the {@code MAP_TILES} value; an unknown name gives the dark map (see {@link #isKnown}) */
    static TileFactory create(String name) {
        TileFactoryInfo info = isOsm(name) ? new OsmTiles() : new DarkTiles();
        DefaultTileFactory factory = new DefaultTileFactory(info);
        // Tile servers ask every application to identify itself.
        factory.setUserAgent("FleetOps/0.1 (university course project)");
        return factory;
    }

    static boolean isKnown(String name) {
        String wanted = normalise(name);
        return DARK.equals(wanted) || OSM.equals(wanted);
    }

    /** The credit line the map must show for a tile source. */
    static String attribution(String name) {
        return isOsm(name) ? OSM_ATTRIBUTION : DARK_ATTRIBUTION;
    }

    private static boolean isOsm(String name) {
        return OSM.equals(normalise(name));
    }

    private static String normalise(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * OpenStreetMap's standard tiles over HTTPS. JXMapViewer2 2.8's own {@code OSMTileFactoryInfo}
     * still uses {@code http://}, which OpenStreetMap only answers with a redirect to https that Java
     * does not follow, so no tile would ever load.
     */
    static final class OsmTiles extends TileFactoryInfo {

        OsmTiles() {
            super("OpenStreetMap", 0, MAX_ZOOM, MAX_ZOOM, 256, true, true, OSM_TILE_URL, "x", "y", "z");
        }

        @Override
        public String getTileUrl(int x, int y, int zoom) {
            // JXMapViewer counts zoom levels the other way round from the tile servers.
            return OSM_TILE_URL + "/" + (MAX_ZOOM - zoom) + "/" + x + "/" + y + ".png";
        }
    }

    /** CARTO "Dark Matter" tiles (free with attribution), spread over the servers a to d. */
    static final class DarkTiles extends TileFactoryInfo {

        private static final String SERVERS = "abcd";

        DarkTiles() {
            super("CARTO Dark Matter", 0, MAX_ZOOM, MAX_ZOOM, 256, true, true,
                    "https://a.basemaps.cartocdn.com/dark_all", "x", "y", "z");
        }

        @Override
        public String getTileUrl(int x, int y, int zoom) {
            char server = SERVERS.charAt(Math.floorMod(x + y, SERVERS.length()));
            return "https://" + server + ".basemaps.cartocdn.com/dark_all/" + (MAX_ZOOM - zoom) + "/" + x + "/" + y + ".png";
        }
    }
}
