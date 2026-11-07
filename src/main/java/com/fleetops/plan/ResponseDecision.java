package com.fleetops.plan;

import com.fleetops.strategy.SearchStrategy;
import java.util.ArrayList;
import java.util.List;
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

    /** The ending of {@code plannedBy} once an operator has changed the decision. */
    public static final String OPERATOR_SUFFIX = " + operator";

    /**
     * The decision after an operator changed it. The rationale keeps the planner's words and says what
     * was changed, and {@code plannedBy} gets {@value #OPERATOR_SUFFIX}, so the plan always shows who
     * decided what. Returns this decision unchanged when nothing differs.
     */
    public ResponseDecision edited(int newDrones, SearchStrategy newStrategy, int newHelicopters) {
        Objects.requireNonNull(newStrategy, "newStrategy");
        List<String> changes = new ArrayList<>();
        if (newDrones != drones) {
            changes.add("drones " + drones + " to " + newDrones);
        }
        if (!newStrategy.name().equals(strategy.name())) {
            changes.add("search pattern " + strategy.name() + " to " + newStrategy.name());
        }
        if (newHelicopters != helicopters) {
            changes.add("helicopters " + helicopters + " to " + newHelicopters);
        }
        if (changes.isEmpty()) {
            return this;
        }
        return new ResponseDecision(newDrones, newStrategy, newHelicopters,
                rationale + " [Operator changed " + String.join(", ", changes) + ".]",
                plannedBy.endsWith(OPERATOR_SUFFIX) ? plannedBy : plannedBy + OPERATOR_SUFFIX);
    }

    public ResponseDecision withRationale(String newRationale) {
        return new ResponseDecision(drones, strategy, helicopters, newRationale, plannedBy);
    }
}
