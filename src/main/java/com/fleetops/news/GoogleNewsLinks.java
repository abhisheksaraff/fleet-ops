package com.fleetops.news;

import com.fleetops.ai.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google News feed links point to a Google page that redirects (with JavaScript) to the publisher,
 * so they cannot be downloaded as an article. This finds the publisher's address behind one:
 * older links carry it inside the encoded id; newer ones need one question to Google's own web
 * service. Nothing here is guaranteed to keep working, so every failure is reported as "not found".
 */
public final class GoogleNewsLinks {

    private static final Pattern ARTICLE_PATH = Pattern.compile("^https?://news\\.google\\.com/(?:rss/)?articles/([^/?#]+)");
    private static final Pattern SIGNATURE = Pattern.compile("data-n-a-sg=\"([^\"]+)\"");
    private static final Pattern TIMESTAMP = Pattern.compile("data-n-a-ts=\"([^\"]+)\"");
    private static final URI BATCH = URI.create("https://news.google.com/_/DotsSplashUi/data/batchexecute");

    private GoogleNewsLinks() {}

    /** True for a link to a Google News article page. */
    public static boolean isGoogleArticle(String url) {
        return articleId(url).isPresent();
    }

    static Optional<String> articleId(String url) {
        Matcher m = ARTICLE_PATH.matcher(url == null ? "" : url.strip());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** The publisher's address, read straight from an older style id; empty if the id does not carry one. */
    static Optional<String> decodeLocally(String id) {
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        int start = text.indexOf("http");
        if (start < 0) {
            return Optional.empty();
        }
        int end = start;
        while (end < text.length() && text.charAt(end) > 0x20 && text.charAt(end) < 0x7f) {
            end++;
        }
        String candidate = text.substring(start, end);
        return candidate.matches("https?://[^/\\s]+\\.[^/\\s]+.*") ? Optional.of(candidate) : Optional.empty();
    }

    /** Finds the publisher's address behind a Google News link, or empty if it could not be worked out. */
    public static Optional<URI> resolve(HttpFetcher fetcher, String url) {
        Optional<String> id = articleId(url);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> local = decodeLocally(id.get());
        if (local.isPresent()) {
            return toUri(local.get());
        }
        try {
            String page = fetcher.get(URI.create("https://news.google.com/articles/" + id.get()));
            Matcher signature = SIGNATURE.matcher(page);
            Matcher timestamp = TIMESTAMP.matcher(page);
            if (!signature.find() || !timestamp.find()) {
                return Optional.empty();
            }
            String inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],"
                    + "\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"" + id.get() + "\"," + timestamp.group(1)
                    + ",\"" + signature.group(1) + "\"]";
            String request = Json.write(List.of(List.of(List.of("Fbv4je", inner))));
            String reply = fetcher.post(BATCH, "application/x-www-form-urlencoded;charset=UTF-8",
                    "f.req=" + URLEncoder.encode(request, StandardCharsets.UTF_8));
            return readAnswer(reply);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Reads the address out of the web service's answer: a JSON list whose third item is more JSON text. */
    static Optional<URI> readAnswer(String reply) {
        try {
            // The answer starts with a guard line, then length numbers and JSON; read the first JSON list.
            Object outer = Json.parseFromText(reply.strip().replaceFirst("^\\)\\]\\}'", ""));
            Optional<Object> inner = Json.path(outer, 0, 2);
            if (inner.isEmpty() || !(inner.get() instanceof String json)) {
                return Optional.empty();
            }
            Optional<Object> address = Json.path(Json.parse(json), 1);
            return address.filter(String.class::isInstance).map(String.class::cast).flatMap(GoogleNewsLinks::toUri);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<URI> toUri(String text) {
        try {
            URI uri = URI.create(text);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equals(scheme) || "https".equals(scheme)) ? Optional.of(uri) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
