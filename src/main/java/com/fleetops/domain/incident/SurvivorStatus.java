package com.fleetops.domain.incident;

/** Where a survivor is in the rescue: unseen, spotted by a drone, or picked up by a helicopter. */
public enum SurvivorStatus {
    HIDDEN,
    FOUND,
    RESCUED
}
