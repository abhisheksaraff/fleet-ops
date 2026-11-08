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
 * @param drones how many drones were sent
 * @param robodogs how many robodogs were sent
 * @param helicopters how many helicopters were sent
 * @param operatorChange what the operator changed in the AI's plan, or empty
 * @param minDrones the fewest drones that could search the area, or 0 if not known
 * @param maxDrones the most drones that could have been sent, or 0 if not known
 * @param maxHelicopters how many helicopters the fleet has, or 0 if not known
 * @param maxRobodogs how many robodogs the rover carries, or 0 if not known
 */
record DebriefFacts(
        MissionStatus status, int people, int rescued, int foundNotRescued, int notFound, double minutes, List<String> notes,
        int drones, int robodogs, int helicopters, String operatorChange,
        int minDrones, int maxDrones, int maxHelicopters, int maxRobodogs) {

    private static final Pattern STATUS = Pattern.compile("^Outcome: (\\w+)", Pattern.MULTILINE);
    private static final Pattern PEOPLE = Pattern.compile("^People in the area: (\\d+)", Pattern.MULTILINE);
    private static final Pattern RESCUED = Pattern.compile("^Rescued: (\\d+)", Pattern.MULTILINE);
    private static final Pattern FOUND = Pattern.compile("^Found but not rescued: (\\d+)", Pattern.MULTILINE);
    private static final Pattern HIDDEN = Pattern.compile("^Never found: (\\d+)", Pattern.MULTILINE);
    private static final Pattern DRONES = Pattern.compile("^Drones sent: (\\d+)", Pattern.MULTILINE);
    private static final Pattern ROBODOGS = Pattern.compile("^Robodogs sent: (\\d+)", Pattern.MULTILINE);
    private static final Pattern HELICOPTERS = Pattern.compile("^Helicopters sent: (\\d+)", Pattern.MULTILINE);
    private static final Pattern CHANGE = Pattern.compile("^Operator changed the plan: (.+)$", Pattern.MULTILINE);
    private static final Pattern MIN_DRONES = Pattern.compile("^Fewest drones that can search the area: (\\d+)", Pattern.MULTILINE);
    private static final Pattern MAX_DRONES = Pattern.compile("^Drones available: (\\d+)", Pattern.MULTILINE);
    private static final Pattern MAX_HELICOPTERS = Pattern.compile("^Helicopters available: (\\d+)", Pattern.MULTILINE);
    private static final Pattern MAX_ROBODOGS = Pattern.compile("^Robodogs available: (\\d+)", Pattern.MULTILINE);
    private static final Pattern MINUTES = Pattern.compile("^Simulated minutes: ([0-9.]+)", Pattern.MULTILINE);

    DebriefFacts {
        Objects.requireNonNull(status, "status");
        notes = List.copyOf(notes);
        operatorChange = operatorChange == null ? "" : operatorChange;
    }

    static DebriefFacts of(MissionReport report) {
        return new DebriefFacts(report.status(), report.people(), report.rescued(), report.foundNotRescued(),
                report.notFound(), report.seconds() / 60.0, report.notes(),
                report.drones(), report.robodogs(), report.helicopters(), report.operatorChange(),
                report.minDrones(), report.maxDrones(), report.maxHelicopters(), report.maxRobodogs());
    }

    /** The numbers as lines of text, one fact per line. */
    String toHeader() {
        String header = String.format(Locale.ROOT,
                "Outcome: %s%nPeople in the area: %d%nRescued: %d%nFound but not rescued: %d%nNever found: %d%n"
                        + "Simulated minutes: %.1f%nDrones sent: %d%nRobodogs sent: %d%nHelicopters sent: %d%n",
                status, people, rescued, foundNotRescued, notFound, minutes, drones, robodogs, helicopters);
        StringBuilder text = new StringBuilder(header);
        if (minDrones > 0) {
            text.append("Fewest drones that can search the area: ").append(minDrones).append('\n');
        }
        if (maxDrones > 0) {
            text.append("Drones available: ").append(maxDrones).append('\n');
        }
        if (maxHelicopters > 0) {
            text.append("Helicopters available: ").append(maxHelicopters).append('\n');
        }
        if (maxRobodogs > 0) {
            text.append("Robodogs available: ").append(maxRobodogs).append('\n');
        }
        if (!operatorChange.isEmpty()) {
            text.append("Operator changed the plan: ").append(operatorChange).append('\n');
        }
        return text.toString();
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
                whole(HIDDEN, header), Double.parseDouble(find(MINUTES, header, "0")), List.of(),
                whole(DRONES, header), whole(ROBODOGS, header), whole(HELICOPTERS, header), find(CHANGE, header, "").strip(),
                whole(MIN_DRONES, header), whole(MAX_DRONES, header), whole(MAX_HELICOPTERS, header), whole(MAX_ROBODOGS, header));
    }

    private static int whole(Pattern pattern, String text) {
        return Integer.parseInt(find(pattern, text, "0"));
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : fallback;
    }
}
