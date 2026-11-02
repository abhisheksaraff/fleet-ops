package com.fleetops.safety;

import java.util.Objects;

/**
 * A problem found by a {@link SafetyRule}.
 *
 * @param rule the name of the rule that found it
 * @param message what is wrong, in plain words
 */
public record SafetyViolation(String rule, String message) {

    public SafetyViolation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public String toString() {
        return rule + ": " + message;
    }
}
