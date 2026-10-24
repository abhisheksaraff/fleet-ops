package com.fleetops.gui;

import com.fleetops.app.AppContext;
import com.fleetops.config.ConfigException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Entry point of the Swing GUI. This commit only opens the main window and shows the effective
 * configuration; the map, incident list and mission panels are added in later commits.
 */
public final class FleetOpsGui {

    private FleetOpsGui() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FleetOpsGui::start);
    }

    private static void start() {
        AppContext context;
        try {
            context = AppContext.fromEnvironment();
        } catch (ConfigException e) {
            JOptionPane.showMessageDialog(
                    null, e.getMessage(), "FleetOps - configuration error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        JFrame frame = new JFrame("FleetOps - Mission Control");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel heading = new JLabel("FleetOps Mission Control");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        heading.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));

        JTextArea details = new JTextArea(context.config().describe());
        details.setEditable(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        details.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        frame.add(heading, BorderLayout.NORTH);
        frame.add(details, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(640, 320));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
