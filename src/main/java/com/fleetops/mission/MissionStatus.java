package com.fleetops.mission;

/** How a {@link Mission} stands. Only {@link #RUNNING} is not final. */
public enum MissionStatus {

    /** Units are deployed: searching, rescuing or coming home. */
    RUNNING,

    /** Everyone in the area was rescued and brought to base. */
    COMPLETED,

    /** The mission ended on its own, but some people were not found or could not be reached. */
    INCOMPLETE,

    /** The operator stopped the mission. */
    ABORTED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
