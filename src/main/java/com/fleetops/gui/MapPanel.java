package com.fleetops.gui;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.event.MouseInputListener;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;

/**
 * The map view. Drag to pan, wheel to zoom, click an incident marker to select it and double-click it
 * to plan a mission. Small floating controls (zoom, fit), a legend and the tile credit sit on top of
 * the map, and the mission card floats in the top-left corner.
 */
final class MapPanel extends JXMapViewer {

    private static final long serialVersionUID = 1L;
    private static final int START_ZOOM = 16; // whole of Canada in view (JXMapViewer: smaller = closer)
    private static final int MARGIN = 14;
    private static final int BUTTON = 34;
    private static final int GAP = 8;

    private enum Control { ZOOM_IN, ZOOM_OUT, FIT }

    private final transient Supplier<List<Incident>> incidents;
    private final String attribution;
    private transient Consumer<Incident> onSelect = i -> {};
    private transient Consumer<Incident> onActivate = i -> {};
    private transient Component card;

    MapPanel(TileFactory tiles, String attribution, Supplier<List<Incident>> incidents, Painter<JXMapViewer> overlay) {
        this.incidents = incidents;
        this.attribution = attribution;
        setLayout(null);
        setBackground(Theme.BG);
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
                Optional<Control> control = controlAt(e.getPoint());
                if (control.isPresent()) {
                    press(control.get());
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
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean hot = controlAt(e.getPoint()).isPresent() || incidentAt(e.getPoint()).isPresent();
                setCursor(Cursor.getPredefinedCursor(hot ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
    }

    void onSelect(Consumer<Incident> handler) {
        this.onSelect = handler;
    }

    void onActivate(Consumer<Incident> handler) {
        this.onActivate = handler;
    }

    /** Hosts a component (the mission card) floating over the map's top-left corner. */
    void setCard(Component floating) {
        card = floating;
        add(floating);
    }

    @Override
    public void doLayout() {
        if (card != null) {
            Dimension size = card.getPreferredSize();
            int height = Math.min(size.height, Math.max(0, getHeight() - 2 * MARGIN));
            card.setBounds(MARGIN, MARGIN, size.width, height);
        }
    }

    /**
     * The incident under a screen point: a round marker wins, otherwise the smallest square that holds
     * the point.
     */
    Optional<Incident> incidentAt(Point screen) {
        Rectangle view = getViewportBounds();
        Incident nearest = null;
        double best = Double.MAX_VALUE;
        for (Incident incident : incidents.get()) {
            if (incident.state() == IncidentState.REJECTED || incident.state() == IncidentState.DUPLICATE) {
                continue;
            }
            Point2D c = MapOverlay.toPixel(this, incident.area().center());
            double distance = Math.hypot(c.getX() - view.x - screen.x, c.getY() - view.y - screen.y);
            if (distance <= MapOverlay.MARKER_RADIUS + 3 && distance < best) {
                best = distance;
                nearest = incident;
            }
        }
        if (nearest != null) {
            return Optional.of(nearest);
        }
        GeoPosition position = convertPointToGeoPosition(screen);
        GeoPoint point = new GeoPoint(position.getLatitude(), position.getLongitude());
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
    void fit(Incident incident, GeoPoint also) {
        Set<GeoPosition> corners = new HashSet<>();
        GeoArea area = incident.area();
        corners.add(new GeoPosition(area.southWest().lat(), area.southWest().lon()));
        corners.add(new GeoPosition(area.northEast().lat(), area.northEast().lon()));
        corners.add(new GeoPosition(also.lat(), also.lon()));
        if (getWidth() > 0 && getHeight() > 0) {
            zoomToBestFit(corners, 0.8);
        }
    }

    // ---- floating controls ----------------------------------------------------------------------

    private Rectangle rectOf(Control control) {
        int x = getWidth() - MARGIN - BUTTON;
        int bottom = getHeight() - MARGIN - 22 - GAP; // above the credit line
        int index = Control.values().length - 1 - control.ordinal();
        return new Rectangle(x, bottom - BUTTON - index * (BUTTON + GAP), BUTTON, BUTTON);
    }

    private Optional<Control> controlAt(Point p) {
        for (Control control : Control.values()) {
            if (rectOf(control).contains(p)) {
                return Optional.of(control);
            }
        }
        return Optional.empty();
    }

    private void press(Control control) {
        switch (control) {
            case ZOOM_IN -> setZoom(getZoom() - 1);
            case ZOOM_OUT -> setZoom(getZoom() + 1);
            case FIT -> fit(incidents.get());
        }
        repaint();
    }

    /** Clicks a floating control; used by tests and by the console later. */
    void zoomIn() {
        press(Control.ZOOM_IN);
    }

    void zoomOut() {
        press(Control.ZOOM_OUT);
    }

    void fitAll() {
        press(Control.FIT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintControls(g2);
        paintLegend(g2);
        paintAttribution(g2);
        g2.dispose();
    }

    private void paintControls(Graphics2D g) {
        for (Control control : Control.values()) {
            Rectangle r = rectOf(control);
            RoundRectangle2D shape = new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 12, 12);
            g.setColor(new Color(0x15, 0x18, 0x1C, 235));
            g.fill(shape);
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(r.x + 0.5f, r.y + 0.5f, r.width - 1, r.height - 1, 12, 12));
            g.setColor(Theme.TEXT);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = r.x + r.width / 2;
            int cy = r.y + r.height / 2;
            switch (control) {
                case ZOOM_IN -> {
                    g.drawLine(cx - 6, cy, cx + 6, cy);
                    g.drawLine(cx, cy - 6, cx, cy + 6);
                }
                case ZOOM_OUT -> g.drawLine(cx - 6, cy, cx + 6, cy);
                case FIT -> {
                    g.draw(new Ellipse2D.Float(cx - 6, cy - 6, 12, 12));
                    g.fill(new Ellipse2D.Float(cx - 2, cy - 2, 4, 4));
                    g.drawLine(cx, cy - 9, cx, cy - 6);
                    g.drawLine(cx, cy + 6, cx, cy + 9);
                    g.drawLine(cx - 9, cy, cx - 6, cy);
                    g.drawLine(cx + 6, cy, cx + 9, cy);
                }
            }
        }
    }

    private void paintLegend(Graphics2D g) {
        IncidentState[] states = {IncidentState.VERIFIED, IncidentState.PLANNED, IncidentState.IN_PROGRESS, IncidentState.COMPLETED};
        String[] names = {"Ready", "Planned", "Rescuing", "Done"};
        g.setFont(Theme.ui(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        int width = 14;
        for (String name : names) {
            width += 14 + fm.stringWidth(name) + 14;
        }
        int height = 24;
        int x = MARGIN;
        int y = getHeight() - MARGIN - height;
        g.setColor(new Color(0x15, 0x18, 0x1C, 235));
        g.fill(new RoundRectangle2D.Float(x, y, width, height, height, height));
        g.setColor(Theme.BORDER);
        g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, height, height));
        int cursor = x + 14;
        for (int i = 0; i < states.length; i++) {
            g.setColor(MapOverlay.colorOf(states[i]));
            g.fill(new Ellipse2D.Float(cursor, y + height / 2f - 4, 8, 8));
            g.setColor(Theme.MUTED);
            g.drawString(names[i], cursor + 14, y + (height + fm.getAscent() - fm.getDescent()) / 2);
            cursor += 14 + fm.stringWidth(names[i]) + 14;
        }
    }

    private void paintAttribution(Graphics2D g) {
        g.setFont(Theme.ui(Font.PLAIN, 10f));
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(attribution) + 16;
        int height = 20;
        int x = getWidth() - MARGIN - width;
        int y = getHeight() - MARGIN - height;
        g.setColor(new Color(0x15, 0x18, 0x1C, 215));
        g.fill(new RoundRectangle2D.Float(x, y, width, height, height, height));
        g.setColor(Theme.MUTED);
        g.drawString(attribution, x + 8, y + (height + fm.getAscent() - fm.getDescent()) / 2);
    }
}
