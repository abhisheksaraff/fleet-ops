package com.fleetops.intel;

import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentType;
import java.util.List;
import java.util.Map;

/**
 * The result of one scan.
 *
 * @param outcomes what happened to each item that was examined
 * @param sourceFailures topics whose news source could not be read, with the reason
 */
public record IngestReport(List<IngestOutcome> outcomes, Map<IncidentType, String> sourceFailures) {

    public IngestReport {
        outcomes = List.copyOf(outcomes);
        sourceFailures = Map.copyOf(sourceFailures);
    }

    public long count(IngestOutcome.Status status) {
        return outcomes.stream().filter(o -> o.status() == status).count();
    }

    /** The incidents that were newly created and verified. */
    public List<Incident> created() {
        return outcomes.stream()
                .filter(o -> o.status() == IngestOutcome.Status.CREATED)
                .flatMap(o -> o.incident().stream())
                .toList();
    }

    /** One line for the console, for example {@code 2 created, 1 duplicate, 0 rejected, 0 failed}. */
    public String summary() {
        return count(IngestOutcome.Status.CREATED) + " created, " + count(IngestOutcome.Status.DUPLICATE) + " duplicate, "
                + count(IngestOutcome.Status.REJECTED) + " rejected, " + count(IngestOutcome.Status.FAILED) + " failed"
                + (sourceFailures.isEmpty() ? "" : ", " + sourceFailures.size() + " source problem(s)");
    }
}
