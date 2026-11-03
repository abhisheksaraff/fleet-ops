package com.fleetops.ai;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Adapter for a local Ollama server ({@code /api/chat}). Free, private, and needs no key. */
public final class OllamaClient implements LlmClient {

    /** Used when LLM_MODEL is empty. Fetch it first with {@code ollama pull llama3.1}. */
    public static final String DEFAULT_MODEL = "llama3.1";

    private final HttpPoster poster;
    private final String baseUrl;
    private final String model;

    public OllamaClient(HttpPoster poster, String baseUrl, String model) {
        this.poster = Objects.requireNonNull(poster, "poster");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").strip().replaceAll("/+$", "");
        if (this.baseUrl.isEmpty()) {
            throw new IllegalArgumentException("The Ollama address must not be blank");
        }
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
    }

    @Override
    public String name() {
        return "ollama:" + model;
    }

    @Override
    public String complete(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        HttpPoster.Reply reply;
        try {
            reply = poster.postJson(URI.create(baseUrl + "/api/chat"), Map.of(), Json.write(body(request)));
        } catch (IOException e) {
            throw new LlmException("Could not reach Ollama at " + baseUrl + " (is it running? start it with 'ollama serve'): "
                    + e.getMessage(), true, e);
        }
        if (!reply.isSuccess()) {
            throw httpError(reply);
        }
        Object root;
        try {
            root = Json.parse(reply.body());
        } catch (JsonException e) {
            throw new LlmException("Ollama sent an unreadable answer: " + e.getMessage(), true, e);
        }
        return Json.path(root, "message", "content").map(Object::toString).filter(s -> !s.isBlank())
                .orElseThrow(() -> new LlmException("Ollama returned no text", false));
    }

    private Map<String, Object> body(LlmRequest request) {
        List<Object> messages = new ArrayList<>();
        if (!request.system().isEmpty()) {
            messages.add(Map.of("role", "system", "content", request.system()));
        }
        messages.add(Map.of("role", "user", "content", request.user()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        if (request.jsonOutput()) {
            body.put("format", "json");
        }
        body.put("options", Map.of("temperature", request.temperature()));
        return body;
    }

    private LlmException httpError(HttpPoster.Reply reply) {
        String detail;
        try {
            detail = Json.path(Json.parse(reply.body()), "error").map(Object::toString).orElse(reply.body());
        } catch (JsonException e) {
            detail = reply.body() == null ? "" : reply.body().strip();
        }
        if (detail.length() > 200) {
            detail = detail.substring(0, 200) + "...";
        }
        String hint = reply.status() == 404 ? " (fetch the model first: ollama pull " + model + ")" : "";
        boolean retryable = reply.status() == 408 || reply.status() == 429 || reply.status() >= 500;
        return new LlmException("Ollama answered HTTP " + reply.status() + ": " + detail + hint, retryable);
    }

    @Override
    public String toString() {
        return "OllamaClient[" + model + " @ " + baseUrl + "]";
    }
}
