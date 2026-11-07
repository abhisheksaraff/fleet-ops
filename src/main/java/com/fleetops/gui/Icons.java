package com.fleetops.gui;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BiConsumer;
import javax.swing.Icon;

/** Small vector icons, drawn in code so no image files are needed. They take the button's text colour. */
final class Icons {

    private static final int SIZE = 18;

    private Icons() {}

    /** A window with its left part highlighted: shows or hides the sidebar. */
    static Icon sidebar() {
        return icon((g, c) -> {
            g.draw(new RoundRectangle2D.Float(1.5f, 2.5f, 15, 13, 1.5f, 1.5f));
            g.drawLine(7, 3, 7, 15);
        });
    }

    /** A window with its bottom part highlighted: shows or hides the console. */
    static Icon console() {
        return icon((g, c) -> {
            g.draw(new RoundRectangle2D.Float(1.5f, 2.5f, 15, 13, 1.5f, 1.5f));
            g.drawLine(2, 10, 16, 10);
            g.drawLine(5, 12, 7, 13);
            g.drawLine(7, 13, 5, 14);
        });
    }

    /** A magnifying glass in the muted colour, for the search box. */
    static Icon search() {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
                g2.setColor(Theme.MUTED);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new java.awt.geom.Ellipse2D.Float(2, 2, 8.5f, 8.5f));
                g2.drawLine(10, 10, 14, 14);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }

    private static Icon icon(BiConsumer<Graphics2D, Component> painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(c.isEnabled() ? c.getForeground() : Theme.MUTED);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                painter.accept(g2, c);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        };
    }

    /** The FleetOps mark: a survey point, a ring with crosshair ticks and a dot, drawn in ochre. */
    static Icon logo(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(Theme.ACCENT);
                float mid = size / 2f;
                float ring = size * 0.30f;
                g2.setStroke(new BasicStroke(Math.max(1.4f, size * 0.07f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                g2.draw(new java.awt.geom.Ellipse2D.Float(mid - ring, mid - ring, ring * 2, ring * 2));
                float tick = size * 0.16f;
                float edge = size - 1f;
                g2.draw(new java.awt.geom.Line2D.Float(mid, 1, mid, 1 + tick + size * 0.04f));
                g2.draw(new java.awt.geom.Line2D.Float(mid, edge, mid, edge - tick - size * 0.04f));
                g2.draw(new java.awt.geom.Line2D.Float(1, mid, 1 + tick + size * 0.04f, mid));
                g2.draw(new java.awt.geom.Line2D.Float(edge, mid, edge - tick - size * 0.04f, mid));
                float dot = size * 0.075f;
                g2.fill(new java.awt.geom.Ellipse2D.Float(mid - dot, mid - dot, dot * 2, dot * 2));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
}
