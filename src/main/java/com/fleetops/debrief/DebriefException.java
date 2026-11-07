package com.fleetops.debrief;

/** A debrief could not be written: the model answered, but not in a usable form. */
public final class DebriefException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DebriefException(String message) {
        super(message);
    }

    public DebriefException(String message, Throwable cause) {
        super(message, cause);
    }
}
