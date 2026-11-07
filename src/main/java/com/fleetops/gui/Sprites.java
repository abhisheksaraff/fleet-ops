package com.fleetops.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Small pixel-art pictures of the units and survivors, drawn from text grids so they can be edited
 * like any other text. Every picture faces up (north); the map turns it to the unit's heading. Moving
 * parts (rotors) are drawn on top by code, so they can spin.
 */
final class Sprites {

    private static final String[] ROVER = {
        "....kkkkkkkk....",
        "...kyMMMMMMyk...",
        ".dkkMwwwwwwMkkd.",
        ".dtkMwwwwwwMktd.",
        ".ddkMMMMMMMMkdd.",
        ".dtkMMMMMMMMktd.",
        ".ddkMmmmmmmMkdd.",
        ".dtkkkkkkkkkktd.",
        ".ddkMsSsSsSMkdd.",
        ".dtkMSsSsSsMktd.",
        ".ddkMsSsSsSMkdd.",
        ".dtkMSsSsSsMktd.",
        ".ddkMMMMMMMMkdd.",
        ".dtkkkkkkkkkktd.",
        "..dkkMMMMMMkkd..",
        "....kkkkkkkk....",
    };

    private static final String[] DRONE = {
        "...............",
        ".cc.........cc.",
        ".ccaa.....aacc.",
        "..aaaa...aaaa..",
        "...aaaa.aaaa...",
        "....aaaaaaa....",
        ".....aBBBa.....",
        ".....aBWBa.....",
        ".....aBBBa.....",
        "....aaaaaaa....",
        "...aaaa.aaaa...",
        "..aaaa...aaaa..",
        ".ccaa.....aacc.",
        ".cc.........cc.",
        "...............",
    };

    private static final String[] HELICOPTER = {
        ".......kk.......",
        ".....kkwwkk.....",
        "....kwwwwwwk....",
        "...kPwwwwwwPk...",
        "...kPPPPPPPPk...",
        "..akPPPPPPPPka..",
        "..akPPPPPPPPka..",
        "..akPPPPPPPPka..",
        "..a.kPPPPPPk.a..",
        "..a..kkPPkk..a..",
        "......kPPk......",
        "......kTTk......",
        "......kPPk......",
        "......kPPk......",
        ".....kTPPTk.....",
        ".....kkTTkk.....",
    };

    private static final String[] PERSON = {
        "..kkk..",
        ".khhhk.",
        ".khhhk.",
        "..kkk..",
        "kbbkbbk",
        ".kbbbk.",
        ".kbbbk.",
        ".kbkbk.",
        ".kk.kk.",
    };

    private static final Map<Character, Color> PALETTE = Map.ofEntries(
            Map.entry('k', new Color(0x0A121A)),
            Map.entry('d', new Color(0x1F2D3A)),
            Map.entry('t', new Color(0x35495A)),
            Map.entry('m', new Color(0x6F9A4B)),
            Map.entry('M', new Color(0x9CCB6B)),
            Map.entry('w', new Color(0x9AD7FF)),
            Map.entry('y', new Color(0xFFD166)),
            Map.entry('s', new Color(0x2B4C7E)),
            Map.entry('S', new Color(0x4F86D6)),
            Map.entry('c', new Color(0xA9B2B8)),
            Map.entry('a', new Color(0x7C8590)),
            Map.entry('B', new Color(0x5AA9FF)),
            Map.entry('W', new Color(0xEBE4D0)),
            Map.entry('P', new Color(0xE3B455)),
            Map.entry('T', new Color(0xA9792A)),
            Map.entry('h', new Color(0xEBCDAA)));

    private static BufferedImage rover;
    private static BufferedImage drone;
    private static BufferedImage helicopter;

    private Sprites() {}

    static synchronized BufferedImage rover() {
        if (rover == null) {
            rover = build(ROVER, Map.of());
        }
        return rover;
    }

    static synchronized BufferedImage drone() {
        if (drone == null) {
            drone = build(DRONE, Map.of());
        }
        return drone;
    }

    static synchronized BufferedImage helicopter() {
        if (helicopter == null) {
            helicopter = build(HELICOPTER, Map.of());
        }
        return helicopter;
    }

    /** A person with their arms out; {@code shirt} is the colour that tells waiting from rescued. */
    static BufferedImage person(Color shirt) {
        return build(PERSON, Map.of('b', shirt));
    }

    /** Draws a sprite centred on a point, turned to a heading (radians, 0 = up) and scaled in whole pixels. */
    static void draw(Graphics2D g0, BufferedImage image, double cx, double cy, double heading, int scale) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(heading);
        at.scale(scale, scale);
        at.translate(-image.getWidth() / 2.0, -image.getHeight() / 2.0);
        g.drawImage(image, at, null);
        g.dispose();
    }

    /** The four rotors of a drone: a faint disc with two blades that turn with {@code phase} (seconds). */
    static void droneRotors(Graphics2D g0, double cx, double cy, double heading, int scale, double phase) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);
        g.rotate(heading);
        double offset = 5.5 * scale;
        for (int i = 0; i < 4; i++) {
            double hx = (i % 2 == 0 ? -offset : offset);
            double hy = (i < 2 ? -offset : offset);
            blades(g, hx, hy, 3.6 * scale, phase * (i % 2 == 0 ? 1 : -1) * 40, new Color(0xEBE4D0));
        }
        g.dispose();
    }

    /** The main rotor and tail rotor of a helicopter. */
    static void helicopterRotors(Graphics2D g0, double cx, double cy, double heading, int scale, double phase) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);
        g.rotate(heading);
        blades(g, 0, -2.5 * scale, 8.0 * scale, phase * 28, new Color(0xEBE4D0));
        blades(g, 0, 6.0 * scale, 2.8 * scale, phase * 45, new Color(0xEBE4D0));
        g.dispose();
    }

    private static void blades(Graphics2D g0, double x, double y, double radius, double angle, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 24));
        g.fillOval((int) -radius, (int) -radius, (int) (2 * radius), (int) (2 * radius));
        g.rotate(angle);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 190));
        g.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.drawLine((int) -radius, 0, (int) radius, 0);
        g.drawLine(0, (int) -radius, 0, (int) radius);
        g.dispose();
    }

    private static BufferedImage build(String[] rows, Map<Character, Color> overrides) {
        int h = rows.length;
        int w = rows[0].length();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            if (rows[y].length() != w) {
                throw new IllegalStateException("Sprite row " + y + " is " + rows[y].length() + " wide, expected " + w);
            }
            for (int x = 0; x < w; x++) {
                char c = rows[y].charAt(x);
                Color color = overrides.getOrDefault(c, PALETTE.get(c));
                if (color != null) {
                    image.setRGB(x, y, color.getRGB());
                }
            }
        }
        return image;
    }
}
