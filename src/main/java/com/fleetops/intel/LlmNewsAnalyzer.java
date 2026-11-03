package com.fleetops.intel;

import com.fleetops.ai.JsonException;
import com.fleetops.ai.LlmClient;
import com.fleetops.ai.LlmException;
import com.fleetops.ai.LlmRequest;
import com.fleetops.news.NewsItem;
import java.util.Objects;

/**
 * Asks an {@link LlmClient} to assess a news item. If the answer is not usable (not JSON, wrong
 * fields, impossible coordinates) the model is told what was wrong and given exactly one more try.
 */
public final class LlmNewsAnalyzer implements NewsAnalyzer {

    private final LlmClient llm;

    public LlmNewsAnalyzer(LlmClient llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    @Override
    public Assessment analyze(NewsItem item) {
        Objects.requireNonNull(item, "item");
        String question = NewsPrompts.user(item);
        String problem;
        try {
            return Assessment.fromJson(ask(question, LlmRequest.DEFAULT_TEMPERATURE), item);
        } catch (InvalidAssessmentException e) {
            problem = e.getMessage();
        } catch (BadJsonException e) {
            problem = e.getMessage();
        }
        try {
            return Assessment.fromJson(ask(NewsPrompts.repair(question, problem), 0.0), item);
        } catch (InvalidAssessmentException | BadJsonException e) {
            throw new AnalysisException(llm.name() + " gave no usable assessment, even after a second try: " + e.getMessage(), e);
        }
    }

    private Object ask(String user, double temperature) {
        try {
            return llm.completeJson(new LlmRequest(NewsPrompts.SYSTEM, user, true, temperature));
        } catch (LlmException e) {
            if (e.getCause() instanceof JsonException) {
                throw new BadJsonException(e.getMessage());
            }
            throw e;
        }
    }

    /** The model answered, but not with JSON. */
    private static final class BadJsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BadJsonException(String message) {
            super(message);
        }
    }
}
