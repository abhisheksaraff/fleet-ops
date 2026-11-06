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
import com.fleetops.mission.Mission;
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
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

/**
 * Draws everything FleetOps adds on top of the map tiles: incident squares with round markers, the
 * mission routes and drone search squares, found and rescued survivors, and the moving units as small
 * pixel-art pictures (dots when the map is zoomed far out). Hidden survivors are deliberately never
 * drawn: the operator only learns about them when a drone finds them.
 */
final class MapOverlay implements Painter<JXMapViewer> {

    /** Radius in pixels of the round marker drawn at the centre of every incident. */
    static final int MARKER_RADIUS = 17;
    private static final int LABEL_LIMIT = 12;
    /** JXMapViewer counts zoom the other way round: at this level or closer the pictures are used. */
    private static final int SPRITE_ZOOM = 12;
    private static final int SCALE = 2;

    private static final BufferedImage FOUND_PERSON = Sprites.person(Theme.DANGER);
    private static final BufferedImage RESCUED_PERSON = Sprites.person(Theme.ACCENT);

    private final Supplier<List<Incident>> incidents;
    private final Supplier<SimulationEngine> engine;
    private final Supplier<Incident> selected;
    private final Supplier<List<Mission>> missions;
    private final Map<Unit, Heading> headings = new WeakHashMap<>();
    private boolean animating;

    MapOverlay(Supplier<List<Incident>> incidents, Supplier<SimulationEngine> engine, Supplier<Incident> selected,
            Supplier<List<Mission>> missions) {
        this.incidents = incidents;
        this.engine = engine;
        this.selected = selected;
        this.missions = missions;
    }

    /** True when the last picture had something moving on it (rotors, a waiting survivor's pulse). */
    boolean animating() {
        return animating;
    }

    /** Where a unit was last seen to move from, and the way it faces. */
    private static final class Heading {
        private GeoPoint last;
        private double angle;

