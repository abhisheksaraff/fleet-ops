package com.fleetops.repository.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Opens connections through {@link DriverManager}, which finds the driver on the classpath from the
 * JDBC URL. Nothing here is specific to H2: a PostgreSQL or SQLite URL works the same way once its
 * driver jar is on the classpath.
 */
public final class DriverManagerConnectionSource implements ConnectionSource {

    private final String url;
    private final String user;
    private final String password;

    public DriverManagerConnectionSource(String url, String user, String password) {
        this.url = Objects.requireNonNull(url, "url");
        this.user = Objects.requireNonNull(user, "user");
        this.password = Objects.requireNonNull(password, "password");
    }

    @Override
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** The URL without credentials, safe to show in messages. */
    public String url() {
        return url;
    }
}
