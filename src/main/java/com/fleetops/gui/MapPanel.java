package com.fleetops.gui;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.incident.Incident;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.event.MouseInputListener;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;

/**
 * The OpenStreetMap view. Drag to pan, wheel to zoom, click an incident square to select it and
 * double-click it to plan a mission.
 */
final class MapPanel extends JXMapViewer {

    private static final long serialVersionUID = 1L;
    private static final int START_ZOOM = 16; // whole of Canada in view (JXMapViewer: smaller = closer)

    private final transient java.util.function.Supplier<List<Incident>> incidents;
    private transient Consumer<Incident> onSelect = i -> {};
    private transient Consumer<Incident> onActivate = i -> {};

    MapPanel(TileFactory tiles, java.util.function.Supplier<List<Incident>> incidents, Painter<JXMapViewer> overlay) {
        this.incidents = incidents;
        setTileFactory(tiles);
        setZoom(START_ZOOM);
        setAddressLocation(new GeoPosition(54.0, -96.0));
        setOverlayPainter(overlay);

        MouseInputListener pan = new PanMouseInputListener(this);
        addMouseListener(pan);
        addMouseMotionListener(pan);
        addMouseWheelListener(new ZoomMouseWheelListenerCursor(this));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                incidentAt(e.getPoint()).ifPresent(incident -> {
                    if (e.getClickCount() >= 2) {
                        onActivate.accept(incident);
                    } else {
                        onSelect.accept(incident);
                    }
                });
            }
        });
    }

    void onSelect(Consumer<Incident> handler) {
        this.onSelect = handler;
    }

    void onActivate(Consumer<Incident> handler) {
        this.onActivate = handler;
    }

    /** The incident square under a screen point; the smallest wins where squares overlap. */
    Optional<Incident> incidentAt(Point screen) {
        GeoPosition position = convertPointToGeoPosition(screen);
        com.fleetops.domain.geo.GeoPoint point = new com.fleetops.domain.geo.GeoPoint(position.getLatitude(), position.getLongitude());
        return incidents.get().stream()
                .filter(i -> i.area().contains(point))
                .min(Comparator.comparingDouble(i -> i.area().areaKm2()));
    }

    /** Zooms so that the given incidents fill most of the view. */
    void fit(List<Incident> toShow) {
        Set<GeoPosition> corners = new HashSet<>();
        for (Incident incident : toShow) {
            GeoArea area = incident.area();
            corners.add(new GeoPosition(area.southWest().lat(), area.southWest().lon()));
            corners.add(new GeoPosition(area.northEast().lat(), area.northEast().lon()));
        }
        if (corners.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (corners.size() == 2) {
            // a single square: leave some room around it
            zoomToBestFit(corners, 0.4);
        } else {
            zoomToBestFit(corners, 0.8);
        }
    }

    /** Zooms so that one incident and one extra point (where the fleet starts) are both in view. */
    void fit(Incident incident, com.fleetops.domain.geo.GeoPoint also) {
        Set<GeoPosition> corners = new HashSet<>();
        GeoArea area = incident.area();
        corners.add(new GeoPosition(area.southWest().lat(), area.southWest().lon()));
        corners.add(new GeoPosition(area.northEast().lat(), area.northEast().lon()));
        corners.add(new GeoPosition(also.lat(), also.lon()));
        if (getWidth() > 0 && getHeight() > 0) {
            zoomToBestFit(corners, 0.8);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g2.getFontMetrics();
        String text = MapTiles.ATTRIBUTION;
        int w = fm.stringWidth(text) + 8;
        int x = getWidth() - w;
        int y = getHeight() - fm.getHeight() - 2;
        g2.setColor(new Color(255, 255, 255, 190));
        g2.fillRect(x, y, w, fm.getHeight() + 2);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(text, x + 4, y + fm.getAscent());
        g2.dispose();
    }
}
