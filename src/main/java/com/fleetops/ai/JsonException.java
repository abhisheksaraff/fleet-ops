package com.fleetops.ai;

/** Text that should have been JSON was not, or a value cannot be written as JSON. */
public final class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonException(String message) {
        super(message);
    }
}
