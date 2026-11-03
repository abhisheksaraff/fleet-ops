package com.fleetops.app;

import com.fleetops.ai.LlmClient;
import com.fleetops.ai.LlmClients;
import com.fleetops.config.AppConfig;
import java.util.Objects;

/**
 * Composition root: the single place that decides which concrete implementations the application
 * uses (LLM vendor, database, map, news sources).
 *
 * <p>Both the GUI and the CLI obtain their collaborators from here, and the core packages only ever
 * see interfaces. Later commits add the repository, LLM client and other wiring; swapping a
 * technology means changing the choice made in this class (driven by {@code .env}) and adding one
 * adapter, never touching the core.
 */
public final class AppContext {

    private final AppConfig config;
    private LlmClient llm;

    private AppContext(AppConfig config) {
        this.config = config;
    }

    public static AppContext create(AppConfig config) {
        return new AppContext(Objects.requireNonNull(config, "config"));
    }

    /** Builds a context from {@code ./.env} and the process environment. */
    public static AppContext fromEnvironment() {
        return create(AppConfig.fromEnvironment());
    }

    public AppConfig config() {
        return config;
    }

    /**
     * The AI client chosen by {@code LLM_PROVIDER}, built on first use and then shared. A missing
     * Gemini key is already reported when the configuration is loaded.
     */
    public synchronized LlmClient llm() {
        if (llm == null) {
            llm = LlmClients.create(config);
        }
        return llm;
    }
}
