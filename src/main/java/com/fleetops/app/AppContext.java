package com.fleetops.app;

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
}
