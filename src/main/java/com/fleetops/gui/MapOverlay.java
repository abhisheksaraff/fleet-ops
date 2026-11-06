package com.fleetops.gui;

import com.fleetops.domain.fleet.Drone;
import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.fleet.Carrier;
import com.fleetops.domain.fleet.Unit;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.domain.incident.Survivor;
import com.fleetops.domain.incident.SurvivorStatus;
import com.fleetops.sim.IncidentZone;
import com.fleetops.sim.SimulationEngine;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Supplier;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

/**
 * Draws everything FleetOps adds on top of the map tiles: incident squares with round markers, found
 * and rescued survivors, and the moving units. Hidden survivors are deliberately never drawn: the operator only
 * learns about them when a drone finds them.
 */
final class MapOverlay implements Painter<JXMapViewer> {

    /** Radius in pixels of the round marker drawn at the centre of every incident. */
    static final int MARKER_RADIUS = 17;
    private static final int LABEL_LIMIT = 12;

    private final Supplier<List<Incident>> incidents;
    private final Supplier<SimulationEngine> engine;
    private final Supplier<Incident> selected;

    MapOverlay(Supplier<List<Incident>> incidents, Supplier<SimulationEngine> engine, Supplier<Incident> selected) {
        this.incidents = incidents;
        this.engine = engine;
        this.selected = selected;
    }

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int width, int height) {
        g = (Graphics2D) g.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle view = map.getViewportBounds();
        g.translate(-view.x, -view.y);

        List<Incident> all = incidents.get();
        Incident chosen = selected.get();
        for (Incident incident : all) {
            drawArea(g, map, incident, incident == chosen);
        }
        SimulationEngine sim = engine.get();
        if (sim != null) {
            for (IncidentZone zone : sim.world().zones()) {
                drawSurvivors(g, map, zone);
            }
            for (Unit unit : sim.world().fleet().units()) {
                drawUnit(g, map, unit);
            }
        }
        // markers last so no square or unit hides them; the selected one is topmost
        for (Incident incident : all) {
            if (incident != chosen) {
                drawMarker(g, map, incident, false, all.size() <= LABEL_LIMIT);
            }
        }
        if (chosen != null && all.contains(chosen)) {
            drawMarker(g, map, chosen, true, true);
        }
        g.dispose();
    }

    static Point2D toPixel(JXMapViewer map, GeoPoint p) {
        return map.getTileFactory().geoToPixel(new GeoPosition(p.lat(), p.lon()), map.getZoom());
    }

    /** The translucent square that shows the area an incident covers. */
    private void drawArea(Graphics2D g, JXMapViewer map, Incident incident, boolean isSelected) {
        IncidentState state = incident.state();
        if (state == IncidentState.REJECTED || state == IncidentState.DUPLICATE) {
            return;
        }
        Rectangle box = boxOf(map, incident.area());
        Color base = colorOf(state);
        g.setColor(Ui.alpha(base, state == IncidentState.COMPLETED ? 16 : 30));
        g.fill(box);
        g.setColor(Ui.alpha(base, isSelected ? 255 : 150));
        g.setStroke(new BasicStroke(isSelected ? 2.4f : 1.4f));
        g.draw(box);
    }

    /** A round badge at the centre: the disaster's icon, a people count and (when room) a name tag. */
    private void drawMarker(Graphics2D g, JXMapViewer map, Incident incident, boolean isSelected, boolean label) {
        IncidentState state = incident.state();
        if (state == IncidentState.REJECTED || state == IncidentState.DUPLICATE) {
            return;
        }
        Point2D c = toPixel(map, incident.area().center());
        int cx = (int) Math.round(c.getX());
        int cy = (int) Math.round(c.getY());
        Color base = colorOf(state);
        int r = MARKER_RADIUS;

        if (isSelected) {
            for (int i = 4; i >= 1; i--) {
                g.setColor(Ui.alpha(base, 18 * (5 - i)));
                g.setStroke(new BasicStroke(i * 3f));
                g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
            }
        }
        g.setColor(new Color(0x15, 0x18, 0x1C, 240));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(base);
        g.setStroke(new BasicStroke(isSelected ? 3f : 2.2f));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        IncidentList.paintTypeIcon(g, incident.type(), cx, cy, r - 3, IncidentList.typeColor(incident.type()));

        // people badge, top right
        String count = incident.estimatedPeople() > 99 ? "99+" : String.valueOf(incident.estimatedPeople());
        g.setFont(Theme.ui(Font.BOLD, 10f));
        FontMetrics fm = g.getFontMetrics();
        int bw = Math.max(18, fm.stringWidth(count) + 10);
        int bh = 17;
        int bx = cx + r - bw / 2 - 1;
        int by = cy - r - bh / 2 + 3;
        g.setColor(new Color(0x15, 0x18, 0x1C));
        g.fill(new RoundRectangle2D.Float(bx - 2, by - 2, bw + 4, bh + 4, bh + 4, bh + 4));
        g.setColor(IncidentList.severityColor(incident.severity()));
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        g.setColor(Theme.ON_ACCENT);
        g.drawString(count, bx + (bw - fm.stringWidth(count)) / 2, by + (bh + fm.getAscent() - fm.getDescent()) / 2);

        if (label) {
            String text = "#" + incident.id().map(String::valueOf).orElse("?") + " " + incident.type().keyword();
            g.setFont(Theme.ui(Font.BOLD, 11f));
            FontMetrics lf = g.getFontMetrics();
            int lw = lf.stringWidth(text) + 16;
            int lh = 20;
            int lx = cx - lw / 2;
            int ly = cy + r + 7;
            g.setColor(new Color(0x15, 0x18, 0x1C, 225));
            g.fill(new RoundRectangle2D.Float(lx, ly, lw, lh, lh, lh));
            g.setColor(isSelected ? base : Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(lx + 0.5f, ly + 0.5f, lw - 1, lh - 1, lh, lh));
            g.setColor(Theme.TEXT);
            g.drawString(text, lx + 8, ly + (lh + lf.getAscent() - lf.getDescent()) / 2);
        }
    }

    private static Rectangle boxOf(JXMapViewer map, GeoArea area) {
        Point2D sw = toPixel(map, area.southWest());
        Point2D ne = toPixel(map, area.northEast());
        int x = (int) Math.round(Math.min(sw.getX(), ne.getX()));
        int y = (int) Math.round(Math.min(sw.getY(), ne.getY()));
        int w = (int) Math.round(Math.abs(ne.getX() - sw.getX()));
        int h = (int) Math.round(Math.abs(ne.getY() - sw.getY()));
        return new Rectangle(x, y, Math.max(w, 6), Math.max(h, 6));
    }

    /** The colour of an incident in a given state, shared by squares, markers and the legend. */
    static Color colorOf(IncidentState state) {
        return switch (state) {
            case NEW -> Theme.MUTED;
            case VERIFIED -> Theme.INFO;
            case PLANNED -> Theme.WARN;
            case IN_PROGRESS -> Theme.ACCENT;
            case COMPLETED -> new Color(0x4C, 0x9F, 0x7A);
            case REJECTED, DUPLICATE -> Theme.BORDER;
        };
    }

    private void drawSurvivors(Graphics2D g, JXMapViewer map, IncidentZone zone) {
        for (Survivor survivor : zone.survivors()) {
            if (survivor.status() == SurvivorStatus.HIDDEN) {
                continue;
            }
            Point2D p = toPixel(map, survivor.position());
            int x = (int) p.getX();
            int y = (int) p.getY();
            if (survivor.status() == SurvivorStatus.FOUND) {
                g.setColor(Ui.alpha(Theme.DANGER, 60));
                g.fillOval(x - 10, y - 10, 20, 20);
                g.setColor(Theme.DANGER);
                g.fillOval(x - 4, y - 4, 8, 8);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1f));
                g.drawOval(x - 4, y - 4, 8, 8);
            } else {
                g.setColor(Ui.alpha(Theme.ACCENT, 70));
                g.fillOval(x - 6, y - 6, 12, 12);
                g.setColor(Theme.ACCENT);
                g.fillOval(x - 3, y - 3, 6, 6);
            }
        }
    }

    private void drawUnit(Graphics2D g, JXMapViewer map, Unit unit) {
        if (unit instanceof Drone && unit.state() == UnitState.IDLE) {
            return; // docked on its rover
        }
        Point2D p = toPixel(map, unit.position());
        int x = (int) p.getX();
        int y = (int) p.getY();
        g.setStroke(new BasicStroke(1.5f));
        if (unit instanceof Carrier) {
            RoundRectangle2D body = new RoundRectangle2D.Float(x - 10, y - 10, 20, 20, 7, 7);
            g.setColor(new Color(0x1D, 0x21, 0x26));
            g.fill(body);
            g.setColor(Theme.ACCENT);
            g.draw(body);
            g.setFont(Theme.ui(Font.BOLD, 11f));
            g.setColor(Theme.TEXT);
            g.drawString("R", x - 4, y + 4);
        } else if (unit instanceof Helicopter) {
            Polygon diamond = new Polygon(new int[] {x, x + 10, x, x - 10}, new int[] {y - 10, y, y + 10, y}, 4);
            g.setColor(new Color(0xB5, 0x8C, 0xFF));
            g.fill(diamond);
            g.setColor(Color.WHITE);
            g.draw(diamond);
        } else {
            g.setColor(Ui.alpha(Theme.INFO, 70));
            g.fillOval(x - 9, y - 9, 18, 18);
            g.setColor(Theme.INFO);
            g.fillOval(x - 5, y - 5, 10, 10);
            g.setColor(Color.WHITE);
            g.drawOval(x - 5, y - 5, 10, 10);
        }
    }
}
