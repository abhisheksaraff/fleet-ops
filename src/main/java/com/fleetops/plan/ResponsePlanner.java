package com.fleetops.plan;

/**
 * Decides how to respond to an incident: the Strategy pattern applied to planning. Fixed rules, an
 * AI model, or rules as a safety net behind an AI model can all be plugged in without changing the
 * code that uses the decision.
 */
public interface ResponsePlanner {

    /**
     * @throws PlanningException if no decision can be made (for example, no drone is available)
     */
    ResponseDecision decide(PlanningContext context);
}
