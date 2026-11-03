package com.fleetops.intel;

import com.fleetops.domain.incident.Incident;
import java.util.Objects;
import java.util.Optional;

/**
 * What happened to one news item during a scan.
 *
 * @param item the item
 * @param status the result
 * @param incident the stored incident (for {@code CREATED}, and {@code DUPLICATE} when it was stored as a duplicate)
 * @param reason why, in plain words
 */
public record IngestOutcome(com.fleetops.news.NewsItem item, Status status, Optional<Incident> incident, String reason) {

    /** The result for one item. */
    public enum Status {
        /** Verified and stored as a new incident. */
        CREATED,
        /** Describes an incident that is already known. */
        DUPLICATE,
        /** The AI judged it not a usable incident; it will not be looked at again. */
        REJECTED,
        /** The AI could not be used right now; the item stays eligible for the next scan. */
        FAILED
    }

    public IngestOutcome {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(reason, "reason");
    }
}
