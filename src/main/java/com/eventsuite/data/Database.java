package com.eventsuite.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The embedded database holding every registered event.
 *
 * <p>One file, opened straight away, with no server to install and no password to
 * forget. The file is the whole system of record: every event, ticket, payment and
 * feedback row lives in it, so copying the file copies the business. That is the
 * right trade for a desktop tool used by one organiser or one small team, and it is
 * stated plainly here rather than presented as scalable.
 *
 * <p>The schema is created on first open rather than shipped as a migration script,
 * because there is only ever one shape of database here. If the file already has the
 * tables, nothing happens; adding a column to a new table therefore costs nothing
 * for existing users, which is the common case.
 */
public final class Database implements AutoCloseable {

    private final Path file;
    private final String jdbcUrl;
    private boolean schemaReady;

    public Database(Path file) {
        this.file = file;
        this.jdbcUrl = buildUrl(file);
    }

    /**
     * An in-memory database, used by the tests.
     *
     * <p>Each call gets a separate database, so tests cannot see one another's data.
     */
    public static Database inMemory(String name) {
        return new Database(null, "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
    }

    private Database(Path file, String url) {
        this.file = file;
        this.jdbcUrl = url;
    }

    private static String buildUrl(Path databaseFile) {
        if (databaseFile == null) {
            return null;
        }
        String path = databaseFile.toAbsolutePath().toString().replace('\\', '/');
        // H2 derives its own storage names, so a .dat extension would end up in the
        // file name as well.
        if (path.endsWith(".dat")) {
            path = path.substring(0, path.length() - 4);
        }
        return "jdbc:h2:file:" + path + ";DB_CLOSE_ON_EXIT=FALSE";
    }
    /**
     * Opens a connection, creating the file and its parent directory if needed.
     *
     * @throws IOException when the directory cannot be created
     * @throws SQLException when the file cannot be opened
     */
    public Connection open() throws IOException, SQLException {
        if (jdbcUrl == null) {
            throw new IOException("No database path configured");
        }
        if (file != null) {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    /**
     * Opens a connection and makes sure the tables are there.
     *
     * <p>Called once at startup rather than on every connection, because creating
     * tables is the expensive part and the shape never changes.
     */
    public Connection openReady() throws IOException, SQLException {
        Connection connection = open();
        if (!schemaReady) {
            Schema.apply(connection);
            schemaReady = true;
        }
        return connection;
    }
    /** Where the schema lives, for the tests to check it was applied. */
    public static String[] tableNames() {
        return Schema.TABLES;
    }

    /** Whether the tables exist in the given database. */
    public static boolean isSchemaPresent(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : Schema.TABLES) {
                try (java.sql.ResultSet results = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + table)) {
                    if (!results.next()) {
                        return false;
                    }
                } catch (SQLException missing) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Empties every table, keeping the schema. Used by the tests. */
    public void truncateAll(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Children first, so there is never a moment where a parent row is gone
            // while a child still points at it.
            for (int i = Schema.TABLES.length - 1; i >= 0; i--) {
                statement.execute("DELETE FROM " + Schema.TABLES[i]);
            }
        }
    }

    /** Nothing is pooled, so there is nothing to release on the way out. */
    @Override
    public void close() {
        // The driver closes its own connections when the JVM exits.
    }
}
