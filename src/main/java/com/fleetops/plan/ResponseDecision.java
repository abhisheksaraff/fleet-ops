package com.fleetops.plan;

import com.fleetops.strategy.SearchStrategy;
import java.util.Objects;

/**
 * How big a response to send, and how to search.
 *
 * @param drones how many drones to launch (at least 1)
 * @param strategy the search pattern the drones fly
 * @param helicopters how many helicopters to have ready for pick-ups
 * @param rationale why, in words an operator can read
 * @param plannedBy who decided, for example {@code rules} or {@code gemini:gemini-2.5-flash}
 */
public record ResponseDecision(int drones, SearchStrategy strategy, int helicopters, String rationale, String plannedBy) {

    public ResponseDecision {
        if (drones < 1) {
            throw new IllegalArgumentException("At least one drone is needed: " + drones);
        }
        if (helicopters < 0) {
            throw new IllegalArgumentException("Helicopters must not be negative: " + helicopters);
        }
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(plannedBy, "plannedBy");
    }

    public ResponseDecision withDrones(int newDrones) {
        return new ResponseDecision(newDrones, strategy, helicopters, rationale, plannedBy);
    }

    public ResponseDecision withRationale(String newRationale) {
        return new ResponseDecision(drones, strategy, helicopters, newRationale, plannedBy);
    }
}
