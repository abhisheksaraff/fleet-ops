package com.fleetops.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * An AI stand-in that needs no key and no network. It answers with whatever the supplied function
 * returns and remembers every request, so the whole program runs offline and tests can check
 * exactly what would have been sent to a real model.
 */
public final class MockLlmClient implements LlmClient {

    private final Function<LlmRequest, String> responder;
    private final List<LlmRequest> requests = new ArrayList<>();

    public MockLlmClient(Function<LlmRequest, String> responder) {
        this.responder = Objects.requireNonNull(responder, "responder");
    }

    /** A mock that returns the given answers one after another, then fails (so a test notices extra calls). */
    public static MockLlmClient scripted(String... answers) {
        Deque<String> remaining = new ArrayDeque<>(List.of(answers));
        return new MockLlmClient(request -> {
            synchronized (remaining) {
                String next = remaining.pollFirst();
                if (next == null) {
                    throw new LlmException("The mock has no more scripted answers", false);
                }
                return next;
            }
        });
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String complete(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (requests) {
            requests.add(request);
        }
        String answer = responder.apply(request);
        if (answer == null) {
            throw new LlmException("The mock responder returned no answer", false);
        }
        return answer;
    }

    /** Every request received so far, oldest first. */
    public List<LlmRequest> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }
}
