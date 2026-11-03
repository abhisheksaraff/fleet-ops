package com.fleetops.ai;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Adapter for Google's Gemini {@code generateContent} web service. The API key is sent in a
 * request header (never in the address) and is never included in error messages or
 * {@link #toString()}.
 */
public final class GeminiClient implements LlmClient {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    /** Used when LLM_MODEL is empty. Set LLM_MODEL if Google has retired this name. */
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private final HttpPoster poster;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiClient(HttpPoster poster, String apiKey, String model) {
        this(poster, apiKey, model, DEFAULT_BASE_URL);
    }

    public GeminiClient(HttpPoster poster, String apiKey, String model, String baseUrl) {
        this.poster = Objects.requireNonNull(poster, "poster");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("A Gemini API key is required");
        }
        this.apiKey = apiKey.strip();
        String name = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip().replaceFirst("^models/", "");
        if (!MODEL_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a valid Gemini model name: '" + model + "'");
        }
        this.model = name;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/+$", "");
    }

    @Override
    public String name() {
        return "gemini:" + model;
    }

    @Override
    public String complete(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        URI uri = URI.create(baseUrl + "/models/" + model + ":generateContent");
        HttpPoster.Reply reply;
        try {
            reply = poster.postJson(uri, Map.of("x-goog-api-key", apiKey), Json.write(body(request)));
        } catch (IOException e) {
            throw new LlmException("Could not reach Gemini: " + e.getMessage(), true, e);
        }
        if (!reply.isSuccess()) {
            throw httpError(reply);
        }
        return readAnswer(reply.body());
    }

    private Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.user())))));
        if (!request.system().isEmpty()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.system()))));
        }
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("temperature", request.temperature());
        if (request.jsonOutput()) {
            generation.put("responseMimeType", "application/json");
        }
        body.put("generationConfig", generation);
        return body;
    }

    private LlmException httpError(HttpPoster.Reply reply) {
        String detail = Json.path(tryParse(reply.body()), "error", "message").map(Object::toString).orElseGet(() -> snippet(reply.body()));
        boolean retryable = reply.status() == 408 || reply.status() == 429 || reply.status() >= 500;
        String hint = reply.status() == 400 || reply.status() == 401 || reply.status() == 403
                ? " (check LLM_API_KEY and LLM_MODEL)" : "";
        return new LlmException("Gemini answered HTTP " + reply.status() + ": " + detail + hint, retryable);
    }

    private String readAnswer(String responseBody) {
        Object root;
        try {
            root = Json.parse(responseBody);
        } catch (JsonException e) {
            throw new LlmException("Gemini sent an unreadable answer: " + e.getMessage(), true, e);
        }
        StringBuilder text = new StringBuilder();
        Object parts = Json.path(root, "candidates", 0, "content", "parts").orElse(null);
        if (parts instanceof List<?> list) {
            for (Object part : new ArrayList<>(list)) {
                boolean thought = part instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("thought"));
                if (!thought) {
                    Json.path(part, "text").ifPresent(t -> text.append(t));
                }
            }
        }
        if (text.length() > 0) {
            return text.toString();
        }
        String blocked = Json.path(root, "promptFeedback", "blockReason").map(Object::toString).orElse(null);
        if (blocked != null) {
            throw new LlmException("Gemini refused the prompt (" + blocked + ")", false);
        }
        String finish = Json.path(root, "candidates", 0, "finishReason").map(Object::toString).orElse("no candidates");
        throw new LlmException("Gemini returned no text (" + finish + ")", false);
    }

    private static Object tryParse(String body) {
        try {
            return Json.parse(body);
        } catch (JsonException e) {
            return null;
        }
    }

    private static String snippet(String body) {
        String flat = body == null ? "" : body.replaceAll("\\s+", " ").strip();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "...";
    }

    @Override
    public String toString() {
        return "GeminiClient[" + model + "]";
    }
}
