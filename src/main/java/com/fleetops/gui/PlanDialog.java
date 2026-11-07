package com.fleetops.gui;

import com.fleetops.domain.incident.Incident;
import com.fleetops.plan.MissionPlan;
import com.fleetops.strategy.SearchStrategies;
import com.fleetops.strategy.SearchStrategy;
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
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * The window that asks the operator to approve a mission plan: the headline numbers as tiles, controls
 * to change how many drones, robodogs and helicopters to send and which search pattern to fly, the planner's
 * reasoning, any notes, the full plan text, and Cancel / Launch buttons. Every change is compiled and
 * checked again, so the operator can only approve a plan that can actually be carried out.
 */
final class PlanDialog {

    /** Plans again with the operator's choices, and says how far the choices may go. */
    interface Reviser {
        /**
         * @return the plan for these choices
         * @throws RuntimeException with a readable message if they cannot be carried out
         */
        MissionPlan revise(int drones, SearchStrategy strategy, int helicopters, int robodogs);

        /** The most drones that are ready to be sent. */
        int maxDrones();

        /** The most helicopters that are ready to be sent. */
        int maxHelicopters();

        /** The most robodogs that are ready to be dropped off. */
        int maxRobodogs();
    }

    private final Component parent;
    private final MissionPlan original;
    private final Reviser reviser;
    private final JDialog dialog;

    private final Tile roverTile = new Tile("Rover");
    private final Tile droneTile = new Tile("Drones");
    private final Tile robodogTile = new Tile("Robodogs");
    private final Tile helicopterTile = new Tile("Helicopters");
    private final Tile driveTile = new Tile("Drive");
    private final JLabel sub = new JLabel(" ");
    private final Wrapped why = new Wrapped(640, 4);
    private final JPanel notes = new JPanel();
    private final JTextArea text = new JTextArea("", 7, 56);
    private final JSpinner droneSpinner;
    private final JSpinner robodogSpinner;
    private final JSpinner helicopterSpinner;
    private final JComboBox<String> pattern = new JComboBox<>(
            SearchStrategies.all().stream().map(SearchStrategy::name).toArray(String[]::new));
    private final Wrapped problem = new Wrapped(640, 3);
    private final JButton back = Ui.secondary(new JButton("Back to original plan"));
    private final JButton cancel = Ui.secondary(new JButton("Cancel"));
    private final JButton launch = Ui.primary(new JButton("Launch"));

    private MissionPlan current;
    private MissionPlan result;
    private boolean syncing;

