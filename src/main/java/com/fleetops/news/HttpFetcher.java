package com.fleetops.news;

import java.io.IOException;
import java.net.URI;

/**
 * The one thing the web-based adapters need from the network: download a page as text. Tests give
 * the adapters canned pages instead of a real connection.
 */
@FunctionalInterface
public interface HttpFetcher {

    /** @throws IOException if the page cannot be downloaded or the server answers with an error */
    String get(URI uri) throws IOException;

    /** Sends a form or text body and returns the answer; sources that never post need not support it. */
    default String post(URI uri, String contentType, String body) throws IOException {
        throw new IOException("This fetcher cannot send POST requests");
    }
}
