package com.fleetops.repository;

/** Thrown when an incident is saved whose source article has already been stored. */
public final class DuplicateIncidentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String sourceUrl;

    public DuplicateIncidentException(String sourceUrl) {
        super("An incident from this source is already stored: " + sourceUrl);
        this.sourceUrl = sourceUrl;
    }

    public String sourceUrl() {
        return sourceUrl;
    }
}
