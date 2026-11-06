package com.fleetops.gui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * The strip across the top: the FleetOps mark and name, buttons that show or hide the sidebar and
 * the console, and the layout presets. On macOS it sits in the window's title bar area, so it also
 * works as the place to drag the window.
 */
final class HeaderBar extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int HEIGHT = 52;

    private final transient Workspace workspace;
    private final JToggleButton sidebarToggle = iconToggle(Icons.sidebar(), "Show or hide the incident sidebar");
    private final JToggleButton consoleToggle = iconToggle(Icons.console(), "Show or hide the console");
    private final transient java.util.Map<Workspace.Preset, JToggleButton> presetButtons = new java.util.EnumMap<>(Workspace.Preset.class);
    private final ButtonGroup presetGroup = new ButtonGroup();
    private boolean syncing;

    HeaderBar(Workspace workspace) {
        super(new BorderLayout());
        this.workspace = workspace;
        setBackground(Theme.PANEL);
        setPreferredSize(new Dimension(100, HEIGHT));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        left.setOpaque(false);
        JPanel buttonsPlaceholder = new JPanel();
        buttonsPlaceholder.setOpaque(false);
        buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
        left.add(buttonsPlaceholder);
        left.add(sidebarToggle);
        left.add(new JLabel(Icons.logo(28)));
        JLabel name = new JLabel("fleetops");
        name.setFont(Theme.ui(Font.BOLD, 18f));
        left.add(name);
        JLabel tagline = Ui.muted("mission control");
        tagline.setFont(Theme.ui(Font.PLAIN, 12f));
        left.add(tagline);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        right.setOpaque(false);
        right.add(buildPresets());
        right.add(consoleToggle);
        right.add(Spacer.of(4));

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        sidebarToggle.addActionListener(e -> {
            if (!syncing) {
                workspace.toggleSidebar();
            }
        });
        consoleToggle.addActionListener(e -> {
            if (!syncing) {
                workspace.toggleConsole();
            }
        });
        workspace.onChange(this::sync);
        sync();
    }

    private JPanel buildPresets() {
        JPanel segmented = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.RAISED);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        segmented.setOpaque(false);
        for (Workspace.Preset preset : Workspace.Preset.values()) {
            JToggleButton button = new JToggleButton(preset.label);
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
            button.putClientProperty(FlatClientProperties.STYLE,
                    "arc:10; borderWidth:0; focusWidth:0; background:#1D2126; foreground:#8B939D"
                            + "; selectedBackground:#2B323A; selectedForeground:#46E0A0"
                            + "; hoverBackground:#242A30; margin:4,12,4,12");
            button.setToolTipText(describe(preset));
            button.addActionListener(e -> {
                if (!syncing) {
                    workspace.applyPreset(preset);
                }
            });
            presetGroup.add(button);
            presetButtons.put(preset, button);
            segmented.add(button);
        }
        return segmented;
    }

    private static String describe(Workspace.Preset preset) {
        return switch (preset) {
            case MAP -> "Only the map";
            case LIST -> "Map with the incident sidebar";
            case CONTROL -> "Map, sidebar and console";
        };
    }

    private static JToggleButton iconToggle(javax.swing.Icon icon, String tooltip) {
        JToggleButton button = new JToggleButton(icon);
        button.setToolTipText(tooltip);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:10; borderWidth:0; focusWidth:0; background:#15181C; foreground:#8B939D"
                        + "; selectedBackground:#2B323A; selectedForeground:#46E0A0; hoverBackground:#242A30"
                        + "; margin:5,7,5,7");
        return button;
    }

    /** Brings the buttons in line with what the workspace currently shows. */
    private void sync() {
        syncing = true;
        try {
            sidebarToggle.setSelected(workspace.sidebarOpen());
            consoleToggle.setSelected(workspace.consoleOpen());
            Workspace.Preset current = workspace.currentPreset();
            if (current == null) {
                presetGroup.clearSelection();
            } else {
                presetButtons.get(current).setSelected(true);
            }
        } finally {
            syncing = false;
        }
    }

    /** Tiny spacer so the right edge does not touch the window frame. */
    private static final class Spacer {
        static java.awt.Component of(int width) {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(width, 1));
            return panel;
        }
    }
}
