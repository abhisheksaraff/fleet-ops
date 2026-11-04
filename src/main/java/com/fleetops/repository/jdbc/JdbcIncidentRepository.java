package com.fleetops.repository.jdbc;

import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.domain.incident.IncidentType;
import com.fleetops.domain.incident.Severity;
import com.fleetops.repository.DuplicateIncidentException;
import com.fleetops.repository.IncidentRepository;
import com.fleetops.repository.RepositoryException;
import com.fleetops.repository.SourceUrls;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores incidents in a relational database using plain JDBC, so it works with H2 (the default,
 * embedded and file based), or any other database whose driver is on the classpath. It behaves
 * exactly like {@link com.fleetops.repository.InMemoryIncidentRepository}, so the two can be
 * swapped without touching any other class.
 *
 * <ul>
 *   <li><b>No duplicates:</b> the normalized source URL is a unique column, so the database itself
 *       refuses a second incident from the same article, even if two threads try at once.
 *   <li><b>Same object back:</b> an incident is loaded from its row only once; after that the
 *       same {@link Incident} object is returned (an identity map). The simulation relies on this,
 *       because it recognizes an incident by object identity.
 *   <li><b>What is saved:</b> everything is written when the incident is saved. Afterwards only
 *       the lifecycle state can change, so {@link #update} writes just the state.
 * </ul>
 *
 * <p>One connection is opened on first use and reused; it is reopened if it breaks. All methods are
 * synchronized. Call {@link #close()} when finished.
 */
public final class JdbcIncidentRepository implements IncidentRepository, AutoCloseable {

    private static final System.Logger LOG = System.getLogger(JdbcIncidentRepository.class.getName());

    /** Longest text stored; longer titles and summaries are cut with an ellipsis. */
    static final int MAX_TITLE = 500;

    static final int MAX_SUMMARY = 4000;

    /** Longest source URL accepted (a longer one cannot be stored without breaking duplicate detection). */
    static final int MAX_URL = 2000;

    /** SQLSTATE for a unique-constraint violation, the same in H2, PostgreSQL and most others. */
    private static final String UNIQUE_VIOLATION = "23505";

    private static final String COLUMNS = "id, incident_type, title, summary, center_lat, center_lon, radius_km, "
            + "severity, estimated_people, source_url, reported_epoch_second, reported_nano, incident_state";

    private final ConnectionSource source;
    private final Map<Long, Incident> loaded = new HashMap<>();
    private Connection connection;

    /**
     * Opens the database and creates the table if it does not exist yet.
     *
     * @throws RepositoryException if the database cannot be reached or set up
     */
    public JdbcIncidentRepository(ConnectionSource source) {
        this.source = Objects.requireNonNull(source, "source");
        createSchema();
    }

    // ---- IncidentRepository --------------------------------------------------------------------

    @Override
    public synchronized Incident save(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        if (incident.id().isPresent()) {
            throw new IllegalArgumentException("Incident was already stored with id " + incident.id().get());
        }
        String url = incident.sourceUrl().filter(u -> !u.isBlank()).orElse(null);
        if (url != null && url.length() > MAX_URL) {
            throw new IllegalArgumentException("The source URL is longer than " + MAX_URL + " characters");
        }
        String key = url == null ? null : SourceUrls.normalize(url);
        if (key != null && key.length() > MAX_URL) {
            throw new IllegalArgumentException("The source URL is longer than " + MAX_URL + " characters");
        }

        if (key != null && keyExists(key)) {
            // Checked first so a refused duplicate does not use up an id; the unique column below
            // still catches the case where another process stores the same article at the same moment.
            throw new DuplicateIncidentException(url);
        }

        String sql = "INSERT INTO incident (incident_type, title, summary, center_lat, center_lon, radius_km, severity, "
                + "estimated_people, source_url, source_key, reported_epoch_second, reported_nano, incident_state) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Instant reportedAt = incident.reportedAt();
            insert.setString(1, incident.type().name());
            insert.setString(2, truncate(incident.title(), MAX_TITLE));
            insert.setString(3, truncate(incident.summary(), MAX_SUMMARY));
            insert.setDouble(4, incident.area().center().lat());
            insert.setDouble(5, incident.area().center().lon());
            insert.setDouble(6, incident.area().radiusKm());
            insert.setString(7, incident.severity().name());
            insert.setInt(8, incident.estimatedPeople());
            insert.setString(9, url);
            insert.setString(10, key);
            insert.setLong(11, reportedAt.getEpochSecond());
            insert.setInt(12, reportedAt.getNano());
            insert.setString(13, incident.state().name());
            insert.executeUpdate();

            long id;
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RepositoryException("The database did not return an id for the new incident");
                }
                id = keys.getLong(1);
            }
            incident.assignId(id);
            loaded.put(id, incident);
            return incident;
        } catch (SQLException e) {
            if (key != null && isUniqueViolation(e)) {
                throw new DuplicateIncidentException(url);
            }
            throw failure("save '" + incident.title() + "'", e);
        }
    }

    @Override
    public synchronized void update(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        long id = incident.id().orElseThrow(() -> new IllegalArgumentException("Incident was never saved: " + incident.title()));
        try (PreparedStatement update = connection().prepareStatement("UPDATE incident SET incident_state = ? WHERE id = ?")) {
            update.setString(1, incident.state().name());
            update.setLong(2, id);
            if (update.executeUpdate() == 0) {
                throw new IllegalArgumentException("No stored incident with id " + id);
            }
        } catch (SQLException e) {
            throw failure("update incident #" + id, e);
        }
        loaded.put(id, incident);
    }

    @Override
    public synchronized Optional<Incident> findById(long id) {
        Incident known = loaded.get(id);
        if (known != null) {
            return Optional.of(known);
        }
        List<Incident> found = query("SELECT " + COLUMNS + " FROM incident WHERE id = ?", id);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public synchronized Optional<Incident> findBySourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }
        List<Incident> found = query("SELECT " + COLUMNS + " FROM incident WHERE source_key = ?", SourceUrls.normalize(sourceUrl));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public synchronized List<Incident> findAll() {
        return query("SELECT " + COLUMNS + " FROM incident ORDER BY id");
    }

    /** Incidents whose <em>stored</em> state is the given one (a change not yet passed to {@link #update} is not seen). */
    @Override
    public synchronized List<Incident> findByState(IncidentState state) {
        Objects.requireNonNull(state, "state");
        return query("SELECT " + COLUMNS + " FROM incident WHERE incident_state = ? ORDER BY id", state.name());
    }

    /** Closes the database connection. The repository opens a new one if it is used again. */
    @Override
    public synchronized void close() {
        closeConnection();
    }

    // ---- internals -----------------------------------------------------------------------------

    private void createSchema() {
        synchronized (this) {
            try (Statement statement = connection().createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS incident ("
                        + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                        + "incident_type VARCHAR(20) NOT NULL, "
                        + "title VARCHAR(" + MAX_TITLE + ") NOT NULL, "
                        + "summary VARCHAR(" + MAX_SUMMARY + ") NOT NULL, "
                        + "center_lat DOUBLE PRECISION NOT NULL, "
                        + "center_lon DOUBLE PRECISION NOT NULL, "
                        + "radius_km DOUBLE PRECISION NOT NULL, "
                        + "severity VARCHAR(20) NOT NULL, "
                        + "estimated_people INTEGER NOT NULL, "
                        + "source_url VARCHAR(" + MAX_URL + "), "
                        + "source_key VARCHAR(" + MAX_URL + ") UNIQUE, "
                        + "reported_epoch_second BIGINT NOT NULL, "
                        + "reported_nano INTEGER NOT NULL, "
                        + "incident_state VARCHAR(20) NOT NULL)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_incident_state ON incident (incident_state)");
            } catch (SQLException e) {
                closeConnection();
                throw failure("set up the incident table", e);
            }
        }
    }

    private boolean keyExists(String key) {
        try (PreparedStatement check = connection().prepareStatement("SELECT 1 FROM incident WHERE source_key = ?")) {
            check.setString(1, key);
            try (ResultSet rows = check.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException e) {
            throw failure("check for a duplicate", e);
        }
    }

    /** Runs a SELECT and turns each row into an incident (the cached object if it was loaded before). */
    private List<Incident> query(String sql, Object... parameters) {
        try (PreparedStatement select = connection().prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = select.executeQuery()) {
                List<Incident> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(readRow(rows));
                }
                return result;
            }
        } catch (SQLException e) {
            throw failure("read incidents", e);
        }
    }

    private Incident readRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        Incident known = loaded.get(id);
        if (known != null) {
            return known;
        }
        try {
            Incident incident = Incident.builder(
                            IncidentType.parse(row.getString("incident_type")),
                            row.getString("title"),
                            new GeoArea(new GeoPoint(row.getDouble("center_lat"), row.getDouble("center_lon")), row.getDouble("radius_km")))
                    .summary(row.getString("summary"))
                    .severity(Severity.parse(row.getString("severity")))
                    .estimatedPeople(row.getInt("estimated_people"))
                    .sourceUrl(row.getString("source_url"))
                    .reportedAt(Instant.ofEpochSecond(row.getLong("reported_epoch_second"), row.getInt("reported_nano")))
                    .id(id)
                    .state(IncidentState.valueOf(row.getString("incident_state")))
                    .build();
            loaded.put(id, incident);
            return incident;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RepositoryException("Stored incident #" + id + " is damaged: " + e.getMessage(), e);
        }
    }

    /** The open connection, reopened if it was closed or has stopped working. */
    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            closeConnection();
            connection = source.open();
        }
        return connection;
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not close the database connection cleanly: " + e.getMessage());
            }
            connection = null;
        }
    }

    private static boolean isUniqueViolation(SQLException e) {
        for (SQLException current = e; current != null; current = current.getNextException()) {
            if (UNIQUE_VIOLATION.equals(current.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static RepositoryException failure(String action, SQLException cause) {
        return new RepositoryException("Could not " + action + ": " + cause.getMessage(), cause);
    }

    /** Cuts text to {@code max} characters, ending with an ellipsis, without splitting an emoji in half. */
    static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "…";
    }
}
