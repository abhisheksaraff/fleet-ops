package com.fleetops.intel;

import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.domain.incident.IncidentType;
import com.fleetops.news.NewsItem;
import com.fleetops.repository.IncidentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Incident reports typed or loaded by the operator, in plain words, instead of found in the news. The
 * command line ({@code fleetops agent}) and the map window ("Report incident") both use this class, so a
 * report is verified, stored and de-duplicated in exactly one way.
 *
 * <p>A task file holds one or more reports separated by a line of three dashes; lines that start with
 * {@code #} are comments.
 */
public final class TaskReports {

    private static final Pattern SEPARATOR = Pattern.compile("^\\s*-{3,}\\s*$");

    /**
     * What became of one report.
     *
     * @param status the result
     * @param incident the incident, when there is one (always present for {@code VERIFIED})
     * @param reason why, in plain words
     */
    public record Submission(Status status, Optional<Incident> incident, String reason) {

        /** The result for one report. */
        public enum Status {
            /** A verified incident is waiting to be planned. */
            VERIFIED,
            /** The incident is known already and cannot be planned again. */
            DUPLICATE,
            /** The AI judged the report not to be a usable incident. */
            REJECTED,
            /** The AI could not be used right now. */
            FAILED
        }

        /** True if a verified incident is ready to be planned. */
        public boolean usable() {
            return status == Status.VERIFIED && incident.isPresent();
        }
    }

    private TaskReports() {}

    /** Splits the text of a task file into reports, each on one line with single spaces. Empty ones are dropped. */
    public static List<String> parse(String text) {
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

    /**
     * Has the AI verify one report exactly as it would verify a scanned story, and stores a new incident.
     * A report that was stored by an earlier run and has not been planned yet is returned as {@code VERIFIED}
     * again, so a dry run does not use the report up.
     */
    public static Submission submit(IntelligenceService intelligence, IncidentRepository repository, String task, Instant now) {
        NewsItem item = toNewsItem(task, now);
        IngestOutcome outcome = intelligence.ingest(item);
        switch (outcome.status()) {
            case CREATED:
                return new Submission(Submission.Status.VERIFIED, outcome.incident(), outcome.reason());
            case DUPLICATE:
                if (outcome.incident().isPresent()) {
                    return new Submission(Submission.Status.DUPLICATE, outcome.incident(), outcome.reason());
                }
                Optional<Incident> stored = repository.findBySourceUrl(item.url());
                if (stored.isPresent() && stored.get().state() == IncidentState.VERIFIED) {
                    return new Submission(Submission.Status.VERIFIED, stored, "this report is already stored and has not been run yet");
                }
                String why = stored.map(i -> "this report is already stored as incident #" + i.id().map(String::valueOf).orElse("?")
                        + " (" + i.state() + "); clear finished incidents to run it again").orElse(outcome.reason());
                return new Submission(Submission.Status.DUPLICATE, stored, why);
            case REJECTED:
                return new Submission(Submission.Status.REJECTED, Optional.empty(), outcome.reason());
            default:
                return new Submission(Submission.Status.FAILED, Optional.empty(), outcome.reason());
        }
    }

    /**
     * Wraps a report as a news item so that it goes through the same verification as a scanned story.
     * The address is made from the text, so the same report is recognised as already stored.
     */
    static NewsItem toNewsItem(String task, Instant now) {
        String title = clip(firstSentence(task), 90);
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

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
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
