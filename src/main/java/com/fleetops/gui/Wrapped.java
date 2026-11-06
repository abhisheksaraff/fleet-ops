package com.fleetops.gui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * A few lines of text wrapped to a fixed width, with an ellipsis when it does not fit. Unlike an HTML
 * label its size does not depend on how Swing scales the markup, so text never runs past its card.
 */
final class Wrapped extends JComponent {

    private static final long serialVersionUID = 1L;

    private final int width;
    private final int maxLines;
    private String text = "";

    Wrapped(int width, int maxLines) {
        this.width = width;
        this.maxLines = maxLines;
        setOpaque(false);
    }

    void setText(String value) {
        String next = value == null ? "" : value.strip();
        if (!next.equals(text)) {
            text = next;
            revalidate();
            repaint();
        }
    }

    String text() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int lines = text.isEmpty() ? 0 : lines(width, fm).size();
        return new Dimension(width, lines * fm.getHeight());
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());
        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();
        int y = fm.getAscent();
        for (String line : lines(Math.min(width, getWidth() == 0 ? width : getWidth()), fm)) {
            g2.drawString(line, 0, y);
            y += fm.getHeight();
        }
        g2.dispose();
    }

    /** Greedy word wrap into at most {@code maxLines} lines; the last one ends in an ellipsis if cut. */
    private List<String> lines(int available, FontMetrics fm) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        boolean cut = false;
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= available) {
                line = new StringBuilder(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            String rest = word;
            while (fm.stringWidth(rest) > available && rest.length() > 1) {
                int n = rest.length();
                while (n > 1 && fm.stringWidth(rest.substring(0, n)) > available) {
                    n--;
                }
                out.add(rest.substring(0, n));
                rest = rest.substring(n);
            }
            line.append(rest);
            if (out.size() >= maxLines) {
                cut = true;
                break;
            }
        }
        if (!cut && !line.isEmpty()) {
            out.add(line.toString());
        }
        if (out.size() > maxLines || cut) {
            out = new ArrayList<>(out.subList(0, Math.min(out.size(), maxLines)));
            cut = true;
        }
        if (cut && !out.isEmpty()) {
            String last = out.get(out.size() - 1);
            String dots = "\u2026";
            while (last.length() > 1 && fm.stringWidth(last + dots) > available) {
                last = last.substring(0, last.length() - 1).stripTrailing();
            }
            out.set(out.size() - 1, last + dots);
        }
        return out;
    }
}
