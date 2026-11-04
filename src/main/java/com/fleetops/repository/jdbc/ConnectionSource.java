package com.fleetops.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Where the repository gets its database connection. Keeping this as an interface means the
 * repository never mentions a database vendor: the URL and driver are chosen at the edge
 * ({@code AppContext}), and tests can supply their own source.
 */
@FunctionalInterface
public interface ConnectionSource {

    /** Opens a new connection; the caller closes it. */
    Connection open() throws SQLException;
}
