package com.fleetops.gui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

/**
 * Factory for the map's tile source, chosen by {@code MAP_TILES}: {@code dark} (CARTO's Dark Matter,
 * the default, which suits the dark interface and needs {@code CARTO_API_KEY}) or {@code osm} (the standard OpenStreetMap look).
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

    /**
     * Whether the dark map can be used: CARTO refuses tiles without a key, so {@code dark} without
     * one falls back to OpenStreetMap (the caller tells the user why).
     */
    static boolean usesOsm(String name, Optional<String> cartoKey) {
        return isOsm(name) || cartoKey.isEmpty();
    }

    /**
     * @param name the {@code MAP_TILES} value; an unknown name gives the dark map (see {@link #isKnown})
     * @param cartoKey the CARTO basemap key; without it the dark map is replaced by OpenStreetMap
     */
    static TileFactory create(String name, Optional<String> cartoKey) {
        TileFactoryInfo info = usesOsm(name, cartoKey) ? new OsmTiles() : new DarkTiles(cartoKey.orElseThrow());
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
    static String attribution(String name, Optional<String> cartoKey) {
        return usesOsm(name, cartoKey) ? OSM_ATTRIBUTION : DARK_ATTRIBUTION;
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

    /**
     * CARTO "Dark Matter" tiles (free with attribution and a key). The key travels in the query string
     * of every tile request, so the address must never be logged.
     */
    static final class DarkTiles extends TileFactoryInfo {

        private static final String BASE = "https://basemaps.cartocdn.com/rastertiles/dark_all";
        private final String key;

        DarkTiles(String key) {
            super("CARTO Dark Matter", 0, MAX_ZOOM, MAX_ZOOM, 256, true, true, BASE, "x", "y", "z");
            this.key = key;
        }

        @Override
        public String getTileUrl(int x, int y, int zoom) {
            return BASE + "/" + (MAX_ZOOM - zoom) + "/" + x + "/" + y + ".png?key="
                    + URLEncoder.encode(key, StandardCharsets.UTF_8);
        }
    }
}
