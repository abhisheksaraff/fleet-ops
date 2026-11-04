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
        List<String> notes) {

    public MissionReport {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(status, "status");
        if (!status.isFinished()) {
            throw new IllegalArgumentException("A report needs a finished mission, not " + status);
        }
        notes = List.copyOf(notes);
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
