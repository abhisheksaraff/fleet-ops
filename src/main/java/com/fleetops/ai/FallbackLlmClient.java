package com.fleetops.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chain of Responsibility over several AI models: a request goes to the first model, and if that model has
 * used up its allowance for the day, the next one gets it, and so on. A model that ran out is skipped for
 * the rest of the run. Any other failure (a bad key, a blocked prompt, an unreadable answer) is passed on
 * unchanged, because another model would not fix it.
 *
 * <p>{@link #name()} is the model that answered last, so plans and debriefs say honestly which model wrote them.
 */
public final class FallbackLlmClient implements LlmClient {

    private final List<LlmClient> chain;
    private final Set<Integer> exhausted = new HashSet<>();
    private LlmClient lastUsed;

    public FallbackLlmClient(List<LlmClient> chain) {
        Objects.requireNonNull(chain, "chain");
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("A fallback chain needs at least one model");
        }
        this.chain = List.copyOf(chain);
        this.lastUsed = this.chain.get(0);
    }

    @Override
    public synchronized String name() {
        return lastUsed.name();
    }

    @Override
    public String complete(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        LlmException lastQuotaFailure = null;
        for (int i = 0; i < chain.size(); i++) {
            if (isExhausted(i)) {
                continue;
            }
            LlmClient model = chain.get(i);
            try {
                String answer = model.complete(request);
                markUsed(model);
                return answer;
            } catch (LlmException e) {
                if (!e.isQuotaExhausted()) {
                    markUsed(model);
                    throw e;
                }
                markExhausted(i);
                lastQuotaFailure = e;
                System.getLogger(FallbackLlmClient.class.getName()).log(System.Logger.Level.WARNING,
                        "{0} has used up its allowance; trying the next model", model.name());
            }
        }
        if (lastQuotaFailure != null) {
            throw new LlmException("Every model in the chain has used up its allowance: " + lastQuotaFailure.getMessage(), false, true);
        }
        throw new LlmException("Every model in the chain has used up its allowance", false, true);
    }

    private synchronized boolean isExhausted(int index) {
        return exhausted.contains(index);
    }

    private synchronized void markExhausted(int index) {
        exhausted.add(index);
    }

    private synchronized void markUsed(LlmClient model) {
        lastUsed = model;
    }

    /** The models in order of preference. */
    public List<LlmClient> chain() {
        return new ArrayList<>(chain);
    }

    @Override
    public String toString() {
        return "FallbackLlmClient" + chain;
    }
}
