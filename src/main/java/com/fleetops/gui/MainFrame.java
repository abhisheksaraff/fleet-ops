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
import com.fleetops.sim.SimulationEngine;
import com.fleetops.sim.SimulationEvent;
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
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
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
    private static final long SEED = 42;
    private static final long MAX_TICKS_PER_FRAME = 200;
    private static final int MAX_LOG_LINES = 1500;
    private static final Integer[] SPEEDS = {30, 60, 120, 300, 600, 1200};

    private final transient AppContext app;
    private final transient Runnable onExit;
    private final IncidentTableModel model = new IncidentTableModel();
    private final JTable table = new JTable(model);
    private final MapPanel map;
    private final JTextArea details = new JTextArea(5, 30);
    private final JTextArea log = new JTextArea(7, 80);
    private final JLabel status = new JLabel(" ");

    private final JButton scanButton = new JButton("Scan news");
    private final JButton clearButton = new JButton("Clear finished");
    private final JButton runButton = new JButton("Plan mission...");
    private final JButton abortButton = new JButton("Abort selected");
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
    private boolean selectingFromMap;
    private double tickBudget;

    MainFrame(AppContext app, Runnable onExit) {
        super("FleetOps - Mission Control");
        this.app = app;
        this.onExit = onExit;

        if (!MapTiles.isKnown(app.config().mapTiles())) {
            appendLog("MAP_TILES='" + app.config().mapTiles() + "' is not supported; using OpenStreetMap.");
        }
        map = new MapPanel(MapTiles.create(app.config().mapTiles()), model::incidents,
                new MapOverlay(model::incidents, () -> engineForMap, this::selectedIncident));
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
        setPreferredSize(new Dimension(1280, 800));
        pack();
        setLocationRelativeTo(null);
    }

    // ---- layout ---------------------------------------------------------------------------------

    private void buildLayout() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(55);
        table.getColumnModel().getColumn(5).setPreferredWidth(220);

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(scanButton);
        top.add(clearButton);
        top.add(new JLabel("Click an incident square on the map or in the list. Double-click a square to plan."));

        JPanel missionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        missionRow.add(runButton);
        missionRow.add(abortButton);
        missionRow.add(abortAllButton);

        JPanel fleetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        fleetRow.add(new JLabel("Rovers"));
        fleetRow.add(rovers);
        fleetRow.add(new JLabel("Drones each"));
        fleetRow.add(drones);
        fleetRow.add(new JLabel("Helicopters"));
        fleetRow.add(helicopters);
        fleetRow.add(resetButton);

        JPanel speedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        speedRow.add(new JLabel("Simulation speed"));
        speedRow.add(speed);
        speedRow.add(new JLabel("x real time"));

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane tablePane = new JScrollPane(table);
        tablePane.setPreferredSize(new Dimension(480, 260));
        JScrollPane detailPane = new JScrollPane(details);
        detailPane.setPreferredSize(new Dimension(480, 110));
        detailPane.setBorder(BorderFactory.createTitledBorder("Selected incident"));
        for (JPanel row : List.of(top, missionRow, fleetRow, speedRow)) {
            row.setAlignmentX(0f);
        }
        tablePane.setAlignmentX(0f);
        detailPane.setAlignmentX(0f);
        right.add(top);
        right.add(tablePane);
        right.add(detailPane);
        right.add(missionRow);
        right.add(fleetRow);
        right.add(speedRow);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, map, right);
        split.setResizeWeight(1.0);
        split.setDividerLocation(780);

        JPanel bottom = new JPanel(new BorderLayout());
        JScrollPane logPane = new JScrollPane(log);
        logPane.setPreferredSize(new Dimension(800, 130));
        logPane.setBorder(BorderFactory.createTitledBorder("Events"));
        bottom.add(logPane, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        bottom.add(status, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void wireActions() {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            Incident selected = selectedIncident();
            showDetails(selected);
            if (selected != null && !selectingFromMap) {
                map.fit(List.of(selected));
            }
            map.repaint();
            updateButtons();
        });
        scanButton.addActionListener(e -> scan());
        clearButton.addActionListener(e -> clearFinished());
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
        int row = table.getSelectedRow();
        return row < 0 || row >= model.getRowCount() ? null : model.incidentAt(row);
    }

    private void selectFromMap(Incident incident) {
        int row = model.rowOf(incident);
        if (row < 0) {
            return;
        }
        selectingFromMap = true;
        try {
            table.getSelectionModel().setSelectionInterval(row, row);
        } finally {
            selectingFromMap = false;
        }
    }

    private void showDetails(Incident i) {
        if (i == null) {
            details.setText("");
            return;
        }
        details.setText(String.format(Locale.ROOT,
                "#%s %s (%s, %s)%nAbout %d people in a %.1f km square at %.4f, %.4f%n%s",
                i.id().map(String::valueOf).orElse("?"), i.title(), i.type().keyword(), i.severity(),
                i.estimatedPeople(), i.area().sideKm(), i.area().center().lat(), i.area().center().lon(),
                i.summary()));
        details.setCaretPosition(0);
    }

    /** Reloads the incident list from the store, keeping the selection. */
    void refreshIncidents() {
        Incident keep = selectedIncident();
        List<Incident> shown = app.repository().findAll().stream()
                .filter(i -> i.state() != IncidentState.REJECTED && i.state() != IncidentState.DUPLICATE)
                .toList();
        model.setIncidents(shown);
        if (keep != null) {
            int row = model.rowOf(keep);
            if (row >= 0) {
                selectingFromMap = true;
                try {
                    table.getSelectionModel().setSelectionInterval(row, row);
                } finally {
                    selectingFromMap = false;
                }
            }
        }
        map.repaint();
        updateButtons();
    }

    private void updateButtons() {
        Incident selected = selectedIncident();
        boolean active = rig != null && !rig.control().activeMissions().isEmpty();
        scanButton.setEnabled(!busy);
        clearButton.setEnabled(!busy && !active && model.incidents().stream().anyMatch(i -> !i.isActive()));
        runButton.setEnabled(!busy && selected != null && selected.state() == IncidentState.VERIFIED);
        abortButton.setEnabled(!busy && selected != null && rig != null && rig.control().missionFor(selected).filter(m -> !m.isFinished()).isPresent());
        abortAllButton.setEnabled(!busy && active);
        resetButton.setEnabled(!busy && !active);
        rovers.setEnabled(!active);
        drones.setEnabled(!active);
        helicopters.setEnabled(!active);
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
                        map.fit(model.incidents());
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
        JTextArea text = new JTextArea(plan.describe(), 12, 70);
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        int choice = JOptionPane.showOptionDialog(this, new JScrollPane(text), "Approve this mission?",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, new Object[] {"Launch", "Cancel"}, "Launch");
        try {
            if (choice == 0) {
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
        long count = model.incidents().stream().filter(i -> !i.isActive()).count();
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
        appendLog("Fleet reset. A new fleet will be placed " + (int) DEPOT_KM + " km south of the next incident you plan.");
        map.repaint();
        updateButtons();
    }

    /** Builds the fleet on first use, parked south of the incident that is planned first. */
    private SimulationRig ensureRig(Incident near) {
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
        if (rig != null) {
            rig.control().abortAll("the window was closed");
            rig.control().runUntilSettled(MAX_TICKS_PER_FRAME * 100);
        }
        app.close();
        dispose();
        onExit.run();
    }

    // ---- hooks for tests ------------------------------------------------------------------------

    MapPanel map() {
        return map;
    }

    IncidentTableModel tableModel() {
        return model;
    }

    JTable table() {
        return table;
    }

    SimulationRig rigOrNull() {
        return rig;
    }

    boolean isBusy() {
        return busy;
    }
}
