package com.fleetops.safety;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The result of running a {@link SafetyPolicy} over one command.
 *
 * @param command the description of the command that was checked
 * @param violations everything that was wrong; empty when the command is safe
 */
public record SafetyReport(String command, List<SafetyViolation> violations) {

    public SafetyReport {
        violations = List.copyOf(violations);
    }

    public boolean isSafe() {
        return violations.isEmpty();
    }

    /** All violations on one line, or "safe". */
    public String summary() {
        return isSafe() ? "safe" : violations.stream().map(SafetyViolation::toString).collect(Collectors.joining("; "));
    }
}
