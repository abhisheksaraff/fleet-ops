package com.fleetops.debrief;

import com.fleetops.ai.JsonException;
import com.fleetops.ai.LlmClient;
import com.fleetops.ai.LlmException;
import com.fleetops.ai.LlmRequest;
import com.fleetops.mission.MissionReport;
import com.fleetops.mission.MissionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lets an AI model write the debrief. The model gets the hard numbers and the fenced incident text and
 * answers in JSON; its answer is untrusted, so it is checked field by field. An unusable answer gets
 * one correction attempt, then a {@link DebriefException}.
 */
public final class LlmDebriefer extends AbstractDebriefer {

    private final LlmClient llm;

    public LlmDebriefer(LlmClient llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    @Override
    Debrief write(MissionReport report, DebriefFacts facts) {
        String question = DebriefPrompts.user(report, facts);
        String problem;
        try {
            return read(ask(question, LlmRequest.DEFAULT_TEMPERATURE), facts);
        } catch (BadAnswerException e) {
            problem = e.getMessage();
        }
        try {
            return read(ask(DebriefPrompts.repair(question, problem), 0.0), facts);
        } catch (BadAnswerException e) {
            throw new DebriefException(llm.name() + " gave no usable debrief, even after a second try: " + e.getMessage(), e);
        }
    }

    private Object ask(String user, double temperature) {
        try {
            return llm.completeJson(new LlmRequest(DebriefPrompts.SYSTEM, user, true, temperature));
        } catch (LlmException e) {
            if (e.getCause() instanceof JsonException) {
                throw new BadAnswerException(e.getMessage());
            }
            throw e;
        }
    }

    private Debrief read(Object json, DebriefFacts facts) {
        if (!(json instanceof Map<?, ?> fields)) {
            throw new BadAnswerException("The answer must be one JSON object");
        }
        if (!(fields.get("summary") instanceof String summary) || summary.isBlank()) {
            throw new BadAnswerException("\"summary\" must be a sentence or two of text");
        }
        List<String> next = list(fields, "nextTime");
        if (next.stream().allMatch(String::isBlank)) {
            throw new BadAnswerException("\"nextTime\" must hold at least one concrete change to the fleet or the search");
        }
        // Advice that this fleet cannot follow (fewer drones than the area needs, the number already sent, more
        // units than exist) is dropped; if nothing usable is left, the fixed rules supply the lessons.
        List<String> usable = next.stream()
                .filter(item -> !item.isBlank() && DebriefRules.advicePossible(item, facts))
                .toList();
        if (usable.isEmpty()) {
            usable = DebriefRules.nextTime(facts);
        }
        return new Debrief(blamesTheMissing(summary, facts) ? DebriefRules.summary(facts) : summary,
                withoutEmptyClaims(list(fields, "wentWell"), facts),
                withoutEmptyClaims(list(fields, "problems"), facts), usable, llm.name(), true);
    }

    /** True if the summary says an aborted mission was stopped because people were not found, which is backwards. */
    private static boolean blamesTheMissing(String summary, DebriefFacts facts) {
        return facts.status() == MissionStatus.ABORTED
                && Pattern.compile("(?i)abort\\w*\\s+because|because[^.]*\\b(never found|not found|were not found)").matcher(summary).find();
    }

    /** Drops an item that talks about a count that is zero ("no one was never found"), which says nothing. */
    private static List<String> withoutEmptyClaims(List<String> items, DebriefFacts facts) {
        return items.stream()
                .filter(item -> {
                    String text = item.toLowerCase(java.util.Locale.ROOT);
                    boolean emptyFound = facts.foundNotRescued() == 0
                            && (text.contains("found but not rescued") || text.contains("found but not picked up"));
                    boolean emptyHidden = facts.notFound() == 0
                            && (text.contains("never found") || text.contains("not found"));
                    boolean notAProblem = facts.status() == MissionStatus.ABORTED
                            && text.matches("(?s)the mission was (aborted|stopped)( early)?( by the operator)?\\.?");
                    return !emptyFound && !emptyHidden && !notAProblem;
                })
                .toList();
    }

    private static List<String> list(Map<?, ?> fields, String name) {
        Object value = fields.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new BadAnswerException("\"" + name + "\" must be a list of short sentences");
        }
        List<String> out = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof String text)) {
                throw new BadAnswerException("\"" + name + "\" must hold only text");
            }
            out.add(text);
        }
        return out;
    }

    /** The model answered, but not in the format we asked for. */
    private static final class BadAnswerException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BadAnswerException(String message) {
            super(message);
        }
    }
}
