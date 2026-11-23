package com.fleetops.cli;

import com.fleetops.domain.incident.IncidentType;
import com.fleetops.news.NewsItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads the task file of {@code fleetops agent}. A task is a short incident report in plain words.
 * Tasks are separated by a line of three dashes; lines that start with {@code #} are comments.
 */
final class TaskFile {

    private static final Pattern SEPARATOR = Pattern.compile("^\\s*-{3,}\\s*$");

    private TaskFile() {}

    /** Splits the text of a task file into tasks, each on one line with single spaces. Empty tasks are dropped. */
    static List<String> parse(String text) {
        List<String> tasks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (SEPARATOR.matcher(line).matches()) {
                add(tasks, current);
            } else if (!line.stripLeading().startsWith("#")) {
                current.append(line).append(' ');
            }
        }
        add(tasks, current);
        return tasks;
    }

    /** Reads everything from a reader, for {@code --input -}. */
    static String readAll(BufferedReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            text.append(line).append('\n');
        }
        return text.toString();
    }

    /**
     * Wraps a task as a news item so that it goes through the same verification as a scanned story.
     * The address is made from the text, so the same task is recognised as already stored.
     */
    static NewsItem toNewsItem(String task, Instant now) {
        String title = CliFormat.clip(firstSentence(task), 90);
        return new NewsItem(guessTopic(task), title, task, "task://" + fingerprint(task), "task file", now);
    }

    /** A first guess at the kind of incident, only passed to the AI as a hint; the AI decides the real type. */
    static IncidentType guessTopic(String task) {
        String text = task.toLowerCase(Locale.ROOT);
        if (text.matches(".*\\b(earthquake|quake|tremor|aftershock)\\b.*")) {
            return IncidentType.EARTHQUAKE;
        }
        if (text.matches(".*\\b(wildfire|fire|blaze|burning)\\b.*")) {
            return IncidentType.FIRE;
        }
        if (text.matches(".*\\b(famine|hunger|starvation|starving|food supplies|food shortage)\\b.*")) {
            return IncidentType.FAMINE;
        }
        return IncidentType.FLOOD;
    }

    private static void add(List<String> tasks, StringBuilder current) {
        String task = current.toString().replaceAll("\\s+", " ").strip();
        current.setLength(0);
        if (!task.isEmpty()) {
            tasks.add(task);
        }
    }

    private static String firstSentence(String task) {
        int end = task.indexOf(". ");
        return end > 0 ? task.substring(0, end + 1) : task;
    }

    private static String fingerprint(String task) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(task.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
