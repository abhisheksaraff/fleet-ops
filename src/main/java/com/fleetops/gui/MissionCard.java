package com.fleetops.gui;

import com.fleetops.debrief.Debrief;
import com.fleetops.domain.fleet.UnitState;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.mission.Mission;
import com.fleetops.mission.MissionReport;
import com.fleetops.sim.IncidentZone;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The card that floats over the map. It describes the selected incident; once a mission exists for it
 * the card also shows live progress (people rescued, found and still hidden, elapsed time, units at
 * work) or, when the mission is over, how it ended and, once it has been written, the debrief.
 */
final class MissionCard extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int WIDTH = 340;

    private final transient Runnable onPlan;
    private final transient Runnable onAbort;
    private final transient Runnable onLaunch;
    private final transient Runnable onCancel;

    private final Badge badge = new Badge();
    private final Wrapped title = new Wrapped(236, 3);
    private final JLabel kind = new JLabel();
    private final Chip severityChip = new Chip();
    private final Chip stateChip = new Chip();
    private final JLabel facts = new JLabel();
    private final JLabel grid = Ui.readout(" ");
    private final Wrapped summary = new Wrapped(308, 5);
    private final JPanel debriefBox = new JPanel();
    private final JLabel debriefCaption = Ui.readout("debrief");
    private final Wrapped debriefSummary = new Wrapped(308, 4);
    private final Wrapped debriefNext = new Wrapped(308, 3);
    private final JPanel progressBox = new JPanel();
    private final JLabel progressCaption = Ui.readout("mission");
    private final Bar bar = new Bar();
    private final JLabel progressMain = new JLabel();
    private final JLabel progressSub = new JLabel();
    private final JLabel progressUnits = new JLabel();
    private final JButton planButton = new JButton("Plan rescue");
    private final JButton abortButton = new JButton("Abort mission");
    private final JButton launchButton = new JButton("Launch");
    private final JButton cancelButton = new JButton("Cancel plan");
    private final JPanel buttons = new JPanel(new GridLayout(1, 0, 8, 0));

    private transient Incident shownIncident;
    private transient IncidentState shownState;
    private String lastProgress = "";
    private String lastDebrief = "";
    private boolean summaryText;
    private String shownButtons = "";
    private boolean relayout;

    MissionCard(Runnable onPlan, Runnable onAbort, Runnable onLaunch, Runnable onCancel) {
        this.onPlan = onPlan;
        this.onAbort = onAbort;
        this.onLaunch = onLaunch;
        this.onCancel = onCancel;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        // The card swallows mouse input so a drag on it does not pan the map underneath.
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });
        addMouseWheelListener(e -> { });

        title.setFont(Theme.serif(17f));
        title.setForeground(Theme.TEXT);
        kind.setFont(Theme.mono(11f));
        kind.setForeground(Theme.MUTED);
        facts.setFont(Theme.ui(Font.PLAIN, 12f));
        facts.setForeground(Theme.MUTED);
        summary.setFont(Theme.ui(Font.PLAIN, 12f));
        summary.setForeground(new Color(0xC4BFAC));
        progressMain.setFont(Theme.serif(18f));
        progressMain.setForeground(Theme.TEXT);
        progressSub.setFont(Theme.ui(Font.PLAIN, 12f));
        progressSub.setForeground(Theme.MUTED);
        progressUnits.setFont(Theme.ui(Font.PLAIN, 12f));
        progressUnits.setForeground(Theme.MUTED);
        debriefSummary.setFont(Theme.ui(Font.PLAIN, 12f));
        debriefSummary.setForeground(Theme.TEXT);
        debriefNext.setFont(Theme.ui(Font.PLAIN, 12f));
        debriefNext.setForeground(Theme.MUTED);

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        heading.add(badge, BorderLayout.WEST);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(title);
        names.add(Box.createVerticalStrut(3));
        names.add(kind);
        title.setAlignmentX(0f);
        kind.setAlignmentX(0f);
        heading.add(names, BorderLayout.CENTER);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chips.setOpaque(false);
        chips.add(severityChip);
        chips.add(stateChip);

        progressBox.setOpaque(false);
        progressBox.setLayout(new BoxLayout(progressBox, BoxLayout.Y_AXIS));
        progressBox.add(progressMain);
        progressBox.add(Box.createVerticalStrut(2));
        progressBox.add(progressCaption);
        progressBox.add(Box.createVerticalStrut(7));
        progressBox.add(bar);
        progressBox.add(Box.createVerticalStrut(8));
        progressBox.add(progressSub);
        progressBox.add(Box.createVerticalStrut(2));
        progressBox.add(progressUnits);
        for (java.awt.Component c : progressBox.getComponents()) {
            ((JComponent) c).setAlignmentX(0f);
        }

        debriefBox.setOpaque(false);
        debriefBox.setLayout(new BoxLayout(debriefBox, BoxLayout.Y_AXIS));
        debriefBox.add(Box.createVerticalStrut(12));
        debriefBox.add(debriefCaption);
        debriefBox.add(Box.createVerticalStrut(5));
        debriefBox.add(debriefSummary);
        debriefBox.add(Box.createVerticalStrut(4));
        debriefBox.add(debriefNext);
        for (java.awt.Component c : debriefBox.getComponents()) {
            ((JComponent) c).setAlignmentX(0f);
        }
        debriefBox.setVisible(false);

        buttons.setOpaque(false);
        Ui.primary(planButton);
        Ui.danger(abortButton);
        Ui.primary(launchButton);
        Ui.secondary(cancelButton);
        launchButton.addActionListener(e -> this.onLaunch.run());
        cancelButton.addActionListener(e -> this.onCancel.run());
        planButton.addActionListener(e -> this.onPlan.run());
        abortButton.addActionListener(e -> this.onAbort.run());

        add(heading);
        add(Box.createVerticalStrut(12));
        add(chips);
        add(Box.createVerticalStrut(12));
        add(facts);
        add(Box.createVerticalStrut(2));
        add(grid);
        add(Box.createVerticalStrut(8));
        add(summary);
        add(Box.createVerticalStrut(14));
        add(progressBox);
        add(debriefBox);
        add(Box.createVerticalStrut(14));
        add(buttons);
        for (java.awt.Component c : getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(0f);
            }
        }
        setVisible(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    // ---- content --------------------------------------------------------------------------------

    /** Hides the card (nothing is selected). */
    void clear() {
        shownIncident = null;
        shownState = null;
        lastProgress = "";
        lastDebrief = "";
        shownButtons = "";
        setVisible(false);
    }

    /**
     * Shows an incident. Static text is rebuilt only when the incident or its state changes; the
     * progress numbers are refreshed on every call, so this is cheap to call from the clock.
     *
     * @param mission the mission for this incident, or {@code null}
     * @param nowSeconds the simulation clock, for the elapsed time of a running mission
     * @param busy true while a plan is being made, which disables the buttons
     * @param pending true when a plan for this incident is waiting to be launched or cancelled
     */
    void show(Incident incident, Mission mission, double nowSeconds, boolean busy, boolean pending) {
        if (incident == null) {
            clear();
            return;
        }
        if (incident != shownIncident || incident.state() != shownState) {
            rebuild(incident);
        }
        boolean running = mission != null && !mission.isFinished();
        planButton.setEnabled(!busy);
        abortButton.setEnabled(!busy && running);
        launchButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        boolean showPlan = incident.state() == IncidentState.VERIFIED;
        String layout = showPlan + "|" + running + "|" + pending;
        if (!layout.equals(shownButtons)) {
            shownButtons = layout;
            buttons.removeAll();
            if (showPlan) {
                buttons.add(planButton);
            }
            if (pending) {
                buttons.add(launchButton);
                buttons.add(cancelButton);
            }
            if (running) {
                buttons.add(abortButton);
            }
            buttons.setVisible(buttons.getComponentCount() > 0);
            relayout = true;
        }
        updateProgress(mission, nowSeconds);
        if (!isVisible()) {
            setVisible(true);
            relayout = true;
        }
        if (relayout) {
            relayout = false;
            revalidate();
            repaint();
        }
    }

    private void rebuild(Incident incident) {
        shownIncident = incident;
        shownState = incident.state();
        badge.type = incident.type();
        title.setText(incident.title());
        kind.setText(incident.type().keyword() + " #" + incident.id().map(String::valueOf).orElse("?"));
        severityChip.set(incident.severity().name(), IncidentList.severityColor(incident.severity()));
        stateChip.set(IncidentList.stateChip(incident.state())[0], IncidentList.stateColor(incident.state()));
        facts.setText(String.format(Locale.ROOT, "About %d people \u00B7 %.1f km square",
                incident.estimatedPeople(), incident.area().sideKm()));
        grid.setText(IncidentList.gridRef(incident));
        String text = incident.summary() == null ? "" : incident.summary().strip();
        summary.setText(text);
        summaryText = !text.isEmpty();
        summary.setVisible(summaryText);
        badge.repaint();
        lastProgress = "";
        lastDebrief = "";
        relayout = true;
    }

    /**
     * Shows the debrief of a finished mission: the summary and the first suggestion for next time.
     *
     * @param debrief the written debrief, or {@code null} if there is none
     * @param writing true while the debrief is still being written
     */
    void showDebrief(Debrief debrief, boolean writing) {
        String caption;
        String text;
        String next;
        if (debrief != null) {
            caption = "debrief \u00B7 " + debrief.writtenBy();
            text = debrief.summary();
            next = debrief.nextTime().isEmpty() ? "" : "Next time: " + debrief.nextTime().get(0);
        } else if (writing) {
            caption = "debrief \u00B7 writing\u2026";
            text = "";
            next = "";
        } else {
            caption = "";
            text = "";
            next = "";
        }
        String signature = caption + "|" + text + "|" + next;
        if (signature.equals(lastDebrief)) {
            return;
        }
        lastDebrief = signature;
        debriefCaption.setText(caption.isEmpty() ? " " : caption);
        debriefSummary.setText(text);
        debriefNext.setText(next);
        debriefBox.setVisible(!caption.isEmpty());
        // The news blurb gives way to the debrief once there is one, which keeps the card short.
        summary.setVisible(summaryText && debrief == null);
        revalidate();
        repaint();
    }

    /** The debrief caption and summary currently shown, for tests. */
    String shownDebrief() {
        return debriefBox.isVisible() ? debriefCaption.getText() + " | " + debriefSummary.text() : "";
    }

    private void updateProgress(Mission mission, double nowSeconds) {
        if (mission == null) {
            if (progressBox.isVisible()) {
                progressBox.setVisible(false);
                relayout = true;
            }
            lastProgress = "";
            return;
        }
        IncidentZone zone = mission.zone();
        int total = zone.totalCount();
        int rescued = zone.rescuedCount();
        int found = zone.foundCount();
        int hidden = zone.hiddenCount();
        String main;
        String sub;
        String units;
        Color accent;
        if (mission.isFinished()) {
            MissionReport report = mission.report().orElse(null);
            accent = switch (mission.status()) {
                case COMPLETED -> Theme.DONE;
                case INCOMPLETE -> Theme.WARN;
                default -> Theme.DANGER;
            };
            progressCaption.setText("mission " + mission.status().toString().toLowerCase(Locale.ROOT));
            main = rescued + " / " + total + " rescued";
            sub = report == null ? "" : String.format(Locale.ROOT, "%.1f min simulated · %d never found",
                    report.seconds() / 60.0, report.notFound());
            units = "";
        } else {
            accent = Theme.LIVE;
            progressCaption.setText("mission running");
            main = rescued + " / " + total + " rescued";
            double minutes = Math.max(0, nowSeconds - mission.startedAtSeconds()) / 60.0;
            sub = String.format(Locale.ROOT, "%d found · %d still hidden · %.1f min", found, hidden, minutes);
            long searching = mission.plan().drones().stream()
                    .filter(d -> d.state() == UnitState.OPERATING || d.state() == UnitState.EN_ROUTE).count();
            long flying = mission.helicopters().stream().filter(h -> h.state() != UnitState.IDLE).count();
            units = searching + " of " + mission.plan().drones().size() + " drones searching · "
                    + flying + " of " + mission.helicopters().size() + " helicopters flying";
        }
        String signature = main + "|" + sub + "|" + units + "|" + accent.getRGB() + "|" + progressCaption.getText();
        if (!signature.equals(lastProgress)) {
            lastProgress = signature;
            progressMain.setText(main);
            progressSub.setText(sub);
            progressUnits.setText(units);
            progressUnits.setVisible(!units.isEmpty());
            progressCaption.setForeground(accent);
        }
        bar.set(total == 0 ? 0 : rescued / (double) total, total == 0 ? 0 : (rescued + found) / (double) total, accent);
        if (!progressBox.isVisible()) {
            progressBox.setVisible(true);
            relayout = true;
        }
    }

    /** The text under the progress number, for tests. */
    String progressSummary() {
        return progressMain.isVisible() && progressBox.isVisible() ? progressMain.getText() : "";
    }

    String shownTitle() {
        return shownIncident == null ? "" : shownIncident.title();
    }

    private static String titleCase(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    /** The card is a title block: ink ground with a double hairline frame, as on a survey sheet. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(new Color(0x0A, 0x12, 0x1A, 245));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(0x4A, 0x5E, 0x70));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g2.setColor(Theme.BORDER);
        g2.drawRect(4, 4, getWidth() - 9, getHeight() - 9);
        g2.dispose();
        super.paintComponent(g);
    }

    // ---- small parts ----------------------------------------------------------------------------

    /** The disaster's symbol in a square frame, next to the title. */
    private static final class Badge extends JComponent {
        private static final long serialVersionUID = 1L;
        private transient com.fleetops.domain.incident.IncidentType type = com.fleetops.domain.incident.IncidentType.FLOOD;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(44, 44);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Color c = IncidentList.typeColor(type);
            g2.setColor(Ui.alpha(c, 150));
            g2.drawRect(0, 0, 43, 43);
            IncidentList.paintTypeIcon(g2, type, 22, 22, 18, c);
            g2.dispose();
        }
    }

    /** Severity or state: a square swatch and a word, no pill. */
    private static final class Chip extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text = "";
        private Color color = Theme.MUTED;

        void set(String label, Color accent) {
            text = label.toUpperCase(Locale.ROOT).replace('_', ' ');
            color = accent;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.monoBold(10.5f));
            return new Dimension(fm.stringWidth(text) + 16, 18);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRect(0, getHeight() / 2 - 3, 7, 7);
            g2.setFont(Theme.monoBold(10.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 14, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** A thin progress bar: solid for rescued, faint for found-so-far. */
    private static final class Bar extends JComponent {
        private static final long serialVersionUID = 1L;
        private double rescued;
        private double found;
        private Color color = Theme.LIVE;

        void set(double rescuedShare, double foundShare, Color accent) {
            if (rescued != rescuedShare || found != foundShare || !color.equals(accent)) {
                rescued = rescuedShare;
                found = foundShare;
                color = accent;
                repaint();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 6);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 6);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.RAISED);
            g2.fillRect(0, 0, w, h);
            g2.setColor(Ui.alpha(color, 80));
            g2.fillRect(0, 0, (int) (w * Math.min(1, found)), h);
            g2.setColor(color);
            g2.fillRect(0, 0, (int) (w * Math.min(1, rescued)), h);
            g2.setColor(Theme.BORDER);
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.dispose();
        }
    }
}
