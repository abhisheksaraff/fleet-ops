package com.fleetops.plan;

import com.fleetops.domain.fleet.Helicopter;
import com.fleetops.domain.incident.Severity;
import com.fleetops.strategy.SearchStrategies;

/**
 * A fixed-rule planner: no AI, always the same answer for the same situation. More urgent
 * incidents get more drones (so the area is searched sooner), and one helicopter is wanted for
 * every four people expected. It is also the safety net used when the AI planner fails.
 */
public final class RuleBasedPlanner implements ResponsePlanner {

    public static final int MAX_HELICOPTERS = 3;
    private static final int PEOPLE_PER_HELICOPTER = Helicopter.DEFAULT_CAPACITY;

    /** Drones wanted for a given urgency: 1 (low) to 4 (critical). */
    public static int droneCountFor(Severity severity) {
        return severity.level();
    }

    /** Helicopters wanted for a number of people: one per four, at least 1 and at most {@value #MAX_HELICOPTERS}. */
    public static int helicoptersFor(int estimatedPeople) {
        int needed = (Math.max(estimatedPeople, 1) + PEOPLE_PER_HELICOPTER - 1) / PEOPLE_PER_HELICOPTER;
        return Math.min(MAX_HELICOPTERS, needed);
    }

    @Override
    public ResponseDecision decide(PlanningContext context) {
        if (context.availableDrones() < 1) {
            throw new PlanningException("No drone is ready to launch");
        }
        Severity severity = context.incident().severity();
        int wantedDrones = droneCountFor(severity);
        int drones = Math.min(wantedDrones, context.availableDrones());
        int wantedHelicopters = helicoptersFor(context.incident().estimatedPeople());
        int helicopters = Math.min(wantedHelicopters, context.availableHelicopters());

        StringBuilder why = new StringBuilder()
                .append("Severity ").append(severity).append(" calls for ").append(wantedDrones).append(wantedDrones == 1 ? " drone" : " drones");
        if (drones < wantedDrones) {
            why.append(" (only ").append(drones).append(" ready)");
        }
        why.append("; about ").append(context.incident().estimatedPeople()).append(" people need ")
                .append(wantedHelicopters).append(wantedHelicopters == 1 ? " helicopter" : " helicopters");
        if (helicopters < wantedHelicopters) {
            why.append(" (only ").append(helicopters).append(" ready)");
        }
        why.append("; lawnmower pattern covers the area evenly with the least flying.");
        return new ResponseDecision(drones, SearchStrategies.LAWNMOWER, helicopters, why.toString(), "rules");
    }
}
