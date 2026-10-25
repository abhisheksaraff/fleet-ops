package com.fleetops.sim;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.Survivor;
import com.fleetops.domain.incident.SurvivorStatus;
import java.util.List;
import java.util.Objects;

/** An {@link Incident} together with the survivors placed inside its area. */
public final class IncidentZone {

    private final Incident incident;
    private final List<Survivor> survivors;

    /**
     * @throws IllegalArgumentException if a survivor lies outside the incident's area
     */
    public IncidentZone(Incident incident, List<Survivor> survivors) {
        this.incident = Objects.requireNonNull(incident, "incident");
        this.survivors = List.copyOf(Objects.requireNonNull(survivors, "survivors"));
        for (Survivor survivor : this.survivors) {
            if (!incident.area().contains(survivor.position())) {
                throw new IllegalArgumentException(survivor.id() + " is outside the area of " + incident.title());
            }
        }
    }

    public Incident incident() {
        return incident;
    }

    public GeoArea area() {
        return incident.area();
    }

    public List<Survivor> survivors() {
        return survivors;
    }

    public int totalCount() {
        return survivors.size();
    }

    public int hiddenCount() {
        return count(SurvivorStatus.HIDDEN);
    }

    public int foundCount() {
        return count(SurvivorStatus.FOUND);
    }

    public int rescuedCount() {
        return count(SurvivorStatus.RESCUED);
    }

    /** True once there is at least one survivor and every one of them has been rescued. */
    public boolean isCleared() {
        return !survivors.isEmpty() && rescuedCount() == survivors.size();
    }

    private int count(SurvivorStatus status) {
        int count = 0;
        for (Survivor survivor : survivors) {
            if (survivor.status() == status) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "IncidentZone[" + incident.title() + ", " + rescuedCount() + "/" + totalCount() + " rescued]";
    }
}
