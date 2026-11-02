package com.fleetops.news;

/** A news source could not be read (network failure, unexpected format, ...). */
public final class NewsSourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NewsSourceException(String message) {
        super(message);
    }

    public NewsSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
