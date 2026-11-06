package com.fleetops.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.prefs.Preferences;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/**
 * The three resizable areas of the window: a sidebar on the left, the map, and a console at the
 * bottom. The sidebar and the console can be dragged to any size, collapsed, and combined into
 * presets. The layout is remembered between runs.
 */
final class Workspace extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int SIDEBAR_WIDTH = 420;
    private static final int CONSOLE_HEIGHT = 190;

    /** Ready-made arrangements of the window. */
    enum Preset {
        MAP("Map", false, false),
        LIST("Map + list", true, false),
        CONTROL("Control", true, true);

        final String label;
        final boolean sidebar;
        final boolean console;

        Preset(String label, boolean sidebar, boolean console) {
            this.label = label;
            this.sidebar = sidebar;
            this.console = console;
        }
    }

    private final transient SplitRegion sidebar;
    private final transient SplitRegion console;
    private final transient Preferences prefs;
    private transient Runnable onChange = () -> {};

    Workspace(Component sidebarPanel, Component map, Component consolePanel, Preferences prefs) {
        super(new BorderLayout());
        this.prefs = prefs;
        map.setMinimumSize(new Dimension(320, 220));

        JSplitPane inner = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, map);
        JSplitPane outer = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inner, consolePanel);
        sidebar = new SplitRegion(inner, SplitRegion.Side.FIRST, sidebarPanel, SIDEBAR_WIDTH);
        console = new SplitRegion(outer, SplitRegion.Side.SECOND, consolePanel, CONSOLE_HEIGHT);
        sidebar.onChange(() -> onChange.run());
        console.onChange(() -> onChange.run());
        add(outer, BorderLayout.CENTER);

        sidebar.setExpandedSize(read("sidebarWidth", SIDEBAR_WIDTH));
        console.setExpandedSize(read("consoleHeight", CONSOLE_HEIGHT));
        apply(prefs == null || get("sidebarOpen", true), prefs == null || get("consoleOpen", true));
    }

    /** Called whenever a region is opened, closed or resized. */
    void onChange(Runnable listener) {
        this.onChange = listener;
    }

    void applyPreset(Preset preset) {
        apply(preset.sidebar, preset.console);
    }

    void toggleSidebar() {
        sidebar.toggle();
    }

    void toggleConsole() {
        console.toggle();
    }

    boolean sidebarOpen() {
        return sidebar.isOpen();
    }

    boolean consoleOpen() {
        return console.isOpen();
    }

    /** The preset the window currently matches, or null if the user arranged it differently. */
    Preset currentPreset() {
        for (Preset preset : Preset.values()) {
            if (preset.sidebar == sidebarOpen() && preset.console == consoleOpen()) {
                return preset;
            }
        }
        return null;
    }

    void savePreferences() {
        if (prefs == null) {
            return;
        }
        try {
            prefs.putBoolean("sidebarOpen", sidebarOpen());
            prefs.putBoolean("consoleOpen", consoleOpen());
            prefs.putInt("sidebarWidth", sidebar.expandedSize());
            prefs.putInt("consoleHeight", console.expandedSize());
            prefs.flush();
        } catch (java.util.prefs.BackingStoreException | SecurityException | IllegalStateException e) {
            // Not being able to remember the layout is not worth an error.
        }
    }

    private void apply(boolean sidebarOpen, boolean consoleOpen) {
        if (sidebarOpen) {
            sidebar.open();
        } else {
            sidebar.close();
        }
        if (consoleOpen) {
            console.open();
        } else {
            console.close();
        }
    }

    private int read(String key, int fallback) {
        try {
            return prefs == null ? fallback : prefs.getInt(key, fallback);
        } catch (SecurityException | IllegalStateException e) {
            return fallback;
        }
    }

    private boolean get(String key, boolean fallback) {
        try {
            return prefs.getBoolean(key, fallback);
        } catch (SecurityException | IllegalStateException e) {
            return fallback;
        }
    }
}
