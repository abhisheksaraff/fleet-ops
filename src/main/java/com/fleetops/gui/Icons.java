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
            g.draw(new RoundRectangle2D.Float(1.5f, 2.5f, 15, 13, 4, 4));
            g.drawLine(7, 3, 7, 15);
        });
    }

    /** A window with its bottom part highlighted: shows or hides the console. */
    static Icon console() {
        return icon((g, c) -> {
            g.draw(new RoundRectangle2D.Float(1.5f, 2.5f, 15, 13, 4, 4));
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

    /** The FleetOps mark: a small quadcopter seen from above on a mint tile. */
    static Icon logo(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
                g2.setColor(Theme.ACCENT);
                g2.fill(new RoundRectangle2D.Float(0, 0, size, size, size * 0.32f, size * 0.32f));
                g2.setColor(Theme.ON_ACCENT);
                float m = size * 0.27f;
                float far = size - m;
                float r = size * 0.11f;
                g2.setStroke(new BasicStroke(size * 0.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(Math.round(m), Math.round(m), Math.round(far), Math.round(far));
                g2.drawLine(Math.round(far), Math.round(m), Math.round(m), Math.round(far));
                for (float[] p : new float[][] {{m, m}, {far, m}, {m, far}, {far, far}}) {
                    g2.fill(new java.awt.geom.Ellipse2D.Float(p[0] - r, p[1] - r, 2 * r, 2 * r));
                }
                g2.fill(new java.awt.geom.Ellipse2D.Float(size / 2f - r * 0.9f, size / 2f - r * 0.9f, r * 1.8f, r * 1.8f));
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
