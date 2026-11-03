package com.fleetops.intel;

/** The AI's answer was valid JSON but does not describe an incident the way we asked. */
public final class InvalidAssessmentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidAssessmentException(String message) {
        super(message);
    }
}
