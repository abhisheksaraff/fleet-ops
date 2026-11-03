package com.fleetops.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Sends requests with the HTTP client that comes with Java. */
public final class JdkHttpPoster implements HttpPoster {

    private static final int MAX_CHARS = 10_000_000;

    private final HttpClient client;
    private final Duration requestTimeout;

    /** Waits up to two minutes for an answer: language models can be slow, especially local ones. */
    public JdkHttpPoster() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(120));
    }

    public JdkHttpPoster(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Reply postJson(URI uri, Map<String, String> headers, String jsonBody) throws IOException {
        Objects.requireNonNull(uri, "uri");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        headers.forEach(request::header);
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().length() > MAX_CHARS) {
                throw new IOException("The answer from " + uri.getHost() + " is too large");
            }
            return new Reply(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + uri.getHost(), e);
        }
    }
}
