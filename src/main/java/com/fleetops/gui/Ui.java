package com.fleetops.gui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;

/** Small helpers that give components the application's look. */
final class Ui {

    private Ui() {}

    /** The main action: filled with the accent colour. */
    static JButton primary(JButton button) {
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:12; borderWidth:0; focusWidth:0; background:" + Theme.hex(Theme.ACCENT)
                        + "; foreground:" + Theme.hex(Theme.ON_ACCENT)
                        + "; hoverBackground:#6AF0B8; pressedBackground:#33C98A"
                        + "; disabledBackground:#252A30; disabledText:#5E656E; font:+0");
        button.setFont(Theme.ui(Font.BOLD, 13f));
        return button;
    }

    /** A quiet outlined button. */
    static JButton secondary(JButton button) {
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:12; borderWidth:1; focusWidth:0; background:" + Theme.hex(Theme.RAISED)
                        + "; borderColor:" + Theme.hex(Theme.BORDER)
                        + "; hoverBackground:#262B32; pressedBackground:#2E343C"
                        + "; disabledBackground:#16191D; disabledBorderColor:#22262C; disabledText:#4F565F");
        return button;
    }

    /** An outlined button that turns red: for stopping things. */
    static JButton danger(JButton button) {
        secondary(button);
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:12; borderWidth:1; focusWidth:0; background:" + Theme.hex(Theme.RAISED)
                        + "; foreground:" + Theme.hex(Theme.DANGER)
                        + "; borderColor:#5A3036; hoverBackground:#2D1D21; pressedBackground:#3A2227"
                        + "; disabledBackground:#16191D; disabledBorderColor:#22262C; disabledText:#4F565F");
        return button;
    }

    static JLabel caption(String text) {
        JLabel label = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        label.setForeground(Theme.MUTED);
        label.setFont(Theme.ui(Font.BOLD, 11f));
        return label;
    }

    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        return label;
    }

    static Border rounded() {
        return BorderFactory.createCompoundBorder(
                new com.formdev.flatlaf.ui.FlatRoundBorder(), BorderFactory.createEmptyBorder());
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
}
