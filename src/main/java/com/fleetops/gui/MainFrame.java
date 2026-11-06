package com.fleetops.gui;

import com.fleetops.app.AppContext;
import com.fleetops.app.FleetLayout;
import com.fleetops.app.SimulationRig;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.intel.IngestReport;
import com.fleetops.mission.Mission;
import com.fleetops.mission.MissionControl;
import com.fleetops.mission.MissionReport;
import com.fleetops.plan.MissionPlan;
import com.fleetops.safety.SafetyPolicy;
import com.fleetops.sim.SimulationEngine;
import com.fleetops.sim.SimulationEvent;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Main window: the map, the incident list, mission buttons and an event log. Everything the window
 * does goes through {@link AppContext} (news, planner, store) and {@link SimulationRig} (the fleet
 * and mission control), the same objects the command line uses.
 *
 * <p>The simulation is advanced from a Swing timer on the event thread. Slow work (reading news,
 * asking the AI for a plan) runs in a {@link SwingWorker} while the simulation is paused, so the
 * window stays responsive and no two threads touch the simulation at once.
 */
final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final int TIMER_MS = 40;
    private static final double DEPOT_KM = 30.0;
    /** Beyond this the rover could not reach an incident within the safety gate's service area. */
    private static final double RELOCATE_KM = SafetyPolicy.DEFAULT_SERVICE_RADIUS_KM * 0.6;
    private static final long SEED = 42;
    private static final long MAX_TICKS_PER_FRAME = 200;
    private static final int MAX_LOG_LINES = 1500;
    private static final Integer[] SPEEDS = {30, 60, 120, 300, 600, 1200};

    private final transient AppContext app;
    private final transient Runnable onExit;
    private final IncidentList incidentList = new IncidentList();
    private final MapPanel map;
    private final transient MapOverlay overlay;
    private int frame;
    private final MissionCard card = new MissionCard(this::planSelected, this::abortSelected);
    private final JTextArea log = new JTextArea(7, 80);
    private final JLabel status = new JLabel(" ");
    private Workspace workspace;

    private final JButton scanButton = new JButton("Scan news");
    private final JButton clearButton = new JButton("Clear finished");
    private final JButton runButton = new JButton("Plan mission");
    private final JButton abortButton = new JButton("Abort");
    private final JButton abortAllButton = new JButton("Abort all");
    private final JButton resetButton = new JButton("Reset fleet");
    private final JSpinner rovers = new JSpinner(new SpinnerNumberModel(FleetLayout.DEFAULT.rovers(), 1, FleetLayout.MAX_ROVERS, 1));
    private final JSpinner drones = new JSpinner(new SpinnerNumberModel(FleetLayout.DEFAULT.dronesPerRover(), 1, 10, 1));
    private final JSpinner helicopters = new JSpinner(new SpinnerNumberModel(FleetLayout.DEFAULT.helicopters(), 1, FleetLayout.MAX_HELICOPTERS, 1));
    private final JComboBox<Integer> speed = new JComboBox<>(SPEEDS);

    private final Timer timer;
    private transient SimulationRig rig;
    private transient SimulationEngine engineForMap;
    private boolean busy;
    private double tickBudget;

    MainFrame(AppContext app, Runnable onExit) {
        super("FleetOps - Mission Control");
        this.app = app;
        this.onExit = onExit;

        if (!MapTiles.isKnown(app.config().mapTiles())) {
            appendLog("MAP_TILES='" + app.config().mapTiles() + "' is not supported; using the dark map (use dark or osm).");
        }
        overlay = new MapOverlay(incidentList::all, () -> engineForMap, this::selectedIncident,
                () -> rig == null ? List.of() : rig.control().activeMissions());
        map = new MapPanel(MapTiles.create(app.config().mapTiles()), MapTiles.attribution(app.config().mapTiles()),
                incidentList::all, overlay);
        map.setCard(card);
        map.onSelect(this::selectFromMap);
        map.onActivate(incident -> {
            selectFromMap(incident);
            planSelected();
        });

        buildLayout();
        wireActions();

        speed.setSelectedItem(300);
        timer = new Timer(TIMER_MS, e -> onTimer());
        timer.start();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        refreshIncidents();
        updateButtons();
        setPreferredSize(new Dimension(1440, 880));
        setMinimumSize(new Dimension(760, 520));
        pack();
        setLocationRelativeTo(null);
    }

    // ---- layout ---------------------------------------------------------------------------------

    private void buildLayout() {
        log.setEditable(false);
        log.setFont(Theme.mono(12f));
        log.setBackground(Theme.CONSOLE_BG);
        log.setForeground(Theme.TEXT);
        log.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        Ui.primary(scanButton);
        Ui.secondary(clearButton);
        Ui.primary(runButton);
        Ui.danger(abortButton);
        Ui.danger(abortAllButton);
        Ui.secondary(resetButton);
        for (JSpinner spinner : List.of(rovers, drones, helicopters)) {
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(2);
        }

        // ---- sidebar: incidents on top, the rest below -----------------------------------------
        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
        actions.setOpaque(false);
        actions.add(scanButton);
        actions.add(clearButton);

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(Ui.caption("Incidents"));
        head.add(javax.swing.Box.createVerticalStrut(8));
        head.add(actions);
        head.add(javax.swing.Box.createVerticalStrut(10));
        for (java.awt.Component c : head.getComponents()) {
            ((javax.swing.JComponent) c).setAlignmentX(0f);
        }

        JPanel missionRow = new JPanel(new GridLayout(1, 3, 8, 0));
        missionRow.setOpaque(false);
        missionRow.add(runButton);
        missionRow.add(abortButton);
        missionRow.add(abortAllButton);

        JPanel fleetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fleetRow.setOpaque(false);
        fleetRow.add(Ui.muted("Rovers"));
        fleetRow.add(rovers);
        fleetRow.add(Ui.muted("Drones each"));
        fleetRow.add(drones);
        fleetRow.add(Ui.muted("Helicopters"));
        fleetRow.add(helicopters);

        JPanel speedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        speedRow.setOpaque(false);
        speedRow.add(Ui.muted("Simulation speed"));
        speedRow.add(speed);
        speedRow.add(Ui.muted("x real time"));
        speedRow.add(resetButton);

        JPanel foot = new JPanel();
        foot.setOpaque(false);
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.add(javax.swing.Box.createVerticalStrut(10));
        foot.add(missionRow);
        foot.add(javax.swing.Box.createVerticalStrut(14));
        foot.add(Ui.caption("Fleet"));
        foot.add(javax.swing.Box.createVerticalStrut(6));
        foot.add(fleetRow);
        foot.add(javax.swing.Box.createVerticalStrut(10));
        foot.add(speedRow);
        for (java.awt.Component c : foot.getComponents()) {
            ((javax.swing.JComponent) c).setAlignmentX(0f);
        }

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.PANEL);
        sidebar.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        sidebar.add(head, BorderLayout.NORTH);
        sidebar.add(incidentList, BorderLayout.CENTER);
        sidebar.add(foot, BorderLayout.SOUTH);
        sidebar.setMinimumSize(new Dimension(0, 0));

        // ---- console ---------------------------------------------------------------------------
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBackground(Theme.CONSOLE_BG);
        JLabel consoleTitle = Ui.caption("Events");
        consoleTitle.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        consolePanel.add(consoleTitle, BorderLayout.NORTH);
        JScrollPane logPane = new JScrollPane(log);
        logPane.setBorder(null);
        logPane.getViewport().setBackground(Theme.CONSOLE_BG);
        consolePanel.add(logPane, BorderLayout.CENTER);

        workspace = new Workspace(sidebar, map, consolePanel, Preferences.userNodeForPackage(MainFrame.class));
        HeaderBar header = new HeaderBar(workspace);

        status.setForeground(Theme.MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(4, 14, 5, 14));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(Theme.PANEL);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        statusBar.add(status, BorderLayout.CENTER);

        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
        }
        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(workspace, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void wireActions() {
        incidentList.addSelectionListener((selected, byUser) -> {
            if (selected != null && byUser) {
                map.fit(List.of(selected));
            }
            map.repaint();
            updateButtons();
        });
        scanButton.addActionListener(e -> scan());
        clearButton.addActionListener(e -> clearFinished());
        runButton.setToolTipText("Ask the AI for a plan for the selected incident");
        abortButton.setToolTipText("Stop the mission of the selected incident");
        abortAllButton.setToolTipText("Stop every running mission");
        runButton.addActionListener(e -> planSelected());
        abortButton.addActionListener(e -> abortSelected());
        abortAllButton.addActionListener(e -> {
            if (rig != null) {
                rig.control().abortAll("stopped by the operator");
                appendLog("All missions are being aborted.");
            }
        });
        resetButton.addActionListener(e -> resetFleet());
    }

    // ---- state shown on screen ------------------------------------------------------------------

    Incident selectedIncident() {
        return incidentList.selected();
    }

    private void selectFromMap(Incident incident) {
        incidentList.select(incident);
    }

    /** Reloads the incident list from the store, keeping the selection. */
    void refreshIncidents() {
        List<Incident> shown = app.repository().findAll().stream()
                .filter(i -> i.state() != IncidentState.REJECTED && i.state() != IncidentState.DUPLICATE)
                .toList();
        incidentList.setIncidents(shown);
        map.repaint();
        updateButtons();
    }

    private void updateButtons() {
        Incident selected = selectedIncident();
        boolean active = rig != null && !rig.control().activeMissions().isEmpty();
        scanButton.setEnabled(!busy);
        clearButton.setEnabled(!busy && !active && incidentList.all().stream().anyMatch(i -> !i.isActive()));
        runButton.setEnabled(!busy && selected != null && selected.state() == IncidentState.VERIFIED);
        abortButton.setEnabled(!busy && selected != null && rig != null && rig.control().missionFor(selected).filter(m -> !m.isFinished()).isPresent());
        abortAllButton.setEnabled(!busy && active);
        resetButton.setEnabled(!busy && !active);
        rovers.setEnabled(!active);
        drones.setEnabled(!active);
        helicopters.setEnabled(!active);
        refreshCard(selected);
    }

    /** Updates the floating card over the map for the selected incident. */
    private void refreshCard(Incident selected) {
        if (selected == null) {
            card.clear();
            return;
        }
        Mission mission = rig == null ? null : rig.control().missionFor(selected).orElse(null);
        card.show(selected, mission, rig == null ? 0 : rig.engine().elapsedSeconds(), busy);
    }

    private void appendLog(String line) {
        log.append(line + "\n");
        if (log.getLineCount() > MAX_LOG_LINES) {
            try {
                log.getDocument().remove(0, log.getLineStartOffset(log.getLineCount() - MAX_LOG_LINES));
            } catch (javax.swing.text.BadLocationException ignored) {
                // the log is only informational
            }
        }
        log.setCaretPosition(log.getDocument().getLength());
    }

    // ---- simulation clock -----------------------------------------------------------------------

    private void onTimer() {
        if (rig == null || busy) {
            return;
        }
        MissionControl control = rig.control();
        boolean active = !control.activeMissions().isEmpty() || !control.queue().isIdle();
        if (active) {
            int perSecond = (Integer) speed.getSelectedItem();
            tickBudget += perSecond * (TIMER_MS / 1000.0) / rig.engine().tickSeconds();
            long ticks = Math.min((long) tickBudget, MAX_TICKS_PER_FRAME);
            tickBudget -= ticks;
            rig.engine().runTicks(ticks);
            map.repaint();
        } else {
            tickBudget = 0;
        }
        if (!active && overlay.animating() && ++frame % 2 == 0) {
            map.repaint(); // rotors and waiting survivors keep moving between missions
        }
        status.setText(String.format(Locale.ROOT, "Active missions: %d    Simulated time: %.1f min",
                control.activeMissions().size(), rig.engine().elapsedSeconds() / 60.0));
        updateButtons();
    }

    // ---- actions --------------------------------------------------------------------------------

    private void scan() {
        setBusy(true, "Scanning news...");
        new SwingWorker<IngestReport, Void>() {
            @Override
            protected IngestReport doInBackground() {
                return app.intelligence().scan(app.config().newsTopics());
            }

            @Override
            protected void done() {
                try {
                    IngestReport report = get();
                    appendLog("Scan: " + report.summary());
                    report.sourceFailures().forEach((type, why) -> appendLog("  could not read " + type.keyword() + " news: " + why));
                    refreshIncidents();
                    if (!report.created().isEmpty()) {
                        map.fit(incidentList.all());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("Scan failed", e.getCause());
                } finally {
                    setBusy(false, " ");
                }
            }
        }.execute();
    }

    private void planSelected() {
        Incident incident = selectedIncident();
        if (incident == null || busy || incident.state() != IncidentState.VERIFIED) {
            return;
        }
        SimulationRig current;
        try {
            current = ensureRig(incident);
        } catch (RuntimeException e) {
            showError("Could not set up the fleet", e);
            return;
        }
        setBusy(true, "Planning...");
        new SwingWorker<MissionPlan, Void>() {
            @Override
            protected MissionPlan doInBackground() {
                return current.control().plan(incident);
            }

            @Override
            protected void done() {
                try {
                    MissionPlan plan = get();
                    setBusy(false, " ");
                    approve(current.control(), plan);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setBusy(false, " ");
                } catch (ExecutionException e) {
                    setBusy(false, " ");
                    showError("No plan could be made", e.getCause());
                    refreshIncidents();
                }
            }
        }.execute();
    }

    private void approve(MissionControl control, MissionPlan plan) {
        boolean launch = PlanDialog.confirm(this, plan);
        try {
            if (launch) {
                Mission mission = control.launch(plan);
                appendLog("Launched: " + plan.incident().title() + " with " + mission.helicopters().size() + " helicopter(s).");
                map.fit(plan.incident(), plan.depot());
            } else {
                control.cancelPlan(plan);
                appendLog("Plan cancelled for " + plan.incident().title() + ".");
            }
        } catch (RuntimeException e) {
            try {
                control.cancelPlan(plan);
            } catch (RuntimeException ignored) {
                // the incident was not PLANNED any more; nothing to undo
            }
            showError("Could not launch", e);
        }
        refreshIncidents();
    }

    private void abortSelected() {
        Incident selected = selectedIncident();
        if (selected == null || rig == null) {
            return;
        }
        Optional<Mission> mission = rig.control().missionFor(selected);
        mission.filter(m -> !m.isFinished()).ifPresent(m -> {
            rig.control().abort(m, "stopped by the operator");
            appendLog("Aborting: " + selected.title());
        });
    }

    private void clearFinished() {
        if (busy || (rig != null && !rig.control().activeMissions().isEmpty())) {
            return;
        }
        long count = incidentList.all().stream().filter(i -> !i.isActive()).count();
        int choice = JOptionPane.showConfirmDialog(this, "Delete " + count + " finished incident(s) from the database?",
                "Clear finished incidents", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            int removed = app.repository().deleteFinished();
            appendLog("Deleted " + removed + " finished incident(s).");
            // The map keeps survivors of removed incidents until the fleet is rebuilt.
            rig = null;
            engineForMap = null;
        } catch (RuntimeException e) {
            showError("Could not delete incidents", e);
        }
        refreshIncidents();
    }

    private void resetFleet() {
        if (rig != null && !rig.control().activeMissions().isEmpty()) {
            return;
        }
        rig = null;
        engineForMap = null;
        appendLog("Fleet reset. A fresh fleet will be placed " + (int) DEPOT_KM + " km south of the next incident you plan.");
        map.repaint();
        updateButtons();
    }

    /**
     * Builds the fleet on first use, parked south of the incident that is planned first. When the
     * fleet is idle and the next incident is outside its service area, the fleet is moved to a new
     * base near that incident; while missions are running it stays put and the planner explains why
     * it cannot reach the incident.
     */
    private SimulationRig ensureRig(Incident near) {
        if (rig != null && rig.control().activeMissions().isEmpty() && rig.control().queue().isIdle()
                && rig.depot().distanceKmTo(near.area().center()) > RELOCATE_KM) {
            appendLog(String.format(Locale.ROOT, "'%s' is %.0f km from the fleet's base; moving the fleet there.",
                    near.title(), rig.depot().distanceKmTo(near.area().center())));
            rig = null;
            engineForMap = null;
        }
        if (rig == null) {
            FleetLayout layout = new FleetLayout((Integer) rovers.getValue(), (Integer) drones.getValue(), (Integer) helicopters.getValue());
            rig = SimulationRig.create(app, layout, near.area().center().offsetByKm(-DEPOT_KM, 0), SEED);
            engineForMap = rig.engine();
            rig.engine().addListener(this::onEvent);
            rig.control().addListener(this::onMissionFinished);
            appendLog(String.format(Locale.ROOT, "Fleet ready: %d rover(s) with %d drone(s) each, %d helicopter(s).",
                    layout.rovers(), layout.dronesPerRover(), layout.helicopters()));
        }
        return rig;
    }

    private void onEvent(SimulationEvent event) {
        SimulationEngine engine = rig.engine();
        String text = switch (event) {
            case SimulationEvent.SurvivorFound e -> e.foundBy().id() + " found " + e.survivor().id();
            case SimulationEvent.SurvivorRescued e -> e.rescuedBy().id() + " rescued " + e.survivor().id();
            case SimulationEvent.ZoneCleared e -> "Area cleared: " + e.zone().incident().title();
            default -> null;
        };
        if (text != null) {
            appendLog(String.format(Locale.ROOT, "[%5.1f min] %s", engine.elapsedSeconds() / 60.0, text));
        }
    }

    private void onMissionFinished(Mission mission, MissionReport report) {
        appendLog(report.describe());
        SwingUtilities.invokeLater(this::refreshIncidents);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private void setBusy(boolean value, String message) {
        busy = value;
        status.setText(message);
        setCursor(value ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : java.awt.Cursor.getDefaultCursor());
        updateButtons();
    }

    private void showError(String title, Throwable error) {
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        appendLog(title + ": " + message);
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void shutdown() {
        timer.stop();
        workspace.savePreferences();
        if (rig != null) {
            rig.control().abortAll("the window was closed");
            rig.control().runUntilSettled(MAX_TICKS_PER_FRAME * 100);
        }
        app.close();
        dispose();
        onExit.run();
    }

    // ---- hooks for tests ------------------------------------------------------------------------

    MapOverlay overlay() {
        return overlay;
    }

    MissionCard card() {
        return card;
    }

    MapPanel map() {
        return map;
    }

    IncidentList incidentList() {
        return incidentList;
    }

    SimulationRig rigOrNull() {
        return rig;
    }

    boolean isBusy() {
        return busy;
    }
}
