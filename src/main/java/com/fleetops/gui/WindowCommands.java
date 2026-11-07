package com.fleetops.gui;

import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The console commands that drive the window itself rather than the database: {@code plan},
 * {@code edit}, {@code launch}, {@code cancel}, {@code abort}, {@code debrief}, {@code focus}, {@code speed},
 * {@code fleet} and {@code layout}. This class only reads and checks what was typed; the window does the work.
 */
final class WindowCommands {

    /** Where a command's answer goes. */
    interface Output {
        void line(String text);

        void error(String text);
    }

    /** The names, in the order {@code help} lists them. */
    static final List<String> NAMES = List.of("plan", "edit", "launch", "cancel", "abort", "debrief", "focus", "speed", "fleet", "layout");

    private final MainFrame frame;

    WindowCommands(MainFrame frame) {
        this.frame = frame;
    }

    static boolean handles(String name) {
        return NAMES.contains(name);
    }

    /** Runs one command. Must be called on the event thread. */
    void run(String name, List<String> args, Output out) {
        switch (name) {
            case "plan" -> plan(args, out);
            case "edit" -> edit(args, out);
            case "launch" -> frame.consoleLaunch(out);
            case "cancel" -> frame.consoleCancel(out);
            case "abort" -> abort(args, out);
            case "debrief" -> debrief(args, out);
            case "focus" -> focus(args, out);
            case "speed" -> speed(args, out);
            case "fleet" -> fleet(args, out);
            case "layout" -> layout(args, out);
            default -> out.error("Unknown window command '" + name + "'.");
        }
    }

    // ---- missions -------------------------------------------------------------------------------

    private void plan(List<String> args, Output out) {
        Optional<Incident> incident = incidentFrom(args, 1, out, "plan <id>");
        if (incident.isEmpty()) {
            return;
        }
        if (incident.get().state() != IncidentState.VERIFIED) {
            out.error("Incident #" + id(incident.get()) + " is " + incident.get().state()
                    + "; only VERIFIED incidents can be planned.");
            return;
        }
        frame.consolePlan(incident.get(), out);
    }

    private void abort(List<String> args, Output out) {
        if (args.size() > 1 && args.get(1).equalsIgnoreCase("all")) {
            frame.consoleAbortAll(out);
            return;
        }
        Incident target;
        if (args.size() > 1) {
            Optional<Incident> incident = incidentFrom(args, 1, out, "abort [id|all]");
            if (incident.isEmpty()) {
                return;
            }
            target = incident.get();
        } else {
            target = frame.selectedIncident();
            if (target == null) {
                out.error("Usage: abort <id>  (or select an incident first, or use: abort all)");
                return;
            }
        }
        frame.consoleAbort(target, out);
    }

    private static final String EDIT_USAGE = "edit [drones=N] [pattern=lawnmower|spiral] [helicopters=N] | edit reset";

    private void edit(List<String> args, Output out) {
        Integer drones = null;
        Integer helicopters = null;
        String pattern = null;
        boolean reset = false;
        for (String token : args.subList(1, args.size())) {
            if (token.equalsIgnoreCase("reset")) {
                reset = true;
                continue;
            }
            int equals = token.indexOf('=');
            if (equals < 1) {
                out.error("'" + token + "' is not name=value. Usage: " + EDIT_USAGE);
                return;
            }
            String key = token.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = token.substring(equals + 1);
            switch (key) {
                case "drones", "helicopters" -> {
                    Integer number = parseInt(value);
                    if (number == null) {
                        out.error("'" + value + "' is not a whole number. Usage: " + EDIT_USAGE);
                        return;
                    }
                    if (key.equals("drones")) {
                        drones = number;
                    } else {
                        helicopters = number;
                    }
                }
                case "pattern" -> pattern = value;
                default -> {
                    out.error("Unknown setting '" + key + "'. Usage: " + EDIT_USAGE);
                    return;
                }
            }
        }
        frame.consoleEdit(drones, pattern, helicopters, reset, out);
    }

    private void debrief(List<String> args, Output out) {
        Incident target;
        if (args.size() > 1) {
            Optional<Incident> incident = incidentFrom(args, 1, out, "debrief [id]");
            if (incident.isEmpty()) {
                return;
            }
            target = incident.get();
        } else {
            target = frame.selectedIncident();
            if (target == null) {
                out.error("Usage: debrief <id>  (or select an incident first)");
                return;
            }
        }
        frame.consoleDebrief(target, out);
    }

    // ---- the map and the clock ------------------------------------------------------------------

    private void focus(List<String> args, Output out) {
        if (args.size() > 1 && args.get(1).equalsIgnoreCase("all")) {
            frame.focusAll();
            out.line("Showing every incident.");
            return;
        }
        Optional<Incident> incident = incidentFrom(args, 1, out, "focus <id|all>");
        if (incident.isPresent()) {
            frame.focusOn(incident.get());
            out.line("Focused on #" + id(incident.get()) + " " + incident.get().title() + ".");
        }
    }

