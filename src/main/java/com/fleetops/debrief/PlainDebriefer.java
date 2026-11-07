package com.fleetops.debrief;

import com.fleetops.mission.MissionReport;

/** Writes the debrief from fixed rules alone: always available, never wrong about the numbers. */
public final class PlainDebriefer extends AbstractDebriefer {

    public static final String AUTHOR = "rules";

    @Override
    Debrief write(MissionReport report, DebriefFacts facts) {
        return DebriefRules.write(facts, AUTHOR, false);
    }
}
