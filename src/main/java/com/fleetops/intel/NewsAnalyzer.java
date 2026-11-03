package com.fleetops.intel;

import com.fleetops.ai.LlmException;
import com.fleetops.news.NewsItem;

/**
 * Judges one news item. The service depends only on this interface, so the judge can be an AI
 * model today and something else (a rules engine, a different vendor) tomorrow.
 */
public interface NewsAnalyzer {

    /**
     * @throws LlmException if the AI service cannot be used
     * @throws AnalysisException if the AI keeps giving unusable answers
     */
    Assessment analyze(NewsItem item);
}
