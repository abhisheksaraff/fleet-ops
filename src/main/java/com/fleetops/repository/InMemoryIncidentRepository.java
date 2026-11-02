package com.fleetops.repository;

import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Keeps incidents in memory. Safe to use from several threads; nothing survives a restart. */
public final class InMemoryIncidentRepository implements IncidentRepository {

    private final Map<Long, Incident> byId = new LinkedHashMap<>();
    private final Map<String, Long> idBySourceKey = new HashMap<>();
    private long nextId = 1;

    @Override
    public synchronized Incident save(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        if (incident.id().isPresent()) {
            throw new IllegalArgumentException("Incident was already stored with id " + incident.id().get());
        }
        Optional<String> key = sourceKey(incident);
        if (key.isPresent() && idBySourceKey.containsKey(key.get())) {
            throw new DuplicateIncidentException(incident.sourceUrl().orElseThrow());
        }
        long id = nextId++;
        incident.assignId(id);
        byId.put(id, incident);
        key.ifPresent(k -> idBySourceKey.put(k, id));
        return incident;
    }

    @Override
    public synchronized void update(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        long id = incident.id().orElseThrow(() -> new IllegalArgumentException("Incident was never saved: " + incident.title()));
        if (!byId.containsKey(id)) {
            throw new IllegalArgumentException("No stored incident with id " + id);
        }
        byId.put(id, incident);
    }

    @Override
    public synchronized Optional<Incident> findById(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public synchronized Optional<Incident> findBySourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }
        Long id = idBySourceKey.get(SourceUrls.normalize(sourceUrl));
        return id == null ? Optional.empty() : Optional.of(byId.get(id));
    }

    @Override
    public synchronized List<Incident> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public synchronized List<Incident> findByState(IncidentState state) {
        Objects.requireNonNull(state, "state");
        return byId.values().stream().filter(i -> i.state() == state).toList();
    }

    private static Optional<String> sourceKey(Incident incident) {
        return incident.sourceUrl().filter(url -> !url.isBlank()).map(SourceUrls::normalize);
    }
}
