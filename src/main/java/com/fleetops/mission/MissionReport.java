package com.fleetops.mission;

import com.fleetops.domain.incident.Incident;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The outcome of a finished mission.
 *
 * @param incident the incident that was answered
 * @param status how the mission ended (never {@code RUNNING})
 * @param people how many people were in the area
 * @param rescued how many were picked up
 * @param foundNotRescued how many were found but could not be picked up
 * @param notFound how many were never found
 * @param ticks simulation steps from launch to the end
 * @param seconds simulated time from launch to the end
 * @param notes what went wrong or was unusual, for the operator
 * @param drones how many drones were sent
 * @param robodogs how many robodogs were sent
 * @param helicopters how many helicopters were sent
 * @param operatorChange what the operator changed in the AI's plan, or empty if nothing
 */
public record MissionReport(
        Incident incident,
        MissionStatus status,
        int people,
        int rescued,
        int foundNotRescued,
        int notFound,
        long ticks,
        double seconds,
        List<String> notes,
        int drones,
        int robodogs,
        int helicopters,
        String operatorChange) {

    /** A report that does not say which units were sent. */
    public MissionReport(Incident incident, MissionStatus status, int people, int rescued, int foundNotRescued,
            int notFound, long ticks, double seconds, List<String> notes) {
        this(incident, status, people, rescued, foundNotRescued, notFound, ticks, seconds, notes, 0, 0, 0, "");
    }

    public MissionReport {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(status, "status");
        if (!status.isFinished()) {
            throw new IllegalArgumentException("A report needs a finished mission, not " + status);
        }
        notes = List.copyOf(notes);
        operatorChange = operatorChange == null ? "" : operatorChange;
    }

    /** A multi-line summary for the console or the report. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Mission %s: %s%n", status, incident.title()));
        text.append(String.format(Locale.ROOT, "  %d of %d people rescued", rescued, people));
        if (foundNotRescued > 0) {
            text.append(String.format(Locale.ROOT, ", %d found but not rescued", foundNotRescued));
        }
        if (notFound > 0) {
            text.append(String.format(Locale.ROOT, ", %d never found", notFound));
        }
        text.append(String.format(Locale.ROOT, " (%.1f minutes simulated, %d ticks)", seconds / 60.0, ticks));
        notes.forEach(note -> text.append("\n  Note: ").append(note));
        return text.toString();
    }
}
