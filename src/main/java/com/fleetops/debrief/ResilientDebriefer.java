package com.fleetops.debrief;

import com.fleetops.ai.LlmException;
import com.fleetops.mission.MissionReport;
import java.util.Objects;

/**
 * Tries the preferred author and, if it cannot produce a debrief, falls back to a simpler one so a
 * finished mission always gets a debrief. The author line says when that happened.
 */
public final class ResilientDebriefer implements MissionDebriefer {

    private static final System.Logger LOG = System.getLogger(ResilientDebriefer.class.getName());

    private final MissionDebriefer primary;
    private final MissionDebriefer fallback;

    public ResilientDebriefer(MissionDebriefer primary, MissionDebriefer fallback) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public Debrief debrief(MissionReport report) {
        try {
            return primary.debrief(report);
        } catch (LlmException | DebriefException e) {
            LOG.log(System.Logger.Level.WARNING, "The AI debrief failed, using the fixed rules: {0}", e.getMessage());
            Debrief plain = fallback.debrief(report);
            return plain.withAuthor(plain.writtenBy() + " (the AI was unavailable)", false);
        }
    }
}
