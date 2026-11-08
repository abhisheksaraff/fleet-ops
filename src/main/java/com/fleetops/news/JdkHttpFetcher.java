package com.fleetops.news;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Downloads pages with the HTTP client that comes with Java. */
public final class JdkHttpFetcher implements HttpFetcher {

    private static final int MAX_CHARS = 5_000_000;
    /** A browser-like label: some news sites refuse requests from an unknown program. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final HttpClient client;
    private final Duration requestTimeout;

    public JdkHttpFetcher() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(20));
    }

    public JdkHttpFetcher(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String get(URI uri) throws IOException {
        return send(builder(uri).GET().build(), uri);
    }

    @Override
    public String post(URI uri, String contentType, String body) throws IOException {
        return send(builder(uri).header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body)).build(), uri);
    }

    private HttpRequest.Builder builder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xml,application/rss+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9");
    }

    private String send(HttpRequest request, URI uri) throws IOException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " from " + uri);
            }
            String body = response.body();
            if (body.length() > MAX_CHARS) {
                throw new IOException("Response from " + uri + " is too large (" + body.length() + " characters)");
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + uri, e);
        }
    }
}
