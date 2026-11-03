package com.fleetops.ai;

/**
 * A call to an AI model failed. {@link #isRetryable()} says whether trying again might help
 * (network trouble, a busy server) or not (a bad key, a blocked prompt, a malformed answer).
 */
public final class LlmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public LlmException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public LlmException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
