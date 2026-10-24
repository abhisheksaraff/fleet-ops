package com.fleetops.domain.incident;

import com.fleetops.domain.geo.GeoPoint;
import java.util.Objects;

/**
 * A person somewhere inside an incident area. Survivors start {@link SurvivorStatus#HIDDEN}; a
 * drone sweep reveals them (they appear as red dots on the map) and a helicopter then rescues them.
 * A survivor can only be rescued after being found.
 */
public final class Survivor {

    private final String id;
    private final GeoPoint position;
    private SurvivorStatus status = SurvivorStatus.HIDDEN;

    public Survivor(String id, GeoPoint position) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.position = Objects.requireNonNull(position, "position");
    }

    public String id() {
        return id;
    }

    public GeoPoint position() {
        return position;
    }

    public SurvivorStatus status() {
        return status;
    }

    /**
     * Marks a hidden survivor as found.
     *
     * @return true if this call revealed the survivor; false if they were already found or rescued
     */
    public boolean reveal() {
        if (status != SurvivorStatus.HIDDEN) {
            return false;
        }
        status = SurvivorStatus.FOUND;
        return true;
    }

    /**
     * Marks a found survivor as rescued.
     *
     * @throws IllegalStateException if the survivor has not been found yet, or is already rescued
     */
    public void markRescued() {
        if (status != SurvivorStatus.FOUND) {
            throw new IllegalStateException("Survivor " + id + " cannot be rescued while " + status);
        }
        status = SurvivorStatus.RESCUED;
    }

    @Override
    public String toString() {
        return "Survivor[" + id + " " + status + "]";
    }
}
