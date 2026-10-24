package com.fleetops.domain.incident;

import com.fleetops.domain.geo.GeoArea;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A disaster extracted from news or Telegram: what happened, where (a square {@link GeoArea}), how
 * severe it is and how far it has progressed through its lifecycle.
 *
 * <p>Everything except the id and the state is fixed at creation. Lifecycle changes go through the
 * event methods ({@link #verify()}, {@link #plan()}, ...), which delegate to the current
 * {@link IncidentState}. Create instances with {@link #builder(IncidentType, String, GeoArea)}.
 */
public final class Incident {

    private Long id;
    private final IncidentType type;
    private final String title;
    private final String summary;
    private final GeoArea area;
    private final Severity severity;
    private final int estimatedPeople;
    private final String sourceUrl;
    private final Instant reportedAt;
    private IncidentState state;

    private Incident(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.title = b.title;
        this.summary = b.summary;
        this.area = b.area;
        this.severity = b.severity;
        this.estimatedPeople = b.estimatedPeople;
        this.sourceUrl = b.sourceUrl;
        this.reportedAt = b.reportedAt;
        this.state = b.state;
    }

    /** Starts building an incident; type, title and area are required. */
    public static Builder builder(IncidentType type, String title, GeoArea area) {
        return new Builder(type, title, area);
    }

    // ---- lifecycle events (delegated to the current state) -------------------------------------

    public void verify() {
        state = state.verify();
    }

    public void reject() {
        state = state.reject();
    }

    public void markDuplicate() {
        state = state.markDuplicate();
    }

    public void plan() {
        state = state.plan();
    }

    public void start() {
        state = state.start();
    }

    public void complete() {
        state = state.complete();
    }

    public void cancel() {
        state = state.cancel();
    }

    // ---- accessors -----------------------------------------------------------------------------

    /** The database id, empty until the incident has been stored. */
    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    /** Called once by the repository when the incident is first stored. */
    public void assignId(long newId) {
        if (id != null) {
            throw new IllegalStateException("Incident already has id " + id);
        }
        this.id = newId;
    }

    public IncidentType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public GeoArea area() {
        return area;
    }

    public Severity severity() {
        return severity;
    }

    public int estimatedPeople() {
        return estimatedPeople;
    }

    public Optional<String> sourceUrl() {
        return Optional.ofNullable(sourceUrl);
    }

    public Instant reportedAt() {
        return reportedAt;
    }

    public IncidentState state() {
        return state;
    }

    /** True while the incident can still change (it is not completed, rejected or a duplicate). */
    public boolean isActive() {
        return !state.isTerminal();
    }

    @Override
    public String toString() {
        return "Incident[" + (id == null ? "unsaved" : "#" + id) + " " + type + " '" + title + "' " + state + "]";
    }

    /** Collects the fields of an {@link Incident}; optional fields have sensible defaults. */
    public static final class Builder {
        private Long id;
        private final IncidentType type;
        private final String title;
        private final GeoArea area;
        private String summary = "";
        private Severity severity = Severity.MODERATE;
        private int estimatedPeople = 0;
        private String sourceUrl;
        private Instant reportedAt = Instant.now();
        private IncidentState state = IncidentState.NEW;

        private Builder(IncidentType type, String title, GeoArea area) {
            this.type = Objects.requireNonNull(type, "type");
            this.area = Objects.requireNonNull(area, "area");
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            this.title = title.strip();
        }

        public Builder summary(String summary) {
            this.summary = summary == null ? "" : summary.strip();
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = Objects.requireNonNull(severity, "severity");
            return this;
        }

        public Builder estimatedPeople(int estimatedPeople) {
            if (estimatedPeople < 0) {
                throw new IllegalArgumentException("estimatedPeople must not be negative: " + estimatedPeople);
            }
            this.estimatedPeople = estimatedPeople;
            return this;
        }

        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = (sourceUrl == null || sourceUrl.isBlank()) ? null : sourceUrl.strip();
            return this;
        }

        public Builder reportedAt(Instant reportedAt) {
            this.reportedAt = Objects.requireNonNull(reportedAt, "reportedAt");
            return this;
        }

        /** Used when loading a stored incident back from the database. */
        public Builder id(long id) {
            this.id = id;
            return this;
        }

        /** Used when loading a stored incident back from the database. */
        public Builder state(IncidentState state) {
            this.state = Objects.requireNonNull(state, "state");
            return this;
        }

        public Incident build() {
            return new Incident(this);
        }
    }
}
