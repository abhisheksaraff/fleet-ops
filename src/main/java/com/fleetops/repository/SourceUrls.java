package com.fleetops.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Turns the many spellings of one article link into a single key, so the same story is not stored
 * twice. Case in the host, a trailing slash, the {@code #fragment} and tracking parameters
 * ({@code utm_*}, {@code fbclid}, ...) are ignored.
 */
public final class SourceUrls {

    private static final Set<String> TRACKING_PARAMS = Set.of("fbclid", "gclid", "oc", "ved", "ref", "ref_src");

    private SourceUrls() {}

    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }
        String trimmed = url.strip();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        if (uri.getHost() == null) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);

        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.equals("/")) {
            path = "";
        }

        StringBuilder key = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort) {
            key.append(':').append(port);
        }
        key.append(path);

        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            StringJoiner kept = new StringJoiner("&");
            for (String param : query.split("&")) {
                String name = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
                String lower = name.toLowerCase(Locale.ROOT);
                if (!param.isEmpty() && !lower.startsWith("utm_") && !TRACKING_PARAMS.contains(lower)) {
                    kept.add(param);
                }
            }
            if (kept.length() > 0) {
                key.append('?').append(kept);
            }
        }
        return key.toString();
    }
}
