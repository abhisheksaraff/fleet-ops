package com.fleetops.gui;

import com.fleetops.app.AppContext;
import com.fleetops.config.ConfigException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Entry point of the Swing GUI: an OpenStreetMap view of incidents, fleet and survivors with the
 * controls to scan the news, plan a mission and watch it run.
 */
public final class FleetOpsGui {

    private FleetOpsGui() {}

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "FleetOps");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        SwingUtilities.invokeLater(FleetOpsGui::start);
    }

    private static void start() {
        Theme.install();
        AppContext context;
        try {
            context = AppContext.fromEnvironment();
            context.repository(); // open the database now so a bad DB_URL is reported before the window opens
        } catch (ConfigException e) {
            JOptionPane.showMessageDialog(
                    null, e.getMessage(), "FleetOps - configuration error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        new MainFrame(context, () -> System.exit(0)).setVisible(true);
    }
}
