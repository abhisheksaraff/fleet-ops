package com.fleetops.mission;

/**
 * Told by {@link MissionControl} when a mission ends, whatever the outcome. The GUI uses it to show
 * the result and the CLI to print the report. Called on the thread that runs the simulation.
 */
@FunctionalInterface
public interface MissionListener {

    void onMissionFinished(Mission mission, MissionReport report);
}
