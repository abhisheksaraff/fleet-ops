package com.fleetops.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import picocli.CommandLine;

/**
 * Tab completion for the console: command names, the options of the command being typed, incident
 * numbers after {@code show} and {@code run}, and topic names after {@code --topic}.
 */
final class ConsoleCompleter {

    /** What Tab produced: the new text of the line, and the options to list when it could not decide. */
    record Result(String line, List<String> candidates) {}

    private static final List<String> TOPICS = List.of("flood", "famine", "fire", "earthquake");

    private final CommandLine cli;
    private final List<String> builtIns;
    private final Supplier<List<Long>> incidentIds;
    private final Function<String, List<String>> windowArguments;

    ConsoleCompleter(CommandLine cli, List<String> builtIns, Supplier<List<Long>> incidentIds,
            Function<String, List<String>> windowArguments) {
        this.cli = cli;
        this.builtIns = builtIns;
        this.incidentIds = incidentIds;
        this.windowArguments = windowArguments;
    }

    Result complete(String line) {
        boolean endsWithSpace = line.endsWith(" ");
        String stripped = line.stripLeading();
        String[] parts = stripped.isEmpty() ? new String[0] : stripped.split("\\s+");
        String current = endsWithSpace || parts.length == 0 ? "" : parts[parts.length - 1];
        String head = line.substring(0, line.length() - current.length());
        int index = endsWithSpace || parts.length == 0 ? parts.length : parts.length - 1;

        Set<String> pool = new TreeSet<>();
        if (index == 0) {
            pool.addAll(cli.getSubcommands().keySet());
            pool.addAll(builtIns);
        } else {
            String command = parts[0];
            String previous = index >= 1 ? parts[index - 1] : "";
            CommandLine sub = cli.getSubcommands().get(command);
            if (previous.equals("-t") || previous.equals("--topic")) {
                pool.addAll(TOPICS);
            } else if (sub == null && WindowCommands.handles(command)) {
                if (index == 1) {
                    pool.addAll(windowArguments.apply(command));
                }
            } else if (sub != null) {
                boolean takesId = command.equals("show") || command.equals("run");
                if (takesId && index == 1 && !current.startsWith("-")) {
                    incidentIds.get().forEach(id -> pool.add(String.valueOf(id)));
                }
                if (current.startsWith("-") || !takesId || index > 1) {
                    sub.getCommandSpec().options().forEach(o -> pool.addAll(List.of(o.names())));
                }
            }
        }
        List<String> matches = new ArrayList<>();
        for (String candidate : pool) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(current.toLowerCase(Locale.ROOT))) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            return new Result(line, List.of());
        }
        if (matches.size() == 1) {
            return new Result(head + matches.get(0) + " ", List.of());
        }
        String prefix = commonPrefix(matches);
        if (prefix.length() > current.length()) {
            return new Result(head + prefix, List.of());
        }
        return new Result(line, matches);
    }

    private static String commonPrefix(List<String> words) {
        String prefix = words.get(0);
        for (String word : words) {
            while (!word.startsWith(prefix)) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
        }
        return prefix;
    }
}
