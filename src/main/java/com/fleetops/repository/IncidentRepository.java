package com.fleetops.repository;

import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import java.util.List;
import java.util.Optional;

/**
 * Where incidents are stored: the Repository pattern. The rest of the program only knows this
 * interface, so the in-memory version used by tests and demos can be swapped for a database one
 * without changing any other class.
 *
 * <p>An incident is identified by the article it came from: saving a second incident with the same
 * (normalized) source URL is refused, which is how the system avoids duplicates.
 */
public interface IncidentRepository {

    /**
     * Stores a new incident and gives it its id.
     *
     * @return the same incident, now with an id
     * @throws DuplicateIncidentException if an incident from the same source URL is already stored
     * @throws IllegalArgumentException if the incident was already stored
     */
    Incident save(Incident incident);

    /**
     * Records changes to a stored incident, such as a new lifecycle state.
     *
     * @throws IllegalArgumentException if the incident was never saved
     */
    void update(Incident incident);

    Optional<Incident> findById(long id);

    /** Finds the incident that came from this article, however the link is spelled. */
    Optional<Incident> findBySourceUrl(String sourceUrl);

    /** All incidents, oldest first. */
    List<Incident> findAll();

    List<Incident> findByState(IncidentState state);

    default boolean existsBySourceUrl(String sourceUrl) {
        return findBySourceUrl(sourceUrl).isPresent();
    }

    /** Incidents that are not finished (not completed, rejected or duplicate). */
    default List<Incident> findActive() {
        return findAll().stream().filter(Incident::isActive).toList();
    }
}
