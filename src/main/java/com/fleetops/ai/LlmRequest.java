package com.fleetops.ai;

import java.util.Objects;

/**
 * One question for an AI model.
 *
 * @param system standing instructions (role, rules, output format); may be empty
 * @param user the actual question or the text to work on
 * @param jsonOutput true if the model must answer with a JSON document
 * @param temperature 0 = most predictable, higher = more varied (0 to 2)
 */
public record LlmRequest(String system, String user, boolean jsonOutput, double temperature) {

    public static final double DEFAULT_TEMPERATURE = 0.2;

    public LlmRequest {
        system = system == null ? "" : system.strip();
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("The user prompt must not be blank");
        }
        if (Double.isNaN(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Temperature must be between 0 and 2: " + temperature);
        }
    }

    /** A request whose answer must be a JSON document. */
    public static LlmRequest json(String system, String user) {
        return new LlmRequest(system, user, true, DEFAULT_TEMPERATURE);
    }

    /** A request with a free-text answer. */
    public static LlmRequest text(String system, String user) {
        return new LlmRequest(system, user, false, DEFAULT_TEMPERATURE);
    }

    public LlmRequest withTemperature(double newTemperature) {
        return new LlmRequest(system, user, jsonOutput, newTemperature);
    }

    @Override
    public String toString() {
        return "LlmRequest[json=" + jsonOutput + ", temperature=" + temperature + ", systemChars=" + system.length()
                + ", userChars=" + Objects.toString(user).length() + "]";
    }
}
