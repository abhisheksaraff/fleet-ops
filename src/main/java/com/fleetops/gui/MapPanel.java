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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final int MAX_TILE_ZOOM = 19;
    private static final int START_ZOOM = 16; // whole of Canada in view (JXMapViewer: smaller = closer)
    private static final int MARGIN = 30;
    private static final int FRAME = 8;
    private static final int BUTTON = 32;
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
        int bottom = getHeight() - MARGIN - 22 - GAP - 6; // above the credit line
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
        paintGraticule(g2);
        paintControls(g2);
        paintLegend(g2);
        paintScale(g2);
        paintAttribution(g2);
        g2.dispose();
    }

    // ---- survey-sheet frame ----------------------------------------------------------------------

    private static final double[] STEPS = {0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 20};
    private static final Color RULE = new Color(0xEB, 0xE4, 0xD0, 120);
    private static final Color RULE_FAINT = new Color(0xEB, 0xE4, 0xD0, 60);

    /** Metres on the ground that one screen pixel covers at the centre of the view. */
    double metresPerPixel() {
        double lat = getCenterPosition().getLatitude();
        int tileZoom = MAX_TILE_ZOOM - getZoom();
        return 156543.03392 * Math.cos(Math.toRadians(lat)) / Math.pow(2, tileZoom);
    }

    /** The centre of the view as a grid reference plus how wide the view is, for the header. */
    String readout() {
        GeoPosition c = getCenterPosition();
        double km = metresPerPixel() * getWidth() / 1000.0;
        String across = km >= 10 ? String.format(Locale.ROOT, "%.0f km", km) : String.format(Locale.ROOT, "%.1f km", km);
        return String.format(Locale.ROOT, "%s%s %s%s  \u00B7  %s across",
                coordinate(Math.abs(c.getLatitude()), 2, 3), c.getLatitude() >= 0 ? "N" : "S",
                coordinate(Math.abs(c.getLongitude()), 3, 3), c.getLongitude() >= 0 ? "E" : "W", across);
    }

    private static String coordinate(double value, int whole, int decimals) {
        return String.format(Locale.ROOT, "%0" + (whole + 1 + decimals) + "." + decimals + "f", value);
    }

    /**
     * Draws the neat line of a survey sheet: a thin frame with graticule ticks every so many degrees,
     * their values in mono, and small crosses where the grid lines would meet.
     */
    private void paintGraticule(Graphics2D g) {
        int w = getWidth();
        int h = getHeight();
        if (w < 200 || h < 160) {
            return;
        }
        Rectangle view = getViewportBounds();
        GeoPosition topLeft = convertPointToGeoPosition(new Point(FRAME, FRAME));
        GeoPosition bottomRight = convertPointToGeoPosition(new Point(w - FRAME, h - FRAME));
        double lonSpan = bottomRight.getLongitude() - topLeft.getLongitude();
        double latSpan = topLeft.getLatitude() - bottomRight.getLatitude();
        if (lonSpan <= 0 || latSpan <= 0) {
            return;
        }
        // the finest spacing that still leaves about 150 px between lines
        double step = STEPS[STEPS.length - 1];
        for (double candidate : STEPS) {
            if (lonSpan / candidate <= Math.max(2, w / 150.0)) {
                step = candidate;
                break;
            }
        }
        GeoPosition mid = getCenterPosition();
        g.setStroke(new BasicStroke(1f));
        g.setColor(RULE);
        g.drawRect(FRAME, FRAME, w - 2 * FRAME, h - 2 * FRAME);
        g.setFont(Theme.mono(10f));
        FontMetrics fm = g.getFontMetrics();
        int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;

        java.util.List<Integer> lonXs = new java.util.ArrayList<>();
        java.util.List<Integer> latYs = new java.util.ArrayList<>();
        for (double lon = Math.ceil(topLeft.getLongitude() / step) * step; lon <= bottomRight.getLongitude() && lonXs.size() < 40; lon += step) {
            Point2D p = getTileFactory().geoToPixel(new GeoPosition(mid.getLatitude(), lon), getZoom());
            int x = (int) Math.round(p.getX() - view.x);
            if (x > FRAME + 24 && x < w - FRAME - 24) {
                lonXs.add(x);
                g.setColor(RULE);
                g.drawLine(x, FRAME, x, FRAME + 7);
                g.drawLine(x, h - FRAME, x, h - FRAME - 7);
                String label = degrees(wrap(lon), decimals, "E", "W");
                g.drawString(label, x - fm.stringWidth(label) / 2, FRAME + 20);
            }
        }
        for (double lat = Math.ceil(bottomRight.getLatitude() / step) * step; lat <= topLeft.getLatitude() && latYs.size() < 40; lat += step) {
            Point2D p = getTileFactory().geoToPixel(new GeoPosition(lat, mid.getLongitude()), getZoom());
            int y = (int) Math.round(p.getY() - view.y);
            if (y > FRAME + 30 && y < h - FRAME - 24) {
                latYs.add(y);
                g.setColor(RULE);
                g.drawLine(FRAME, y, FRAME + 7, y);
                g.drawLine(w - FRAME, y, w - FRAME - 7, y);
                String label = degrees(lat, decimals, "N", "S");
                g.drawString(label, FRAME + 12, y + 4);
            }
        }
        g.setColor(RULE_FAINT);
        for (int x : lonXs) {
            for (int y : latYs) {
                g.drawLine(x - 4, y, x + 4, y);
                g.drawLine(x, y - 4, x, y + 4);
            }
        }
    }

    /** Maps a longitude that has wrapped around the world (such as -200) into -180..180. */
    private static double wrap(double lon) {
        return ((lon + 540) % 360 + 360) % 360 - 180;
    }

    private static String degrees(double value, int decimals, String positive, String negative) {
        return String.format(Locale.ROOT, "%." + decimals + "f\u00B0%s", Math.abs(value), value >= 0 ? positive : negative);
    }

    /** A scale bar in the bottom-left corner, in whole kilometres (or metres when zoomed in far). */
    private void paintScale(Graphics2D g) {
        double metresPerPixel = metresPerPixel();
        double[] nice = {1, 2, 5};
        double length = 50;
        double target = 120 * metresPerPixel; // aim for a bar about 120 px long
        for (double decade = 1; decade <= 1e7; decade *= 10) {
            for (double n : nice) {
                if (n * decade <= target) {
                    length = n * decade;
                }
            }
        }
        int pixels = (int) Math.round(length / metresPerPixel);
        if (pixels < 20) {
            return;
        }
        String label = length >= 1000 ? String.format(Locale.ROOT, "%.0f km", length / 1000) : String.format(Locale.ROOT, "%.0f m", length);
        int x = MARGIN;
        int y = getHeight() - MARGIN - 24 - 30;
        g.setFont(Theme.mono(10f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0x0A, 0x12, 0x1A, 215));
        g.fillRect(x - 8, y - 19, pixels + 16 + fm.stringWidth(label) + 10, 34);
        g.setColor(RULE);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x - 8, y - 19, pixels + 16 + fm.stringWidth(label) + 10, 34);
        int half = pixels / 2;
        g.setColor(Theme.TEXT);
        g.fillRect(x, y, half, 5);
        g.setColor(new Color(0x0A, 0x12, 0x1A));
        g.fillRect(x + half, y, pixels - half, 5);
        g.setColor(Theme.TEXT);
        g.drawRect(x, y, pixels, 5);
        g.drawLine(x, y - 5, x, y);
        g.drawLine(x + pixels, y - 5, x + pixels, y);
        g.setColor(Theme.MUTED);
        g.drawString("0", x - 2, y - 8);
        g.drawString(label, x + pixels + 6, y + 6);
    }

    private void paintControls(Graphics2D g) {
        for (Control control : Control.values()) {
            Rectangle r = rectOf(control);
            g.setColor(new Color(0x0A, 0x12, 0x1A, 240));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(new Color(0x4A, 0x5E, 0x70));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            g.setColor(Theme.TEXT);
            g.setStroke(new BasicStroke(1.6f));
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
        g.setFont(Theme.ui(Font.PLAIN, 11.5f));
        FontMetrics fm = g.getFontMetrics();
        int width = 14;
        for (String name : names) {
            width += 16 + fm.stringWidth(name) + 14;
        }
        int height = 26;
        int x = MARGIN - 8;
        int y = getHeight() - MARGIN - height + 4;
        g.setColor(new Color(0x0A, 0x12, 0x1A, 240));
        g.fillRect(x, y, width, height);
        g.setColor(new Color(0x4A, 0x5E, 0x70));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, width - 1, height - 1);
        int cursor = x + 14;
        for (int i = 0; i < states.length; i++) {
            g.setColor(MapOverlay.colorOf(states[i]));
            g.drawRect(cursor, y + height / 2 - 4, 8, 8);
            g.fillRect(cursor + 2, y + height / 2 - 2, 5, 5);
            g.setColor(Theme.TEXT);
            g.drawString(names[i], cursor + 16, y + (height + fm.getAscent() - fm.getDescent()) / 2);
            cursor += 16 + fm.stringWidth(names[i]) + 14;
        }
    }

    private void paintAttribution(Graphics2D g) {
        g.setFont(Theme.mono(9.5f));
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(attribution) + 14;
        int height = 18;
        int x = getWidth() - MARGIN + 8 - width;
        int y = getHeight() - MARGIN + 4 - height + 4;
        g.setColor(new Color(0x0A, 0x12, 0x1A, 225));
        g.fillRect(x, y, width, height);
        g.setColor(Theme.MUTED);
        g.drawString(attribution, x + 7, y + (height + fm.getAscent() - fm.getDescent()) / 2);
    }
}
