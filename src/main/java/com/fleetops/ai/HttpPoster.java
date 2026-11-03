package com.fleetops.ai;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Sends a JSON request over HTTP and returns whatever the server answers. Unlike a plain download,
 * an error status is not an exception here: the AI clients read the error message the service sends
 * back. Tests give the clients a fake instead of a real connection.
 */
@FunctionalInterface
public interface HttpPoster {

    /** The server's answer. */
    record Reply(int status, String body) {

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    /** @throws IOException if the server cannot be reached or does not answer in time */
    Reply postJson(URI uri, Map<String, String> headers, String jsonBody) throws IOException;
}
