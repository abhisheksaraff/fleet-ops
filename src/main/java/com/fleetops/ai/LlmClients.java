package com.fleetops.ai;

import com.fleetops.config.AppConfig;
import com.fleetops.config.ConfigException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Factory that builds the AI client chosen in the configuration ({@code LLM_PROVIDER}). This is the
 * only place that knows which classes belong to which vendor. Real services are wrapped in a
 * {@link RetryingLlmClient}; the mock is not, since it never fails by chance.
 */
public final class LlmClients {

    private LlmClients() {}

    /**
     * @param config the application settings
     * @param poster how to reach the network (only used for Gemini and Ollama)
     * @param mockResponder how the mock answers; used only when the provider is {@code mock}
     * @throws ConfigException if the chosen provider is missing something it needs, such as Gemini's key
     *     (the configuration normally rejects that already when it is loaded)
     */
    public static LlmClient create(AppConfig config, HttpPoster poster, Function<LlmRequest, String> mockResponder) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(poster, "poster");
        Objects.requireNonNull(mockResponder, "mockResponder");
        String model = config.llmModel().orElse(null);
        return switch (config.llmProvider()) {
            case MOCK -> new MockLlmClient(mockResponder);
            case GEMINI -> gemini(config, poster, model);
            case OLLAMA -> new RetryingLlmClient(new OllamaClient(poster, config.llmBaseUrl(), model));
        };
    }

    /** Gemini: the main model, then each model in {@code LLM_FALLBACK_MODELS} when one has used up its day's allowance. */
    private static LlmClient gemini(AppConfig config, HttpPoster poster, String model) {
        String key = config.llmApiKey().orElseThrow(() -> new ConfigException(
                "LLM_PROVIDER=gemini needs LLM_API_KEY. Put your key in .env (see .env.example), "
                        + "or use LLM_PROVIDER=mock or ollama."));
        List<LlmClient> chain = new ArrayList<>();
        chain.add(new RetryingLlmClient(new GeminiClient(poster, key, model)));
        Set<String> seen = new HashSet<>();
        seen.add(new GeminiClient(poster, key, model).name());
        for (String fallback : config.llmFallbackModels()) {
            GeminiClient next = new GeminiClient(poster, key, fallback);
            if (seen.add(next.name())) {
                chain.add(new RetryingLlmClient(next));
            }
        }
        return chain.size() == 1 ? chain.get(0) : new FallbackLlmClient(chain);
    }

    /** Like {@link #create(AppConfig, HttpPoster, Function)} with the real network and a mock that answers {@code {}}. */
    public static LlmClient create(AppConfig config) {
        return create(config, new JdkHttpPoster(), request -> "{}");
    }
}
