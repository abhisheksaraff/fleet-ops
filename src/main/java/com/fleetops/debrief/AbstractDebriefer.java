package com.fleetops.debrief;

import com.fleetops.mission.MissionReport;
import java.util.Objects;

/**
 * Template Method for writing a debrief. {@link #debrief} fixes the steps: gather the hard numbers
 * from the report, let the subclass write the text, and check that what came back really is a
 * debrief. Subclasses supply only {@link #write}, so an AI author and the fixed rules cannot disagree
 * about the numbers they start from or about what counts as a valid result.
 */
public abstract class AbstractDebriefer implements MissionDebriefer {

    @Override
    public final Debrief debrief(MissionReport report) {
        Objects.requireNonNull(report, "report");
        DebriefFacts facts = DebriefFacts.of(report);
        Debrief written = write(report, facts);
        if (written == null) {
            throw new DebriefException("The debrief author returned nothing");
        }
        return written;
    }

    /** Writes the debrief. Called once per report, with the numbers already gathered. */
    abstract Debrief write(MissionReport report, DebriefFacts facts);
}
