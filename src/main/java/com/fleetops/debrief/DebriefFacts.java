package com.fleetops.debrief;

import com.fleetops.mission.MissionReport;
import com.fleetops.mission.MissionStatus;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The hard numbers of a finished mission, with no wording around them. They are written into the AI's
 * question as a header the program generates ({@link #toHeader()}) and read back from it by the
 * offline stand-in ({@link #fromHeader(String)}), so the two always agree on the format.
 *
 * @param status how the mission ended
 * @param people how many people were in the area
 * @param rescued how many were picked up
 * @param foundNotRescued how many were found but not picked up
 * @param notFound how many were never found
 * @param minutes simulated minutes from launch to the end
 * @param notes the operator notes of the report
 */
record DebriefFacts(
        MissionStatus status, int people, int rescued, int foundNotRescued, int notFound, double minutes, List<String> notes) {

    private static final Pattern STATUS = Pattern.compile("^Outcome: (\\w+)", Pattern.MULTILINE);
    private static final Pattern PEOPLE = Pattern.compile("^People in the area: (\\d+)", Pattern.MULTILINE);
    private static final Pattern RESCUED = Pattern.compile("^Rescued: (\\d+)", Pattern.MULTILINE);
    private static final Pattern FOUND = Pattern.compile("^Found but not rescued: (\\d+)", Pattern.MULTILINE);
    private static final Pattern HIDDEN = Pattern.compile("^Never found: (\\d+)", Pattern.MULTILINE);
    private static final Pattern MINUTES = Pattern.compile("^Simulated minutes: ([0-9.]+)", Pattern.MULTILINE);

    DebriefFacts {
        Objects.requireNonNull(status, "status");
        notes = List.copyOf(notes);
    }

    static DebriefFacts of(MissionReport report) {
        return new DebriefFacts(report.status(), report.people(), report.rescued(), report.foundNotRescued(),
                report.notFound(), report.seconds() / 60.0, report.notes());
    }

    /** The numbers as lines of text, one fact per line. */
    String toHeader() {
        return String.format(Locale.ROOT,
                "Outcome: %s%nPeople in the area: %d%nRescued: %d%nFound but not rescued: %d%nNever found: %d%n"
                        + "Simulated minutes: %.1f%n",
                status, people, rescued, foundNotRescued, notFound, minutes);
    }

    /** Reads a header written by {@link #toHeader()}; a missing line counts as zero. */
    static DebriefFacts fromHeader(String header) {
        MissionStatus status;
        try {
            status = MissionStatus.valueOf(find(STATUS, header, "COMPLETED"));
        } catch (IllegalArgumentException e) {
            status = MissionStatus.COMPLETED;
        }
        if (!status.isFinished()) {
            status = MissionStatus.COMPLETED;
        }
        return new DebriefFacts(status, whole(PEOPLE, header), whole(RESCUED, header), whole(FOUND, header),
                whole(HIDDEN, header), Double.parseDouble(find(MINUTES, header, "0")), List.of());
    }

    private static int whole(Pattern pattern, String text) {
        return Integer.parseInt(find(pattern, text, "0"));
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : fallback;
    }
}
