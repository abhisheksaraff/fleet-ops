package com.fleetops.plan;

/** No workable response plan could be made. */
public final class PlanningException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PlanningException(String message) {
        super(message);
    }

    public PlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
