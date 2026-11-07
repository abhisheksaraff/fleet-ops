package com.fleetops.plan;

import com.fleetops.ai.JsonException;
import com.fleetops.ai.LlmClient;
import com.fleetops.ai.LlmException;
import com.fleetops.ai.LlmRequest;
import com.fleetops.strategy.SearchStrategies;
import com.fleetops.strategy.SearchStrategy;
import java.util.Map;
import java.util.Objects;

/**
 * Lets an AI model decide how big a response to send. The model's answer is untrusted: it is
 * checked, and any request for more drones or helicopters than exist is cut back to what is ready.
 * An unusable answer gets one correction attempt, then a {@link PlanningException}.
 */
public final class LlmResponsePlanner implements ResponsePlanner {

    private static final int MAX_RATIONALE_CHARS = 400;

    private final LlmClient llm;

    public LlmResponsePlanner(LlmClient llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    @Override
    public ResponseDecision decide(PlanningContext context) {
        Objects.requireNonNull(context, "context");
        if (context.availableDrones() < 1) {
            throw new PlanningException("No drone is ready to launch");
        }
        String question = PlanPrompts.user(context);
        String problem;
        try {
            return read(ask(question, LlmRequest.DEFAULT_TEMPERATURE), context);
        } catch (BadAnswerException e) {
            problem = e.getMessage();
        }
        try {
            return read(ask(PlanPrompts.repair(question, problem), 0.0), context);
        } catch (BadAnswerException e) {
            throw new PlanningException(llm.name() + " gave no usable plan, even after a second try: " + e.getMessage(), e);
        }
    }

    private Object ask(String user, double temperature) {
        try {
            return llm.completeJson(new LlmRequest(PlanPrompts.SYSTEM, user, true, temperature));
        } catch (LlmException e) {
            if (e.getCause() instanceof JsonException) {
                throw new BadAnswerException(e.getMessage());
            }
            throw e;
        }
    }

    private ResponseDecision read(Object json, PlanningContext context) {
        if (!(json instanceof Map<?, ?> fields)) {
            throw new BadAnswerException("The answer must be one JSON object");
        }
        int askedDrones = wholeNumber(fields.get("drones"), "drones");
        if (askedDrones < 1) {
            throw new BadAnswerException("\"drones\" must be at least 1");
        }
        int askedHelicopters = wholeNumber(fields.get("helicopters"), "helicopters");
        if (askedHelicopters < 0) {
            throw new BadAnswerException("\"helicopters\" must not be negative");
        }
        int askedRobodogs = fields.get("robodogs") == null ? 0 : wholeNumber(fields.get("robodogs"), "robodogs");
        if (askedRobodogs < 0) {
            throw new BadAnswerException("\"robodogs\" must not be negative");
        }
        SearchStrategy strategy = readStrategy(fields.get("strategy"));

        StringBuilder rationale = new StringBuilder(readRationale(fields.get("rationale")));
        int drones = Math.min(askedDrones, context.availableDrones());
        if (drones < askedDrones) {
            rationale.append(" [AI asked for ").append(askedDrones).append(" drones; only ").append(drones).append(" are ready.]");
        }
        int helicopters = Math.min(askedHelicopters, context.availableHelicopters());
        if (helicopters < askedHelicopters) {
            rationale.append(" [AI asked for ").append(askedHelicopters).append(" helicopters; only ").append(helicopters).append(" are ready.]");
        }
        int robodogs = Math.min(askedRobodogs, context.availableRobodogs());
        if (robodogs < askedRobodogs) {
            rationale.append(" [AI asked for ").append(askedRobodogs).append(" robodogs; only ").append(robodogs).append(" are ready.]");
        }
        return new ResponseDecision(drones, strategy, helicopters, robodogs, rationale.toString(), llm.name());
    }

    private static int wholeNumber(Object value, String name) {
        double number;
        if (value instanceof Number n) {
            number = n.doubleValue();
        } else if (value instanceof String s) {
            try {
                number = Double.parseDouble(s.strip());
            } catch (NumberFormatException e) {
                throw new BadAnswerException("\"" + name + "\" must be a number, not \"" + s + "\"");
            }
        } else {
            throw new BadAnswerException("\"" + name + "\" is missing or not a number");
        }
        if (Double.isNaN(number) || Double.isInfinite(number) || Math.abs(number) > 1_000_000) {
            throw new BadAnswerException("\"" + name + "\" is not a sensible number");
        }
        return (int) Math.round(number);
    }

    private static SearchStrategy readStrategy(Object value) {
        if (value instanceof String s) {
            try {
                return SearchStrategies.parse(s);
            } catch (IllegalArgumentException e) {
                // fall through to the message below
            }
        }
        throw new BadAnswerException("\"strategy\" must be \"lawnmower\" or \"spiral\"");
    }

    private static String readRationale(Object value) {
        String text = value instanceof String s ? s.strip() : "";
        if (text.isEmpty()) {
            return "(the AI gave no explanation)";
        }
        return text.length() <= MAX_RATIONALE_CHARS ? text : text.substring(0, MAX_RATIONALE_CHARS - 3).stripTrailing() + "...";
    }

    /** The model answered, but not in the format we asked for. */
    private static final class BadAnswerException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BadAnswerException(String message) {
            super(message);
        }
    }
}
