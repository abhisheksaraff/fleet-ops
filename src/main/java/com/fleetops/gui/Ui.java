package com.fleetops.gui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

/** Small helpers that give components the application's look. */
final class Ui {

    private static final String DISABLED = "; disabledBackground:#101B26; disabledBorderColor:#1D2B38; disabledText:#5B6670";

    private Ui() {}

    /** The main action: filled with ochre. */
    static JButton primary(JButton button) {
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:3; borderWidth:0; focusWidth:0; background:" + Theme.hex(Theme.ACCENT)
                        + "; foreground:" + Theme.hex(Theme.ON_ACCENT)
                        + "; hoverBackground:#EBBE66; pressedBackground:#C4933A"
                        + DISABLED + "; margin:4,10,4,10");
        button.setFont(Theme.ui(Font.BOLD, 13f));
        return button;
    }

    /** A quiet outlined button. */
    static JButton secondary(JButton button) {
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:3; borderWidth:1; focusWidth:0; background:" + Theme.hex(Theme.PANEL)
                        + "; foreground:" + Theme.hex(Theme.TEXT)
                        + "; borderColor:#3A4D5E; hoverBackground:#172736; pressedBackground:#1E3144"
                        + DISABLED + "; margin:4,10,4,10");
        return button;
    }

    /** An outlined button that turns red: for stopping things. */
    static JButton danger(JButton button) {
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:3; borderWidth:1; focusWidth:0; background:" + Theme.hex(Theme.PANEL)
                        + "; foreground:" + Theme.hex(Theme.DANGER)
                        + "; borderColor:#6A3A33; hoverBackground:#2A1A1A; pressedBackground:#3A2220"
                        + DISABLED + "; margin:4,10,4,10");
        return button;
    }

    /** A section heading in the serif face with a hairline rule under it, like a legend title on a map sheet. */
    static JPanel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.serif(16f));
        label.setForeground(Theme.TEXT);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 6, 0)));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        return label;
    }

    /** A small label in the monospaced face, for coordinates and readouts. */
    static JLabel readout(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        label.setFont(Theme.mono(11f));
        return label;
    }

    static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        pane.getViewport().setBackground(Theme.PANEL);
        return pane;
    }

    static JPanel transparent() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** A text tab: plain text that is underlined in ochre when selected. Used for the filters and layout presets. */
    static final class Tab extends JToggleButton {
        private static final long serialVersionUID = 1L;

        Tab(String text) {
            super(text);
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(Theme.ui(Font.PLAIN, 13f));
            setBorder(BorderFactory.createEmptyBorder(4, 2, 7, 2));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.BOLD));
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, fm.stringWidth(getText()) + 4), d.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isSelected();
            boolean hover = getModel().isRollover();
            g2.setFont(on ? getFont().deriveFont(Font.BOLD) : getFont());
            g2.setColor(on ? Theme.TEXT : hover ? Theme.TEXT : Theme.MUTED);
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            g2.drawString(getText(), x, 4 + fm.getAscent());
            if (on) {
                g2.setColor(Theme.ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
            }
            g2.dispose();
        }
    }
}
