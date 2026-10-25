package com.fleetops.sim;

import com.fleetops.domain.fleet.Fleet;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.Survivor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The state of the simulated world: the fleet and every incident area with its survivors. */
public final class World {

    private final Fleet fleet;
    private final List<IncidentZone> zones = new ArrayList<>();

    public World(Fleet fleet) {
        this.fleet = Objects.requireNonNull(fleet, "fleet");
    }

    public Fleet fleet() {
        return fleet;
    }

    public List<IncidentZone> zones() {
        return List.copyOf(zones);
    }

    /**
     * Adds an incident area.
     *
     * @throws IllegalArgumentException if the incident is already in the world or a survivor id is taken
     */
    public void addZone(IncidentZone zone) {
        Objects.requireNonNull(zone, "zone");
        if (zoneFor(zone.incident()).isPresent()) {
            throw new IllegalArgumentException(zone.incident() + " is already in the world");
        }
        for (Survivor survivor : zone.survivors()) {
            if (findSurvivor(survivor.id()).isPresent()) {
                throw new IllegalArgumentException("Survivor id " + survivor.id() + " is already in use");
            }
        }
        zones.add(zone);
    }

    public Optional<IncidentZone> zoneFor(Incident incident) {
        return zones.stream().filter(zone -> zone.incident() == incident).findFirst();
    }

    public Optional<IncidentZone> zoneOf(Survivor survivor) {
        return zones.stream().filter(zone -> zone.survivors().contains(survivor)).findFirst();
    }

    /** Every survivor in every area. */
    public List<Survivor> survivors() {
        return zones.stream().flatMap(zone -> zone.survivors().stream()).toList();
    }

    public Optional<Survivor> findSurvivor(String id) {
        return survivors().stream().filter(survivor -> survivor.id().equals(id)).findFirst();
    }
}
