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
 * @param robodogs how many robodogs the rover drops off to search the ground near its drop-off points (may be 0)
 * @param rationale why, in words an operator can read
 * @param plannedBy who decided, for example {@code rules} or {@code gemini:gemini-2.5-flash}
 */
public record ResponseDecision(
        int drones, SearchStrategy strategy, int helicopters, int robodogs, String rationale, String plannedBy) {

    public ResponseDecision {
        if (drones < 1) {
            throw new IllegalArgumentException("At least one drone is needed: " + drones);
        }
        if (helicopters < 0) {
            throw new IllegalArgumentException("Helicopters must not be negative: " + helicopters);
        }
        if (robodogs < 0) {
            throw new IllegalArgumentException("Robodogs must not be negative: " + robodogs);
        }
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(plannedBy, "plannedBy");
    }

    /** A decision without robodogs. */
    public ResponseDecision(int drones, SearchStrategy strategy, int helicopters, String rationale, String plannedBy) {
        this(drones, strategy, helicopters, 0, rationale, plannedBy);
    }

    /** What the operator changed in the AI's plan, such as "drones 3 to 5, robodogs 1 to 2", if anything. */
    public java.util.Optional<String> operatorChanges() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[Operator changed ([^\\]]*?)\\.\\]").matcher(rationale);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return java.util.Optional.ofNullable(last);
    }

    public ResponseDecision withDrones(int newDrones) {
        return new ResponseDecision(newDrones, strategy, helicopters, robodogs, rationale, plannedBy);
    }

    public ResponseDecision withRobodogs(int newRobodogs) {
        return new ResponseDecision(drones, strategy, helicopters, newRobodogs, rationale, plannedBy);
    }

    /** The ending of {@code plannedBy} once an operator has changed the decision. */
    public static final String OPERATOR_SUFFIX = " + operator";

    /**
     * The decision after an operator changed it. The rationale keeps the planner's words and says what
     * was changed, and {@code plannedBy} gets {@value #OPERATOR_SUFFIX}, so the plan always shows who
     * decided what. Returns this decision unchanged when nothing differs.
     */
    public ResponseDecision edited(int newDrones, SearchStrategy newStrategy, int newHelicopters) {
        return edited(newDrones, newStrategy, newHelicopters, robodogs);
    }

    /** Like {@link #edited(int, SearchStrategy, int)}, and the operator can change the robodog count too. */
    public ResponseDecision edited(int newDrones, SearchStrategy newStrategy, int newHelicopters, int newRobodogs) {
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
        if (newRobodogs != robodogs) {
            changes.add("robodogs " + robodogs + " to " + newRobodogs);
        }
        if (changes.isEmpty()) {
            return this;
        }
        return new ResponseDecision(newDrones, newStrategy, newHelicopters, newRobodogs,
                rationale + " [Operator changed " + String.join(", ", changes) + ".]",
                plannedBy.endsWith(OPERATOR_SUFFIX) ? plannedBy : plannedBy + OPERATOR_SUFFIX);
    }

    public ResponseDecision withRationale(String newRationale) {
        return new ResponseDecision(drones, strategy, helicopters, robodogs, newRationale, plannedBy);
    }
}
