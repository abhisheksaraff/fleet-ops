package com.fleetops.gui;

import com.fleetops.app.AppContext;
import com.fleetops.app.FleetLayout;
import com.fleetops.app.SimulationRig;
import com.fleetops.debrief.Debrief;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.intel.IngestReport;
import com.fleetops.mission.Mission;
import com.fleetops.mission.MissionControl;
import com.fleetops.mission.MissionReport;
import com.fleetops.plan.AreaGrid;
import com.fleetops.plan.MissionPlan;
import com.fleetops.safety.SafetyPolicy;
import com.fleetops.sim.SimulationEngine;
import com.fleetops.sim.SimulationEvent;
import com.fleetops.strategy.SearchStrategies;
import com.fleetops.strategy.SearchStrategy;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
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
    private static final double DEPOT_KM = 8.0;
    /** Beyond this the rover could not reach an incident within the safety gate's service area. */
    private static final double RELOCATE_KM = SafetyPolicy.DEFAULT_SERVICE_RADIUS_KM * 0.6;
    private static final long SEED = 42;
    private static final long MAX_TICKS_PER_FRAME = 200;
    private static final Integer[] SPEEDS = {30, 60, 120, 300, 600, 1200};

    private final transient AppContext app;
    private final transient Runnable onExit;
    private final IncidentList incidentList = new IncidentList();
    private final MapPanel map;
    private final transient MapOverlay overlay;
    private int frame;
    private final transient WindowCommands.Output logOutput = new WindowCommands.Output() {
        @Override
        public void line(String text) {
            appendLog(text);
        }

        @Override
        public void error(String text) {
            appendLog("Error: " + text);
        }
    };
    private final MissionCard card = new MissionCard(this::planSelected, this::abortSelected,
            () -> consoleLaunch(logOutput), () -> consoleCancel(logOutput));
    private final transient WindowCommands windowCommands = new WindowCommands(this);
    private transient MissionPlan pendingPlan;
    /** The plan as the planner made it, kept so the operator's changes can be undone; null with no plan waiting. */
    private transient MissionPlan pendingOriginal;
    private transient MissionControl pendingControl;
    /** The debriefs written so far and the missions still waiting for one; touched on the event thread only. */
    private final transient Map<Mission, Debrief> debriefs = new HashMap<>();
    private final transient Set<Mission> debriefing = new HashSet<>();
    private boolean closing;
    private Console console;
    private final JLabel status = new JLabel(" ");
    private HeaderBar header;
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

        app.keepOpen(); // the console runs commands that close the context they are given
        overlay = new MapOverlay(incidentList::all, () -> engineForMap, this::selectedIncident,
                () -> rig == null ? List.of() : rig.control().activeMissions());
        map = new MapPanel(MapTiles.create(app.config().mapTiles(), app.config().cartoApiKey()),
                MapTiles.attribution(app.config().mapTiles(), app.config().cartoApiKey()),
                incidentList::all, overlay);
        map.setCard(card);
        map.onSelect(this::selectFromMap);
        map.onActivate(incident -> {
            selectFromMap(incident);
            planSelected();
        });

        buildLayout();
        wireActions();
        if (!MapTiles.isKnown(app.config().mapTiles())) {
            appendLog("MAP_TILES='" + app.config().mapTiles() + "' is not supported; using the dark map (use dark or osm).");
        }
        if (!"osm".equalsIgnoreCase(app.config().mapTiles().strip()) && app.config().cartoApiKey().isEmpty()) {
            appendLog("The dark map needs a free CARTO key: set CARTO_API_KEY in .env (carto.com/basemaps/apikey). "
                    + "Showing OpenStreetMap for now.");
        }

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
        head.add(Ui.heading("Incidents"));
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

        JPanel fleetRow = new JPanel(new GridLayout(1, 4, 8, 0));
        fleetRow.setOpaque(false);
        fleetRow.add(labelled("Rovers", rovers));
        fleetRow.add(labelled("Drones each", drones));
        fleetRow.add(labelled("Helicopters", helicopters));
        fleetRow.add(labelled("Speed (x)", speed));

        JPanel fleetHead = Ui.heading("Fleet");
        resetButton.putClientProperty(FlatClientProperties.STYLE,
                "arc:3; borderWidth:1; focusWidth:0; background:#0E1823; foreground:#EBE4D0; borderColor:#3A4D5E"
                        + "; hoverBackground:#172736; pressedBackground:#1E3144; margin:1,8,1,8"
                        + "; disabledBackground:#101B26; disabledBorderColor:#1D2B38; disabledText:#5B6670");
        fleetHead.add(resetButton, BorderLayout.EAST);

        JPanel foot = new JPanel();
        foot.setOpaque(false);
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.add(javax.swing.Box.createVerticalStrut(8));
        foot.add(missionRow);
        foot.add(javax.swing.Box.createVerticalStrut(10));
        foot.add(fleetHead);
        foot.add(javax.swing.Box.createVerticalStrut(6));
        foot.add(fleetRow);
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
        console = new Console(new ConsoleHost());
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBackground(Theme.CONSOLE_BG);
        JLabel consoleTitle = new JLabel("Console");
        consoleTitle.setFont(Theme.serif(14f));
        consoleTitle.setForeground(Theme.TEXT);
        JLabel consoleHint = new JLabel("scan \u00B7 incidents \u00B7 plan \u00B7 launch \u00B7 focus \u00B7 help");
        consoleHint.setForeground(Theme.MUTED);
        consoleHint.setFont(Theme.mono(11f));
        JPanel consoleHead = new JPanel(new BorderLayout());
        consoleHead.setOpaque(false);
        consoleHead.setBorder(BorderFactory.createEmptyBorder(8, 14, 2, 14));
        consoleHead.add(consoleTitle, BorderLayout.WEST);
        consoleHead.add(consoleHint, BorderLayout.EAST);
        consolePanel.add(consoleHead, BorderLayout.NORTH);
        consolePanel.add(console, BorderLayout.CENTER);

        workspace = new Workspace(sidebar, map, consolePanel, Preferences.userNodeForPackage(MainFrame.class));
        header = new HeaderBar(workspace);

        status.setForeground(Theme.MUTED);
        status.setFont(Theme.mono(11f));
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

    /** A small label over a control. */
    private static JPanel labelled(String text, JComponent control) {
        JPanel box = new JPanel(new BorderLayout(0, 3));
        box.setOpaque(false);
        JLabel label = Ui.muted(text);
        label.setFont(Theme.ui(Font.PLAIN, 12f));
        box.add(label, BorderLayout.NORTH);
        box.add(control, BorderLayout.CENTER);
        return box;
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
        runButton.setEnabled(!busy && pendingPlan == null && selected != null && selected.state() == IncidentState.VERIFIED);
        abortButton.setEnabled(!busy && selected != null && rig != null && rig.control().missionFor(selected).filter(m -> !m.isFinished()).isPresent());
        abortAllButton.setEnabled(!busy && active);
        resetButton.setEnabled(!busy && !active && pendingPlan == null);
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
        card.show(selected, mission, rig == null ? 0 : rig.engine().elapsedSeconds(), busy,
                pendingPlan != null && pendingPlan.incident() == selected);
        card.showDebrief(mission == null ? null : debriefs.get(mission), mission != null && debriefing.contains(mission));
    }

    private void appendLog(String line) {
        console.event(line);
    }

    // ---- simulation clock -----------------------------------------------------------------------

    private void onTimer() {
        if (header != null) {
            header.setReadout(map.readout());
        }
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
        if (pendingPlan != null) {
            appendLog("A plan is waiting: type launch or cancel first.");
            return;
        }
        planIncident(incident, null);
    }

    /**
     * Makes a plan for an incident. With no {@code out} the operator approves it in a dialog; with one
     * (the console) the plan is printed and kept until {@code launch} or {@code cancel}.
     */
    private void planIncident(Incident incident, WindowCommands.Output out) {
        SimulationRig current;
        try {
            current = ensureRig(incident);
        } catch (RuntimeException e) {
            if (out == null) {
                showError("Could not set up the fleet", e);
            } else {
                out.error("Could not set up the fleet: " + e.getMessage());
            }
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
                    if (out == null) {
                        approve(current.control(), plan);
                    } else {
                        pendingPlan = plan;
                        pendingOriginal = plan;
                        pendingControl = current.control();
                        for (String line : plan.describe().split("\\R")) {
                            out.line(line);
                        }
                        out.line("Type launch to start the mission, edit to change it, or cancel to drop the plan.");
                        refreshIncidents();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setBusy(false, " ");
                } catch (ExecutionException e) {
                    setBusy(false, " ");
                    if (out == null) {
                        showError("No plan could be made", e.getCause());
                    } else {
                        out.error("No plan could be made: " + e.getCause().getMessage());
                    }
                    refreshIncidents();
                }
            }
        }.execute();
    }

    private void approve(MissionControl control, MissionPlan plan) {
        PlanDialog.Reviser reviser = new PlanDialog.Reviser() {
            @Override
            public MissionPlan revise(int drones, SearchStrategy strategy, int helicopters) {
                return control.revise(plan, drones, strategy, helicopters);
            }

            @Override
            public int maxDrones() {
                return Math.min(control.availability(plan.incident()).availableDrones(), AreaGrid.MAX_CELLS);
            }

            @Override
            public int maxHelicopters() {
                return control.availability(plan.incident()).availableHelicopters();
            }
        };
        Optional<MissionPlan> approved = PlanDialog.review(this, plan, reviser);
        MissionPlan toLaunch = approved.orElse(plan);
        try {
            if (approved.isPresent()) {
                Mission mission = control.launch(toLaunch);
                appendLog("Launched: " + toLaunch.incident().title() + " with " + mission.helicopters().size() + " helicopter(s)."
                        + (toLaunch == plan ? "" : " The operator changed the plan."));
                map.fit(toLaunch.incident(), toLaunch.depot());
            } else {
                control.cancelPlan(plan);
                appendLog("Plan cancelled for " + plan.incident().title() + ".");
            }
        } catch (RuntimeException e) {
            try {
                control.cancelPlan(toLaunch);
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

    // ---- actions for the console commands -------------------------------------------------------

    void consolePlan(Incident incident, WindowCommands.Output out) {
        if (busy) {
            out.error("The window is busy; try again when it has finished.");
        } else if (pendingPlan != null) {
            out.error("A plan is already waiting: type launch or cancel first.");
        } else {
            incidentList.select(incident);
            out.line("Planning #" + incident.id().map(String::valueOf).orElse("?") + " " + incident.title() + "...");
            planIncident(incident, out);
        }
    }

    /**
     * Changes the plan that is waiting. Each change starts again from the planner's own plan, so the
     * operator can try values and always get back to the original with {@code edit reset}.
     *
     * @param drones the number of drones to send, or null to keep the current number
     * @param patternName {@code lawnmower} or {@code spiral}, or null to keep the current pattern
     * @param helicopters the number of helicopters to have ready, or null to keep the current number
     * @param reset true to go back to the planner's own plan
     */
    void consoleEdit(Integer drones, String patternName, Integer helicopters, boolean reset, WindowCommands.Output out) {
        if (pendingPlan == null) {
            out.error("There is no plan waiting. Use: plan <id>");
            return;
        }
        MissionPlan next;
        if (reset) {
            next = pendingOriginal;
        } else if (drones == null && patternName == null && helicopters == null) {
            out.line("The plan sends " + pendingPlan.drones().size() + " drone(s) searching with "
                    + pendingPlan.decision().strategy().name() + " and has " + pendingPlan.decision().helicopters()
                    + " helicopter(s) ready.");
            out.line("Change it with: edit drones=N pattern=lawnmower|spiral helicopters=N   (edit reset goes back to the original)");
            return;
        } else {
            SearchStrategy strategy;
            try {
                strategy = patternName == null ? pendingPlan.decision().strategy() : SearchStrategies.parse(patternName);
            } catch (IllegalArgumentException e) {
                out.error(e.getMessage());
                return;
            }
            try {
                next = pendingControl.revise(pendingOriginal,
                        drones == null ? pendingPlan.drones().size() : drones,
                        strategy,
                        helicopters == null ? pendingPlan.decision().helicopters() : helicopters);
            } catch (RuntimeException e) {
                out.error("The plan cannot be changed that way: " + e.getMessage());
                return;
            }
        }
        pendingPlan = next;
        for (String line : next.describe().split("\\R")) {
            out.line(line);
        }
        out.line("Type launch to start the mission, edit to change it, or cancel to drop the plan.");
        refreshIncidents();
    }

    void consoleLaunch(WindowCommands.Output out) {
        if (pendingPlan == null) {
            out.error("There is no plan waiting. Use: plan <id>");
            return;
        }
        MissionPlan plan = pendingPlan;
        MissionControl control = pendingControl;
        pendingPlan = null;
        pendingOriginal = null;
        pendingControl = null;
        try {
            Mission mission = control.launch(plan);
            out.line("Launched: " + plan.incident().title() + " with " + mission.helicopters().size() + " helicopter(s).");
            map.fit(plan.incident(), plan.depot());
        } catch (RuntimeException e) {
            try {
                control.cancelPlan(plan);
            } catch (RuntimeException ignored) {
                // the incident was not PLANNED any more; nothing to undo
            }
            out.error("Could not launch: " + e.getMessage());
        }
        refreshIncidents();
    }

    void consoleCancel(WindowCommands.Output out) {
        if (pendingPlan == null) {
            out.error("There is no plan waiting.");
            return;
        }
        MissionPlan plan = pendingPlan;
        MissionControl control = pendingControl;
        pendingPlan = null;
        pendingOriginal = null;
        pendingControl = null;
        try {
            control.cancelPlan(plan);
        } catch (RuntimeException ignored) {
            // the incident was not PLANNED any more; nothing to undo
        }
        out.line("Plan cancelled for " + plan.incident().title() + ".");
        refreshIncidents();
    }

    void consoleAbort(Incident incident, WindowCommands.Output out) {
        Optional<Mission> mission = rig == null
                ? Optional.empty() : rig.control().missionFor(incident).filter(m -> !m.isFinished());
        if (mission.isEmpty()) {
            out.error("#" + incident.id().map(String::valueOf).orElse("?") + " has no running mission.");
            return;
        }
        rig.control().abort(mission.get(), "stopped by the operator");
        out.line("Aborting: " + incident.title());
    }

    void consoleDebrief(Incident incident, WindowCommands.Output out) {
        String id = incident.id().map(String::valueOf).orElse("?");
        Mission mission = rig == null ? null : rig.control().missionFor(incident).orElse(null);
        if (mission == null || !mission.isFinished()) {
            out.error("#" + id + " has no finished mission in this session.");
            return;
        }
        Debrief debrief = debriefs.get(mission);
        if (debrief != null) {
            out.line(debrief.describe());
        } else if (debriefing.contains(mission)) {
            out.line("The debrief for #" + id + " is still being written.");
        } else {
            out.error("No debrief was written for #" + id + ".");
        }
    }

    void consoleAbortAll(WindowCommands.Output out) {
        if (rig == null || rig.control().activeMissions().isEmpty()) {
            out.error("No mission is running.");
            return;
        }
        rig.control().abortAll("stopped by the operator");
        out.line("All missions are being aborted.");
    }

    void consoleSetFleet(int r, int d, int h, WindowCommands.Output out) {
        if (missionRunning() || pendingPlan != null) {
            out.error("The fleet cannot change while a mission runs or a plan is waiting.");
            return;
        }
        FleetLayout layout;
        try {
            layout = new FleetLayout(r, d, h);
        } catch (IllegalArgumentException e) {
            out.error(e.getMessage());
            return;
        }
        rovers.setValue(layout.rovers());
        drones.setValue(layout.dronesPerRover());
        helicopters.setValue(layout.helicopters());
        resetFleet();
        out.line("Fleet set to " + layout.rovers() + " rover(s) with " + layout.dronesPerRover()
                + " drone(s) each and " + layout.helicopters() + " helicopter(s).");
    }

    String fleetSummary() {
        String configured = String.format(Locale.ROOT, "Fleet settings: %d rover(s) with %d drone(s) each, %d helicopter(s).",
                (Integer) rovers.getValue(), (Integer) drones.getValue(), (Integer) helicopters.getValue());
        return configured + (rig == null
                ? " No fleet is on the map yet; one is placed when you plan an incident."
                : String.format(Locale.ROOT, " The fleet is based at %.3f, %.3f.", rig.depot().lat(), rig.depot().lon()));
    }

    void focusOn(Incident incident) {
        incidentList.select(incident);
        map.fit(List.of(incident));
    }

    void focusAll() {
        map.fit(incidentList.all());
    }

    List<Integer> speeds() {
        return List.of(SPEEDS);
    }

    int speed() {
        return (Integer) speed.getSelectedItem();
    }

    void setSpeed(int perSecond) {
        speed.setSelectedItem(perSecond);
    }

    Workspace workspace() {
        return workspace;
    }

    private boolean missionRunning() {
        return rig != null && !rig.control().activeMissions().isEmpty();
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
        SwingUtilities.invokeLater(() -> {
            refreshIncidents();
            writeDebrief(mission, report);
        });
    }

    /** Has the AI write the debrief off the event thread, then shows it on the card and in the console. */
    private void writeDebrief(Mission mission, MissionReport report) {
        if (closing) {
            return; // the window is being closed: do not spend an AI call on it
        }
        debriefing.add(mission);
        Thread worker = new Thread(() -> {
            Debrief debrief = null;
            String failure = null;
            try {
                debrief = app.debriefer().debrief(report);
            } catch (RuntimeException e) {
                failure = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            Debrief written = debrief;
            String problem = failure;
            SwingUtilities.invokeLater(() -> {
                debriefing.remove(mission);
                if (written != null) {
                    debriefs.put(mission, written);
                    appendLog(written.describe());
                } else {
                    appendLog("The debrief could not be written: " + problem);
                }
                updateButtons();
            });
        }, "debrief");
        worker.setDaemon(true);
        worker.start();
        updateButtons();
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
        closing = true;
        timer.stop();
        workspace.savePreferences();
        if (pendingPlan != null) {
            try {
                pendingControl.cancelPlan(pendingPlan);
            } catch (RuntimeException ignored) {
                // the incident was not PLANNED any more
            }
            pendingPlan = null;
            pendingOriginal = null;
        }
        if (rig != null) {
            rig.control().abortAll("the window was closed");
            rig.control().runUntilSettled(MAX_TICKS_PER_FRAME * 100);
        }
        app.shutdown();
        dispose();
        onExit.run();
    }

    /** What the console may ask of this window. */
    private final class ConsoleHost implements Console.Host {
        @Override
        public AppContext app() {
            return app;
        }

        @Override
        public boolean busy() {
            return busy;
        }

        @Override
        public boolean missionRunning() {
            return MainFrame.this.missionRunning() || pendingPlan != null;
        }

        @Override
        public boolean windowCommand(String name, List<String> args, WindowCommands.Output out) {
            if (!WindowCommands.handles(name)) {
                return false;
            }
            windowCommands.run(name, args, out);
            return true;
        }

        @Override
        public List<String> windowArguments(String command) {
            return WindowCommands.arguments(command, incidentList.all(), speeds());
        }

        @Override
        public void setBusy(boolean value, String message) {
            MainFrame.this.setBusy(value, message);
        }

        @Override
        public void commandFinished(String command, int exitCode) {
            if (command.equals("reset") && !missionRunning()) {
                rig = null; // the fleet and survivors of deleted incidents go with them
                engineForMap = null;
            }
            boolean hadIncidents = !incidentList.all().isEmpty();
            refreshIncidents();
            if (command.equals("scan") && !hadIncidents && !incidentList.all().isEmpty()) {
                map.fit(incidentList.all());
            }
            map.repaint();
        }

        @Override
        public List<Long> incidentIds() {
            return incidentList.all().stream().flatMap(i -> i.id().stream()).toList();
        }

        @Override
        public void hide() {
            if (workspace.consoleOpen()) {
                workspace.toggleConsole();
            }
        }
    }

    // ---- hooks for tests ------------------------------------------------------------------------

    Console console() {
        return console;
    }

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
