package com.fleetops.intel;

/** The AI could not give a usable assessment of an item, even after being asked to correct itself. */
public final class AnalysisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
