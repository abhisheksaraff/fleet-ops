package com.fleetops.gui;

import com.fleetops.domain.incident.Incident;
import com.fleetops.plan.MissionPlan;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * The window that asks the operator to approve a mission plan: the headline numbers as tiles, the
 * planner's reasoning, any notes, the full plan text, and Cancel / Launch buttons.
 */
final class PlanDialog {

    private PlanDialog() {}

    /** Shows the plan and returns true when the operator chose Launch. */
    static boolean confirm(Component parent, MissionPlan plan) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Approve this mission?", JDialog.DEFAULT_MODALITY_TYPE);
        boolean[] launched = new boolean[1];

        Incident incident = plan.incident();
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        // ---- heading -----------------------------------------------------------------------------
        Wrapped name = new Wrapped(580, 2);
        name.setText(incident.title());
        name.setFont(Theme.ui(Font.BOLD, 17f));
        name.setForeground(Theme.TEXT);
        JLabel sub = new JLabel(String.format(Locale.ROOT, "%s · %s · about %d people · planned by %s",
                titleCase(incident.type().keyword()), titleCase(incident.severity().name()),
                incident.estimatedPeople(), plan.decision().plannedBy()));
        sub.setFont(Theme.ui(Font.PLAIN, 12f));
        sub.setForeground(Theme.MUTED);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(name);
        names.add(Box.createVerticalStrut(4));
        names.add(sub);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel heading = new JPanel(new BorderLayout(14, 0));
        heading.setOpaque(false);
        heading.add(new TypeBadge(incident), BorderLayout.WEST);
        heading.add(names, BorderLayout.CENTER);

        // ---- body --------------------------------------------------------------------------------
        JPanel tiles = new JPanel(new GridLayout(1, 4, 10, 0));
        tiles.setOpaque(false);
        tiles.add(new Tile(plan.carrier().id(), "Rover"));
        tiles.add(new Tile(String.valueOf(plan.drones().size()), "Drones"));
        tiles.add(new Tile(String.valueOf(plan.decision().helicopters()), "Helicopters"));
        tiles.add(new Tile(String.format(Locale.ROOT, "%.1f km", plan.depot().distanceKmTo(plan.staging())), "Drive"));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(left(tiles));
        body.add(Box.createVerticalStrut(14));
        body.add(left(Ui.caption("Why this plan")));
        body.add(Box.createVerticalStrut(4));
        Wrapped why = new Wrapped(640, 4);
        why.setText(plan.decision().rationale());
        why.setFont(Theme.ui(Font.PLAIN, 13f));
        why.setForeground(new Color(0xC4CAD1));
        body.add(left(why));
        for (String note : plan.notes()) {
            body.add(Box.createVerticalStrut(6));
            Wrapped n = new Wrapped(640, 3);
            n.setText("\u26A0 " + note);
            n.setFont(Theme.ui(Font.PLAIN, 12f));
            n.setForeground(Theme.WARN);
            body.add(left(n));
        }
        body.add(Box.createVerticalStrut(14));
        body.add(left(Ui.caption("Full plan")));
        body.add(Box.createVerticalStrut(4));
        JTextArea text = new JTextArea(plan.describe(), 7, 56);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.mono(12f));
        text.setForeground(Theme.TEXT);
        text.setBackground(Theme.CONSOLE_BG);
        text.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(640, 150));
        body.add(left(scroll));

        // ---- buttons -----------------------------------------------------------------------------
        JButton cancel = Ui.secondary(new JButton("Cancel"));
        JButton launch = Ui.primary(new JButton("Launch"));
        cancel.addActionListener(e -> dialog.dispose());
        launch.addActionListener(e -> {
            launched[0] = true;
            dialog.dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        cancel.setPreferredSize(new Dimension(96, 36));
        launch.setPreferredSize(new Dimension(120, 36));
        buttons.add(cancel);
        buttons.add(launch);

        root.add(heading, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(launch);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true); // blocks until the dialog is disposed
        return launched[0];
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static String titleCase(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
    }

    /** A rounded tile with one big value and a caption. */
    private static final class Tile extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String value;
        private final String caption;

        Tile(String value, String caption) {
            this.value = value;
            this.caption = caption;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(110, 62);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Theme.RAISED);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            g2.setColor(Theme.BORDER);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 16, 16));
            g2.setFont(Theme.ui(Font.BOLD, 17f));
            g2.setColor(Theme.TEXT);
            g2.drawString(fit(g2, value, getWidth() - 20), 12, 27);
            g2.setFont(Theme.ui(Font.PLAIN, 11f));
            g2.setColor(Theme.MUTED);
            g2.drawString(caption, 12, 47);
            g2.dispose();
        }

        private static String fit(Graphics2D g, String text, int width) {
            java.awt.FontMetrics fm = g.getFontMetrics();
            String out = text;
            while (out.length() > 1 && fm.stringWidth(out) > width) {
                out = out.substring(0, out.length() - 1);
            }
            return out;
        }
    }

    /** The round disaster icon in the heading. */
    private static final class TypeBadge extends JComponent {
        private static final long serialVersionUID = 1L;
        private final transient Incident incident;

        TypeBadge(Incident incident) {
            this.incident = incident;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(48, 48);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            IncidentList.paintTypeIcon(g2, incident.type(), 24, 24, 24, IncidentList.typeColor(incident.type()));
            g2.dispose();
        }
    }
}
