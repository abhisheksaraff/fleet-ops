package com.fleetops.ai;

/**
 * A call to an AI model failed. {@link #isRetryable()} says whether trying again might help
 * (network trouble, a busy server) or not (a bad key, a blocked prompt, a malformed answer).
 */
public final class LlmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean retryable;
    private final boolean quotaExhausted;

    public LlmException(String message, boolean retryable) {
        this(message, retryable, false);
    }

    /** @param quotaExhausted true if this model's allowance (for the day) is used up, so another model may answer */
    public LlmException(String message, boolean retryable, boolean quotaExhausted) {
        super(message);
        this.retryable = retryable;
        this.quotaExhausted = quotaExhausted;
    }

    public LlmException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.quotaExhausted = false;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * True if the model's allowance for the day is used up. Waiting a few seconds will not help, but a
     * different model has its own allowance (see {@link FallbackLlmClient}).
     */
    public boolean isQuotaExhausted() {
        return quotaExhausted;
    }
}
