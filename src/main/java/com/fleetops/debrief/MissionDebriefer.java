package com.fleetops.debrief;

import com.fleetops.mission.MissionReport;

/**
 * Turns the report of a finished mission into a written {@link Debrief}. The window, the console and
 * the command line only know this interface; whether an AI model or fixed rules do the writing is
 * decided once, in {@code AppContext}.
 */
public interface MissionDebriefer {

    /**
     * @throws com.fleetops.ai.LlmException if an AI-backed debriefer cannot reach its model
     * @throws DebriefException if the author produced nothing usable
     */
    Debrief debrief(MissionReport report);
}