        Heading(GeoPoint last) {
            this.last = last;
        }
    }

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int width, int height) {
        g = (Graphics2D) g.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle view = map.getViewportBounds();
        g.translate(-view.x, -view.y);

        animating = false;
        boolean pictures = map.getZoom() <= SPRITE_ZOOM;
        double phase = System.nanoTime() / 1e9;

        List<Incident> all = incidents.get();
        Incident chosen = selected.get();
        for (Incident incident : all) {
            drawArea(g, map, incident, incident == chosen);
        }
        for (Mission mission : missions.get()) {
            drawSweeps(g, map, mission);
        }
        for (Mission mission : missions.get()) {
            drawRoute(g, map, mission);
        }
        SimulationEngine sim = engine.get();
        if (sim != null) {
            for (IncidentZone zone : sim.world().zones()) {
                drawSurvivors(g, map, zone, pictures, phase);
            }
            for (Unit unit : sim.world().fleet().units()) {
                drawUnit(g, map, unit, pictures, phase);
            }
        }
        // markers last so no square or unit hides them; the selected one is topmost and its name
        // tag is placed first, then the others skip any tag that would overlap one already placed
        List<Rectangle> taken = new ArrayList<>();
        if (chosen != null && all.contains(chosen)) {
            taken.add(labelBox(g, map, chosen));
        }
        for (Incident incident : all) {
            if (incident == chosen) {
                continue;
            }
            Rectangle box = labelBox(g, map, incident);
            boolean room = all.size() <= LABEL_LIMIT && taken.stream().noneMatch(box::intersects);
            if (room) {
                taken.add(box);
            }
            drawMarker(g, map, incident, false, room);
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
            Rectangle box = labelBox(g, map, incident);
            g.setFont(Theme.ui(Font.BOLD, 11f));
            FontMetrics lf = g.getFontMetrics();
            g.setColor(new Color(0x15, 0x18, 0x1C, 225));
            g.fill(new RoundRectangle2D.Float(box.x, box.y, box.width, box.height, box.height, box.height));
            g.setColor(isSelected ? base : Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(box.x + 0.5f, box.y + 0.5f, box.width - 1, box.height - 1, box.height, box.height));
            g.setColor(Theme.TEXT);
            g.drawString(labelText(incident), box.x + 8, box.y + (box.height + lf.getAscent() - lf.getDescent()) / 2);
        }
    }

    private static String labelText(Incident incident) {
        return "#" + incident.id().map(String::valueOf).orElse("?") + " " + incident.type().keyword();
    }

    /** Where an incident's name tag goes: under its marker, in world pixels. */
    private static Rectangle labelBox(Graphics2D g, JXMapViewer map, Incident incident) {
        Point2D c = toPixel(map, incident.area().center());
        FontMetrics fm = g.getFontMetrics(Theme.ui(Font.BOLD, 11f));
        int width = fm.stringWidth(labelText(incident)) + 16;
        return new Rectangle((int) Math.round(c.getX()) - width / 2, (int) Math.round(c.getY()) + MARKER_RADIUS + 7, width, 20);
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

    /** The squares each drone of a running mission will search, drawn very faintly. */
    private void drawSweeps(Graphics2D g, JXMapViewer map, Mission mission) {
        g.setStroke(new BasicStroke(1f));
        for (List<GeoArea> cells : mission.plan().sweeps().values()) {
            for (GeoArea cell : cells) {
                Rectangle box = boxOf(map, cell);
                if (box.width < 4) {
                    continue;
                }
                g.setColor(Ui.alpha(Theme.INFO, 14));
                g.fill(box);
                g.setColor(Ui.alpha(Theme.INFO, 80));
                g.draw(box);
            }
        }
    }

    /** The rover's drive from its base to the edge of the incident, as a glowing mint line. */
    private void drawRoute(Graphics2D g, JXMapViewer map, Mission mission) {
        Point2D a = toPixel(map, mission.plan().depot());
        Point2D b = toPixel(map, mission.plan().staging());
        Path2D line = new Path2D.Double();
        line.moveTo(a.getX(), a.getY());
        line.lineTo(b.getX(), b.getY());
        int[] widths = {14, 9, 5};
        int[] alphas = {22, 38, 80};
        for (int i = 0; i < widths.length; i++) {
            g.setColor(Ui.alpha(Theme.ACCENT, alphas[i]));
            g.setStroke(new BasicStroke(widths[i], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
        }
        g.setColor(new Color(0xC8, 0xFF, 0xE8, 230));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        for (Point2D end : List.of(a, b)) {
            g.setColor(new Color(0x10, 0x12, 0x15));
            g.fillOval((int) end.getX() - 5, (int) end.getY() - 5, 10, 10);
            g.setColor(Theme.ACCENT);
            g.setStroke(new BasicStroke(2f));
            g.drawOval((int) end.getX() - 5, (int) end.getY() - 5, 10, 10);
        }
    }

    private void drawSurvivors(Graphics2D g, JXMapViewer map, IncidentZone zone, boolean pictures, double phase) {
        for (Survivor survivor : zone.survivors()) {
            if (survivor.status() == SurvivorStatus.HIDDEN) {
                continue;
            }
            Point2D p = toPixel(map, survivor.position());
            double x = p.getX();
            double y = p.getY();
            boolean found = survivor.status() == SurvivorStatus.FOUND;
            if (found) {
                animating = true;
                double t = (phase * 1.1 + (survivor.id().hashCode() & 7) / 8.0) % 1.0;
                g.setColor(Ui.alpha(Theme.DANGER, (int) (150 * (1 - t))));
                g.setStroke(new BasicStroke(2f));
                double r = 7 + 14 * t;
                g.draw(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            }
            if (pictures) {
                Sprites.draw(g, found ? FOUND_PERSON : RESCUED_PERSON, x, y - 2, 0, SCALE);
            } else {
                g.setColor(found ? Theme.DANGER : Theme.ACCENT);
                int r = found ? 4 : 3;
                g.fillOval((int) x - r, (int) y - r, 2 * r, 2 * r);
            }
        }
    }

    private void drawUnit(Graphics2D g, JXMapViewer map, Unit unit, boolean pictures, double phase) {
        if (unit instanceof Drone && unit.state() == UnitState.IDLE) {
            return; // docked on its rover
        }
        Point2D p = toPixel(map, unit.position());
        double x = p.getX();
        double y = p.getY();
        double heading = headingOf(unit, p, map);
        boolean flying = unit.state() != UnitState.IDLE;
        if (!pictures) {
            drawDot(g, unit, (int) x, (int) y);
            return;
        }
        if (unit instanceof Carrier) {
            Sprites.draw(g, Sprites.rover(), x, y, heading, SCALE);
        } else if (unit instanceof Helicopter) {
            Sprites.draw(g, Sprites.helicopter(), x, y, heading, SCALE);
            Sprites.helicopterRotors(g, x, y, heading, SCALE, flying ? phase : 0);
            animating |= flying;
        } else {
            Sprites.draw(g, Sprites.drone(), x, y, heading, SCALE);
            Sprites.droneRotors(g, x, y, heading, SCALE, phase);
            animating = true;
        }
    }

    /** The plain marker used when the map is zoomed too far out for pictures. */
    private static void drawDot(Graphics2D g, Unit unit, int x, int y) {
        g.setStroke(new BasicStroke(1.5f));
        if (unit instanceof Carrier) {
            RoundRectangle2D body = new RoundRectangle2D.Float(x - 5, y - 5, 10, 10, 4, 4);
            g.setColor(new Color(0x1D, 0x21, 0x26));
            g.fill(body);
            g.setColor(Theme.ACCENT);
            g.draw(body);
        } else if (unit instanceof Helicopter) {
            Polygon diamond = new Polygon(new int[] {x, x + 6, x, x - 6}, new int[] {y - 6, y, y + 6, y}, 4);
            g.setColor(new Color(0xB5, 0x8C, 0xFF));
            g.fill(diamond);
        } else {
            g.setColor(Theme.INFO);
            g.fillOval(x - 3, y - 3, 6, 6);
        }
    }

    /**
     * The way a unit faces, in radians with 0 = north. It is worked out from where the unit moved
     * since it was last turned, and kept while the unit stands still.
     */
    private double headingOf(Unit unit, Point2D now, JXMapViewer map) {
        GeoPoint position = unit.position();
        Heading heading = headings.computeIfAbsent(unit, u -> new Heading(position));
        if (!position.equals(heading.last)) {
            Point2D before = toPixel(map, heading.last);
            double dx = now.getX() - before.getX();
            double dy = now.getY() - before.getY();
            if (Math.hypot(dx, dy) >= 1.0) {
                heading.angle = Math.atan2(dx, -dy);
                heading.last = position;
            }
        }
        return heading.angle;
    }
}
