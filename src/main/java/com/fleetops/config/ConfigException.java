package com.fleetops.config;

/** Thrown when the local configuration is missing, malformed or inconsistent. */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
