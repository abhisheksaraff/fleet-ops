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
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.Supplier;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

/**
 * Draws everything FleetOps adds on top of the map tiles: incident squares, found and rescued
 * survivors, and the moving units. Hidden survivors are deliberately never drawn: the operator only
 * learns about them when a drone finds them.
 */
final class MapOverlay implements Painter<JXMapViewer> {

    private static final Color FOUND = new Color(0xE5, 0x20, 0x20);
    private static final Color RESCUED = new Color(0x2E, 0xA0, 0x4F);

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
        Rectangle view = map.getViewportBounds();
        g.translate(-view.x, -view.y);

        for (Incident incident : incidents.get()) {
            drawIncident(g, map, incident, incident == selected.get());
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
        g.dispose();
    }

    static Point2D toPixel(JXMapViewer map, GeoPoint p) {
        return map.getTileFactory().geoToPixel(new GeoPosition(p.lat(), p.lon()), map.getZoom());
    }

    private void drawIncident(Graphics2D g, JXMapViewer map, Incident incident, boolean isSelected) {
        IncidentState state = incident.state();
        if (state == IncidentState.REJECTED || state == IncidentState.DUPLICATE) {
            return;
        }
        Rectangle box = boxOf(map, incident.area());
        Color base = colorOf(state);
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), state == IncidentState.COMPLETED ? 40 : 70));
        g.fill(box);
        g.setColor(base);
        g.setStroke(new BasicStroke(isSelected ? 4f : 2f));
        g.draw(box);
        if (isSelected) {
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 4f}, 0f));
            g.drawRect(box.x - 4, box.y - 4, box.width + 8, box.height + 8);
        }
        g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
        String text = "#" + incident.id().map(String::valueOf).orElse("?") + " " + incident.type().keyword();
        g.setColor(Color.BLACK);
        g.drawString(text, box.x + 4, box.y + 15);
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

    static Color colorOf(IncidentState state) {
        return switch (state) {
            case NEW -> Color.GRAY;
            case VERIFIED -> new Color(0xF0, 0x8A, 0x00);
            case PLANNED -> new Color(0xD4, 0xB0, 0x00);
            case IN_PROGRESS -> new Color(0x1F, 0x6F, 0xD0);
            case COMPLETED -> new Color(0x2E, 0xA0, 0x4F);
            case REJECTED, DUPLICATE -> Color.LIGHT_GRAY;
        };
    }

    private void drawSurvivors(Graphics2D g, JXMapViewer map, IncidentZone zone) {
        for (Survivor survivor : zone.survivors()) {
            if (survivor.status() == SurvivorStatus.HIDDEN) {
                continue;
            }
            Point2D p = toPixel(map, survivor.position());
            boolean found = survivor.status() == SurvivorStatus.FOUND;
            int r = found ? 5 : 3;
            g.setColor(found ? FOUND : RESCUED);
            g.fillOval((int) p.getX() - r, (int) p.getY() - r, 2 * r, 2 * r);
            if (found) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1f));
                g.drawOval((int) p.getX() - r, (int) p.getY() - r, 2 * r, 2 * r);
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
            g.setColor(new Color(0x33, 0x33, 0x33));
            g.fillRect(x - 8, y - 8, 16, 16);
            g.setColor(Color.WHITE);
            g.drawString("R", x - 4, y + 5);
        } else if (unit instanceof Helicopter) {
            Polygon diamond = new Polygon(new int[] {x, x + 9, x, x - 9}, new int[] {y - 9, y, y + 9, y}, 4);
            g.setColor(new Color(0x7B, 0x3F, 0xC4));
            g.fill(diamond);
            g.setColor(Color.WHITE);
            g.draw(diamond);
        } else {
            g.setColor(new Color(0x00, 0x9C, 0xC6));
            g.fillOval(x - 5, y - 5, 10, 10);
            g.setColor(Color.WHITE);
            g.drawOval(x - 5, y - 5, 10, 10);
        }
    }
}
