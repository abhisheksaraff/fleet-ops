package com.fleetops.plan;

import com.fleetops.ai.LlmException;
import java.util.Objects;

/**
 * Tries the preferred planner and, if it cannot produce a plan, falls back to a simpler one so a
 * mission is never blocked by an AI outage. The operator is told in the rationale when that
 * happened.
 */
public final class ResilientPlanner implements ResponsePlanner {

    private static final System.Logger LOG = System.getLogger(ResilientPlanner.class.getName());

    private final ResponsePlanner primary;
    private final ResponsePlanner fallback;

    public ResilientPlanner(ResponsePlanner primary, ResponsePlanner fallback) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public ResponseDecision decide(PlanningContext context) {
        try {
            return primary.decide(context);
        } catch (LlmException | PlanningException e) {
            LOG.log(System.Logger.Level.WARNING, "Primary planner failed, using the fallback: {0}", e.getMessage());
            ResponseDecision decision = fallback.decide(context);
            return decision.withRationale(decision.rationale() + " [The AI planner was unavailable: " + e.getMessage() + "]");
        }
    }
}
