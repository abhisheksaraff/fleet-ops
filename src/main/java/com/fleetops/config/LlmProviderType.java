package com.fleetops.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The AI backends the application can be configured to use. Only a factory at the edge of the
 * system (added in a later commit) turns one of these into a real client, so the core never
 * depends on a specific vendor.
 */
public enum LlmProviderType {
    GEMINI,
    OLLAMA,
    MOCK;

    /** Parses a provider name case-insensitively. */
    public static LlmProviderType parse(String raw) {
        String wanted = raw == null ? "" : raw.strip();
        for (LlmProviderType type : values()) {
            if (type.name().equalsIgnoreCase(wanted)) {
                return type;
            }
        }
        String valid = Arrays.stream(values())
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        throw new ConfigException("Unknown LLM_PROVIDER '" + wanted + "'. Valid values: " + valid + ".");
    }
}
