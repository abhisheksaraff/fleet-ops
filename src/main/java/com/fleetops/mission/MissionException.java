package com.fleetops.mission;

/**
 * A mission cannot be planned, launched or stopped as asked: the incident is in the wrong state,
 * the plan is out of date, or no helicopter is free. The message is written for the operator.
 */
public class MissionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MissionException(String message) {
        super(message);
    }

    public MissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
