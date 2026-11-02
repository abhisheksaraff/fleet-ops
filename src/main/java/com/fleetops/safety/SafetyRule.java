package com.fleetops.safety;

import com.fleetops.command.MissionCommand;
import com.fleetops.domain.geo.GeoPoint;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * One safety check on a command that has not started yet. A rule looks at what the command would
 * do from a given starting point and either accepts it (empty result) or explains what is wrong.
 * New rules are added to a {@link SafetyPolicy} without touching the commands.
 */
public interface SafetyRule {

    /** Short name shown next to a violation, for example {@code BatteryReserve}. */
    String name();

    /**
     * @param command the command about to run
     * @param start where its unit will be when it starts
     * @return the problem, or empty if the command is acceptable
     */
    Optional<String> check(MissionCommand command, GeoPoint start);

    /** Builds a rule from a function, handy for one-off rules and for tests. */
    static SafetyRule of(String name, BiFunction<MissionCommand, GeoPoint, Optional<String>> check) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(check, "check");
        return new SafetyRule() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<String> check(MissionCommand command, GeoPoint start) {
                return check.apply(command, start);
            }
        };
    }
}
