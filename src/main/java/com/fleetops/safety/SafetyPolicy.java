package com.fleetops.safety;

import com.fleetops.command.MissionCommand;
import com.fleetops.domain.geo.GeoPoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A set of {@link SafetyRule}s applied together. The policy never stops at the first problem: the
 * report lists every violation so the operator sees everything that is wrong at once.
 */
public final class SafetyPolicy {

    public static final double DEFAULT_RESERVE_PERCENT = 15.0;
    public static final double DEFAULT_SERVICE_RADIUS_KM = 100.0;

    private final List<SafetyRule> rules;

    public SafetyPolicy(List<? extends SafetyRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** The default rules for a mission operating out of {@code base}. */
    public static SafetyPolicy standard(GeoPoint base) {
        return new SafetyPolicy(List.of(
                new UnitReadyRule(),
                new BatteryReserveRule(base, DEFAULT_RESERVE_PERCENT),
                new ServiceAreaRule(base, DEFAULT_SERVICE_RADIUS_KM)));
    }

    /** A policy with no rules; every command passes. */
    public static SafetyPolicy permissive() {
        return new SafetyPolicy(List.of());
    }

    /** A copy of this policy with one more rule. */
    public SafetyPolicy with(SafetyRule rule) {
        List<SafetyRule> more = new ArrayList<>(rules);
        more.add(Objects.requireNonNull(rule, "rule"));
        return new SafetyPolicy(more);
    }

    public List<SafetyRule> rules() {
        return rules;
    }

    /** Checks a command as if its unit were at {@code start}. */
    public SafetyReport evaluate(MissionCommand command, GeoPoint start) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(start, "start");
        List<SafetyViolation> found = new ArrayList<>();
        for (SafetyRule rule : rules) {
            rule.check(command, start).ifPresent(message -> found.add(new SafetyViolation(rule.name(), message)));
        }
        return new SafetyReport(command.describe(), found);
    }

    /** Wraps a command so that it is checked just before it starts. */
    public VerifiedCommand verify(MissionCommand command) {
        return new VerifiedCommand(command, this);
    }

    /** Wraps every command in the collection. */
    public List<VerifiedCommand> verifyAll(Collection<? extends MissionCommand> commands) {
        return commands.stream().map(this::verify).toList();
    }
}