    private PlanDialog(Component parent, MissionPlan plan, Reviser reviser) {
        this.parent = parent;
        this.original = plan;
        this.reviser = reviser;
        this.current = plan;
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        this.dialog = new JDialog(owner, "Approve this mission?", JDialog.DEFAULT_MODALITY_TYPE);
        int drones = plan.drones().size();
        droneSpinner = new JSpinner(new SpinnerNumberModel(drones, 1, Math.max(drones, reviser.maxDrones()), 1));
        int robodogs = plan.decision().robodogs();
        robodogSpinner = new JSpinner(new SpinnerNumberModel(robodogs, 0, Math.max(robodogs, reviser.maxRobodogs()), 1));
        int helicopters = plan.decision().helicopters();
        helicopterSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(1, helicopters), 1, Math.max(Math.max(1, helicopters), reviser.maxHelicopters()), 1));
        pattern.setSelectedItem(plan.decision().strategy().name());
        build();
        refresh(plan);
    }

    /**
     * Shows the plan and lets the operator change it.
     *
     * @return the plan to launch (the original or the operator's version), or empty if the operator cancelled
     */
    static Optional<MissionPlan> review(Component parent, MissionPlan plan, Reviser reviser) {
        PlanDialog review = new PlanDialog(parent, plan, reviser);
        review.dialog.setLocationRelativeTo(parent);
        review.dialog.setVisible(true); // blocks until the dialog is disposed
        return Optional.ofNullable(review.result);
    }

    // ---- layout ---------------------------------------------------------------------------------

    private void build() {
        Incident incident = original.incident();
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        Wrapped name = new Wrapped(580, 2);
        name.setText(incident.title());
        name.setFont(Theme.serif(18f));
        name.setForeground(Theme.TEXT);
        sub.setFont(Theme.mono(11f));
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

        JPanel tiles = new JPanel(new GridLayout(1, 5, 10, 0));
        tiles.setOpaque(false);
        tiles.add(roverTile);
        tiles.add(droneTile);
        tiles.add(robodogTile);
        tiles.add(helicopterTile);
        tiles.add(driveTile);

        Wrapped hint = new Wrapped(640, 2);
        hint.setText("Change what to send. The plan is worked out and checked again against the drones' batteries "
                + "and the rover's range before you can launch it.");
        hint.setFont(Theme.ui(Font.PLAIN, 12f));
        hint.setForeground(Theme.MUTED);
        JPanel adjust = new JPanel(new GridLayout(1, 5, 10, 0));
        adjust.setOpaque(false);
        adjust.add(labelled("Drones to send", droneSpinner));
        adjust.add(labelled("Search pattern", pattern));
        adjust.add(labelled("Robodogs to drop", robodogSpinner));
        adjust.add(labelled("Helicopters ready", helicopterSpinner));
        JPanel backBox = new JPanel(new BorderLayout());
        backBox.setOpaque(false);
        backBox.add(back, BorderLayout.SOUTH);
        adjust.add(backBox);
        problem.setFont(Theme.ui(Font.PLAIN, 12f));
        problem.setForeground(Theme.DANGER);
        droneSpinner.addChangeListener(e -> onEdit());
        robodogSpinner.addChangeListener(e -> onEdit());
        helicopterSpinner.addChangeListener(e -> onEdit());
        pattern.addActionListener(e -> onEdit());
        back.addActionListener(e -> goBack());

        why.setFont(Theme.ui(Font.PLAIN, 13f));
        why.setForeground(new Color(0xCFC9B5));
        notes.setOpaque(false);
        notes.setLayout(new BoxLayout(notes, BoxLayout.Y_AXIS));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.mono(12f));
        text.setForeground(Theme.TEXT);
        text.setBackground(Theme.CONSOLE_BG);
        text.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(640, 130));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(left(tiles));
        body.add(Box.createVerticalStrut(14));
        body.add(left(Ui.heading("Adjust the plan")));
        body.add(Box.createVerticalStrut(4));
        body.add(left(hint));
        body.add(Box.createVerticalStrut(8));
        body.add(left(adjust));
        body.add(Box.createVerticalStrut(4));
        body.add(left(problem));
        body.add(Box.createVerticalStrut(10));
        body.add(left(Ui.heading("Why this plan")));
        body.add(Box.createVerticalStrut(4));
        body.add(left(why));
        body.add(left(notes));
        body.add(Box.createVerticalStrut(12));
        body.add(left(Ui.heading("Full plan")));
        body.add(Box.createVerticalStrut(4));
        body.add(left(scroll));

        cancel.addActionListener(e -> dialog.dispose());
        launch.addActionListener(e -> {
            result = current;
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
    }

    // ---- editing --------------------------------------------------------------------------------

    /** The operator changed a control: plan again and show the result, or say why it cannot be done. */
    private void onEdit() {
        if (syncing) {
            return;
        }
        int drones = (Integer) droneSpinner.getValue();
        int helicopters = (Integer) helicopterSpinner.getValue();
        int robodogs = (Integer) robodogSpinner.getValue();
        SearchStrategy strategy = SearchStrategies.parse((String) pattern.getSelectedItem());
        try {
            MissionPlan revised = sameAsOriginal(drones, strategy, helicopters, robodogs)
                    ? original : reviser.revise(drones, strategy, helicopters, robodogs);
            current = revised;
            showProblem(null);
            refresh(revised);
            // The compiler may have needed more drones than were asked for; show what the plan really uses.
            syncControls(revised);
        } catch (RuntimeException e) {
            showProblem(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private boolean sameAsOriginal(int drones, SearchStrategy strategy, int helicopters, int robodogs) {
        return drones == original.drones().size()
                && robodogs == original.decision().robodogs()
                && strategy.name().equals(original.decision().strategy().name())
                && helicopters == original.decision().helicopters();
    }

    private void goBack() {
        current = original;
        showProblem(null);
        refresh(original);
        syncControls(original);
    }

    private void syncControls(MissionPlan plan) {
        syncing = true;
        try {
            droneSpinner.setValue(plan.drones().size());
            robodogSpinner.setValue(plan.decision().robodogs());
            helicopterSpinner.setValue(Math.max(1, plan.decision().helicopters()));
            pattern.setSelectedItem(plan.decision().strategy().name());
        } finally {
            syncing = false;
        }
        back.setEnabled(plan != original);
    }

    private void showProblem(String message) {
        problem.setText(message == null ? "" : "⚠ " + message);
        launch.setEnabled(message == null);
        if (message != null) {
            back.setEnabled(true);
        }
    }

    /** Fills the tiles, the reasoning, the notes and the full plan from a plan. */
    private void refresh(MissionPlan plan) {
        roverTile.set(plan.carrier().id());
        droneTile.set(String.valueOf(plan.drones().size()));
        robodogTile.set(String.valueOf(plan.decision().robodogs()));
        helicopterTile.set(String.valueOf(plan.decision().helicopters()));
        driveTile.set(String.format(Locale.ROOT, "%.1f km", plan.depot().distanceKmTo(plan.staging())));
        Incident incident = plan.incident();
        sub.setText(String.format(Locale.ROOT, "%s · %s · about %d people · planned by %s",
                titleCase(incident.type().keyword()), titleCase(incident.severity().name()),
                incident.estimatedPeople(), plan.decision().plannedBy()));
        why.setText(plan.decision().rationale());
        notes.removeAll();
        for (String note : plan.notes()) {
            notes.add(Box.createVerticalStrut(6));
            Wrapped n = new Wrapped(640, 3);
            n.setText("⚠ " + note);
            n.setFont(Theme.ui(Font.PLAIN, 12f));
            n.setForeground(Theme.WARN);
            notes.add(left(n));
        }
        text.setText(plan.describe());
        text.setCaretPosition(0);
        back.setEnabled(plan != original);
        notes.revalidate();
        dialog.getContentPane().revalidate();
        if (dialog.isShowing() && dialog.getPreferredSize().height > dialog.getHeight()) {
            dialog.pack();
        }
        dialog.getContentPane().repaint();
    }

    // ---- small helpers --------------------------------------------------------------------------

    private static JPanel labelled(String caption, JComponent control) {
        JPanel box = new JPanel(new BorderLayout(0, 3));
        box.setOpaque(false);
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.ui(Font.PLAIN, 12f));
        box.add(label, BorderLayout.NORTH);
        box.add(control, BorderLayout.CENTER);
        return box;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static String titleCase(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
    }

    /** For tests: the dialog's current settings. */
    String settings() {
        return droneSpinner.getValue() + "|" + pattern.getSelectedItem() + "|" + helicopterSpinner.getValue()
                + "|dogs" + robodogSpinner.getValue()
                + "|" + (launch.isEnabled() ? "ok" : "blocked");
    }

    /** A square tile with one big value and a caption. */
    private static final class Tile extends JComponent {
        private static final long serialVersionUID = 1L;
        private String value = " ";
        private final String caption;

        Tile(String caption) {
            this.caption = caption;
        }

        void set(String newValue) {
            if (!newValue.equals(value)) {
                value = newValue;
                repaint();
            }
        }

        String value() {
            return value;
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
            g2.setColor(Theme.BORDER);
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setFont(Theme.serif(18f));
            g2.setColor(Theme.TEXT);
            g2.drawString(fit(g2, value, getWidth() - 20), 12, 28);
            g2.setFont(Theme.mono(10.5f));
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

    /** The disaster icon in the heading. */
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
            Color c = IncidentList.typeColor(incident.type());
            g2.setColor(Ui.alpha(c, 150));
            g2.drawRect(0, 0, 47, 47);
            IncidentList.paintTypeIcon(g2, incident.type(), 24, 24, 20, c);
            g2.dispose();
        }
    }
}
