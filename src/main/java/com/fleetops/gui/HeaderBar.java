package com.fleetops.gui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * The strip across the top, laid out like the title block of a survey sheet: the FleetOps mark and
 * name, a live readout of where the map is centred, the layout presets as text tabs and buttons that
 * show or hide the sidebar and the console. On macOS it sits in the window's title bar area, so it
 * also works as the place to drag the window.
 */
final class HeaderBar extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int HEIGHT = 54;

    private final transient Workspace workspace;
    private final JToggleButton sidebarToggle = iconToggle(Icons.sidebar(), "Show or hide the incident sidebar");
    private final JToggleButton consoleToggle = iconToggle(Icons.console(), "Show or hide the console");
    private final transient java.util.Map<Workspace.Preset, JToggleButton> presetButtons = new java.util.EnumMap<>(Workspace.Preset.class);
    private final ButtonGroup presetGroup = new ButtonGroup();
    private final JLabel readout = Ui.readout(" ");
    private boolean syncing;

    HeaderBar(Workspace workspace) {
        super(new BorderLayout());
        this.workspace = workspace;
        setBackground(Theme.PANEL);
        setPreferredSize(new Dimension(100, HEIGHT));
        putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        left.setOpaque(false);
        JPanel buttonsPlaceholder = new JPanel();
        buttonsPlaceholder.setOpaque(false);
        buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
        left.add(buttonsPlaceholder);
        left.add(sidebarToggle);
        left.add(new JLabel(Icons.logo(26)));
        JLabel name = new JLabel("FleetOps");
        name.setFont(Theme.serif(21f));
        name.setForeground(Theme.TEXT);
        left.add(name);
        left.add(new Rule());
        left.add(readout);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 12));
        right.setOpaque(false);
        right.add(buildPresets());
        right.add(consoleToggle);
        right.add(Spacer.of(2));

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

    /** Sets the map-centre readout, for example {@code 63.747°N 068.517°W · 1:50 km}. */
    void setReadout(String text) {
        if (!text.equals(readout.getText())) {
            readout.setText(text);
        }
    }

    String readout() {
        return readout.getText();
    }

    private JPanel buildPresets() {
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        tabs.setOpaque(false);
        for (Workspace.Preset preset : Workspace.Preset.values()) {
            Ui.Tab button = new Ui.Tab(preset.label);
            button.setToolTipText(describe(preset));
            button.addActionListener(e -> {
                if (!syncing) {
                    workspace.applyPreset(preset);
                }
            });
            presetGroup.add(button);
            presetButtons.put(preset, button);
            tabs.add(button);
        }
        return tabs;
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
        button.setFocusable(false);
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:3; borderWidth:1; focusWidth:0; background:#0E1823; foreground:#A39F8E"
                        + "; borderColor:#2A3B4A; selectedBackground:#1B2C3B; selectedForeground:#EBE4D0"
                        + "; hoverBackground:#172736; margin:5,7,5,7");
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

    /** The double rule along the bottom edge, as on the frame of a map sheet. */
    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Theme.BORDER);
        g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        g2.setColor(Ui.alpha(Theme.BORDER, 110));
        g2.drawLine(0, getHeight() - 4, getWidth(), getHeight() - 4);
        g2.dispose();
    }

    /** A short vertical hairline between the name and the readout. */
    private static final class Rule extends JPanel {
        private static final long serialVersionUID = 1L;

        Rule() {
            setOpaque(false);
            setPreferredSize(new Dimension(1, 26));
            setBackground(Theme.BORDER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.BORDER);
            g.fillRect(0, 0, 1, getHeight());
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