    private void speed(List<String> args, Output out) {
        List<Integer> allowed = frame.speeds();
        if (args.size() < 2) {
            out.line("Simulation speed is " + frame.speed() + "x real time. Choose one of "
                    + allowed.stream().map(String::valueOf).toList() + ".");
            return;
        }
        Integer wanted = parseInt(args.get(1));
        if (wanted == null || !allowed.contains(wanted)) {
            out.error("Speed must be one of " + allowed.stream().map(String::valueOf).toList() + ".");
            return;
        }
        frame.setSpeed(wanted);
        out.line("Simulation speed set to " + wanted + "x real time.");
    }

    private void fleet(List<String> args, Output out) {
        if (args.size() == 1) {
            out.line(frame.fleetSummary());
            return;
        }
        if (args.size() != 4) {
            out.error("Usage: fleet <rovers> <drones per rover> <helicopters>   (or just: fleet)");
            return;
        }
        Integer rovers = parseInt(args.get(1));
        Integer drones = parseInt(args.get(2));
        Integer helicopters = parseInt(args.get(3));
        if (rovers == null || drones == null || helicopters == null) {
            out.error("The fleet sizes must be whole numbers: fleet <rovers> <drones per rover> <helicopters>");
            return;
        }
        frame.consoleSetFleet(rovers, drones, helicopters, out);
    }

    private void layout(List<String> args, Output out) {
        Workspace workspace = frame.workspace();
        if (args.size() < 2) {
            out.line("Layout: " + (workspace.currentPreset() == null ? "custom" : workspace.currentPreset().label)
                    + "  (sidebar " + (workspace.sidebarOpen() ? "open" : "hidden")
                    + ", console " + (workspace.consoleOpen() ? "open" : "hidden") + ")");
            out.line("Use: layout map | list | control | sidebar | console");
            return;
        }
        switch (args.get(1).toLowerCase(Locale.ROOT)) {
            case "map" -> {
                workspace.applyPreset(Workspace.Preset.MAP);
                out.line("Map only. Use the console button in the header to bring this console back.");
            }
            case "list" -> {
                workspace.applyPreset(Workspace.Preset.LIST);
                out.line("Map and list. Use the console button in the header to bring this console back.");
            }
            case "control" -> {
                workspace.applyPreset(Workspace.Preset.CONTROL);
                out.line("Sidebar and console are open.");
            }
            case "sidebar" -> {
                workspace.toggleSidebar();
                out.line("Sidebar " + (workspace.sidebarOpen() ? "shown" : "hidden") + ".");
            }
            case "console" -> {
                out.line("Hiding the console. Use the console button in the header to bring it back.");
                workspace.toggleConsole();
            }
            default -> out.error("Unknown layout '" + args.get(1) + "'. Use: map, list, control, sidebar or console.");
        }
    }

    // ---- reading what was typed -----------------------------------------------------------------

    private Optional<Incident> incidentFrom(List<String> args, int index, Output out, String usage) {
        if (args.size() <= index) {
            out.error("Usage: " + usage);
            return Optional.empty();
        }
        Integer id = parseInt(args.get(index).startsWith("#") ? args.get(index).substring(1) : args.get(index));
        if (id == null) {
            out.error("'" + args.get(index) + "' is not an incident number. Usage: " + usage);
            return Optional.empty();
        }
        Optional<Incident> found = frame.incidentList().all().stream()
                .filter(i -> i.id().map(v -> v == id.longValue()).orElse(false))
                .findFirst();
        if (found.isEmpty()) {
            out.error("No incident with id " + id + ".");
        }
        return found;
    }

    private static String id(Incident incident) {
        return incident.id().map(String::valueOf).orElse("?");
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Candidates for the first argument of a window command, for Tab completion. */
    static List<String> arguments(String command, List<Incident> incidents, List<Integer> speeds) {
        return switch (command) {
            case "plan" -> ids(incidents, i -> i.state() == IncidentState.VERIFIED);
            case "abort" -> {
                java.util.ArrayList<String> out = new java.util.ArrayList<>(ids(incidents, i -> i.state() == IncidentState.IN_PROGRESS));
                out.add("all");
                yield out;
            }
            case "edit" -> List.of("drones=", "pattern=lawnmower", "pattern=spiral", "helicopters=", "reset");
            case "debrief" -> ids(incidents, i -> true);
            case "focus" -> {
                java.util.ArrayList<String> out = new java.util.ArrayList<>(ids(incidents, i -> true));
                out.add("all");
                yield out;
            }
            case "speed" -> speeds.stream().map(String::valueOf).toList();
            case "layout" -> Arrays.asList("map", "list", "control", "sidebar", "console");
            default -> List.of();
        };
    }

    private static List<String> ids(List<Incident> incidents, java.util.function.Predicate<Incident> keep) {
        return incidents.stream().filter(keep).flatMap(i -> i.id().stream()).map(String::valueOf).toList();
    }
}
