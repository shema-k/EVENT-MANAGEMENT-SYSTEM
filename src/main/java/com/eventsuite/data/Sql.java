package com.eventsuite.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Small helpers for the JDBC calls every store makes.
 *
 * <p>These exist so the stores read as statements about events and tickets rather
 * than as boilerplate. Each one closes what it opens and returns the null for the
 * empty case rather than an empty string, so "not set" stays distinguishable from
 * "set to nothing" — which matters for fields such as an organiser's name that a
 * dashboard shows as blank either way but a form must not overwrite.
 */
public final class Sql {

    private Sql() {
    }

    /** A record read from a single row. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet results) throws SQLException;
    }

    /** Binds values into a statement. */
    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /**
     * Runs one update and returns how many rows it touched.
     *
     * <p>Holding the connection's own monitor for the whole statement is what makes a
     * single connection safe to share. The interface reads on a background thread every
     * few seconds while a sale runs on the event thread, and a JDBC connection is not
     * safe to use from two threads at once — the symptom is a statement being closed
     * out from under a caller that is still using it.
     */
    public static int update(Connection connection, String sql, Binder binder)
            throws SQLException {
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (binder != null) {
                    binder.bind(statement);
                }
                return statement.executeUpdate();
            }
        }
    }
    /**
     * Runs a query and maps every row.
     *
     * <p>Synchronised on the connection for the same reason as {@link #update}: a
     * result set is read lazily and must not be closed by another thread's statement
     * going out of scope. Mapping happens inside the lock so the whole read is atomic
     * with respect to a concurrent write.
     */
    public static <T> List<T> query(Connection connection, String sql, Binder binder,
                                    RowMapper<T> mapper) throws SQLException {
        List<T> results = new ArrayList<>();
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (binder != null) {
                    binder.bind(statement);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        results.add(mapper.map(rows));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Runs a query expected to match at most one row.
     *
     * @return the row, or null when there was none. Never throws on multiple rows
     *         because the queries using it are all on primary keys.
     */
    public static <T> T queryOne(Connection connection, String sql, Binder binder,
                                 RowMapper<T> mapper) throws SQLException {
        List<T> results = query(connection, sql, binder, mapper);
        return results.isEmpty() ? null : results.get(0);
    }

    /** Counts the rows a query matches. */
    public static int count(Connection connection, String sql, Binder binder)
            throws SQLException {
        Integer value = queryOne(connection, sql, binder,
                results -> results.getInt(1));
        return value == null ? 0 : value;
    }

    /** Sums a column, treating an empty result as zero rather than null. */
    public static BigDecimal sum(Connection connection, String sql, Binder binder)
            throws SQLException {
        BigDecimal value = queryOne(connection, sql, binder,
                results -> results.getBigDecimal(1));
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Deletes the rows matching a condition, returning how many went. */
    public static int delete(Connection connection, String sql, Binder binder)
            throws SQLException {
        return update(connection, sql, binder);
    }

    // ---------------------------------------------------------------- primitives

    public static String text(ResultSet results, String column) throws SQLException {
        String value = results.getString(column);
        return value == null ? "" : value;
    }

    public static String nullableText(ResultSet results, String column) throws SQLException {
        return results.getString(column);
    }

    public static int number(ResultSet results, String column) throws SQLException {
        return results.getInt(column);
    }

    public static boolean flag(ResultSet results, String column) throws SQLException {
        return results.getBoolean(column);
    }

    public static BigDecimal money(ResultSet results, String column) throws SQLException {
        BigDecimal value = results.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    public static LocalDate date(ResultSet results, String column) throws SQLException {
        return results.getDate(column) == null ? null : results.getDate(column).toLocalDate();
    }

    public static LocalDateTime dateTime(ResultSet results, String column) throws SQLException {
        Timestamp value = results.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    public static java.time.LocalTime time(ResultSet results, String column) throws SQLException {
        Time value = results.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    public static Instant instant(ResultSet results, String column) throws SQLException {
        Timestamp value = results.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    // ---------------------------------------------------------------- binders

    public static void setText(PreparedStatement statement, int index, String value)
            throws SQLException {
        statement.setString(index, value);
    }
    public static void setInt(PreparedStatement statement, int index, int value)
            throws SQLException {
        statement.setInt(index, value);
    }

    public static void setFlag(PreparedStatement statement, int index, boolean value)
            throws SQLException {
        statement.setBoolean(index, value);
    }

    public static void setMoney(PreparedStatement statement, int index, BigDecimal value)
            throws SQLException {
        statement.setBigDecimal(index, value);
    }

    public static void setDate(PreparedStatement statement, int index, LocalDate value)
            throws SQLException {
        statement.setDate(index, value == null ? null : java.sql.Date.valueOf(value));
    }

    public static void setDateTime(PreparedStatement statement, int index, LocalDateTime value)
            throws SQLException {
        statement.setTimestamp(index, value == null ? null : Timestamp.valueOf(value));
    }

    public static void setInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        statement.setTimestamp(index, value == null ? null : Timestamp.from(value));
    }

    public static void setTime(PreparedStatement statement, int index, java.time.LocalTime value)
            throws SQLException {
        statement.setTime(index, value == null ? null : Time.valueOf(value));
    }

    /**
     * Joins a list into one comma separated column value.
     *
     * <p>Used for the short lists the schema keeps as text — tags, facilities, room
     * names. They are never filtered on, so a table each would be three columns and a
     * join for no query that exists.
     */
    public static String joinList(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(",", values);
    }

    /** Splits a comma separated column back into a list, dropping blanks. */
    public static List<String> splitList(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return parts;
        }
        for (String piece : value.split(",")) {
            String trimmed = piece.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }
}
