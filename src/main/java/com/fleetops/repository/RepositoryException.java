package com.fleetops.repository;

/**
 * A repository could not do its job because the storage failed (for example the database file is
 * locked or the stored data is damaged). It is unchecked so that {@link IncidentRepository} looks the
 * same whatever sits behind it.
 */
public class RepositoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
