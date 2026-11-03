package com.fleetops.ai;

import java.util.Objects;

/**
 * Decorator that repeats a call when it fails for a reason that may pass (network trouble, a busy
 * server), waiting a little longer each time. Failures that will not go away, such as a wrong key,
 * are passed on at once.
 */
public final class RetryingLlmClient implements LlmClient {

    /** Pauses the current thread; replaceable so tests do not really wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public static final int DEFAULT_ATTEMPTS = 3;
    public static final long DEFAULT_BASE_DELAY_MILLIS = 500;
    private static final long MAX_DELAY_MILLIS = 8_000;

    private final LlmClient delegate;
    private final int maxAttempts;
    private final long baseDelayMillis;
    private final Sleeper sleeper;

    public RetryingLlmClient(LlmClient delegate) {
        this(delegate, DEFAULT_ATTEMPTS, DEFAULT_BASE_DELAY_MILLIS, Thread::sleep);
    }

    public RetryingLlmClient(LlmClient delegate, int maxAttempts, long baseDelayMillis, Sleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
        }
        if (baseDelayMillis < 0) {
            throw new IllegalArgumentException("baseDelayMillis must not be negative: " + baseDelayMillis);
        }
        this.maxAttempts = maxAttempts;
        this.baseDelayMillis = baseDelayMillis;
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String complete(LlmRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.complete(request);
            } catch (LlmException e) {
                if (!e.isRetryable() || attempt == maxAttempts) {
                    throw e;
                }
                pause(attempt, e);
            }
        }
    }

    private void pause(int attempt, LlmException cause) {
        long delay = Math.min(MAX_DELAY_MILLIS, baseDelayMillis << (attempt - 1));
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting to retry: " + cause.getMessage(), false, e);
        }
    }

    @Override
    public String toString() {
        return "RetryingLlmClient[" + delegate + ", attempts=" + maxAttempts + "]";
    }
}
