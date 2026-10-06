package com.eventsuite.data;

import com.eventsuite.ticketing.AccessTier;
import com.eventsuite.ticketing.Ticket;
import com.eventsuite.ticketing.TicketStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores and reads tickets, the thing the door scans.
 *
 * <p>Tickets are keyed on their printed reference rather than a generated id, because
 * the reference is what exists in the world: it is on the card, in the email and
 * read aloud at the desk. Making it the key means a duplicate scan is caught by the
 * database itself rather than by a check that might be forgotten.
 *
 * <p>A ticket holds the tier name and price it was sold at, not a lookup back to the
 * tier. Repricing a tier must not rewrite what people already paid, so the historical
 * facts are copied onto the ticket at the point of sale and never looked up again.
 */
public final class TicketStore {

    private static final String COLUMNS =
            "reference, event_id, type_id, attendee_id, attendee_name, attendee_email,"
                    + " tier_name, access_tier, price_paid, discount_code, status, issued_at,"
                    + " hold_expires, checked_in_at, gate, checked_in_by, seat_label, notes";

    private final Connection connection;

    public TicketStore(Connection connection) {
        this.connection = connection;
    }

    /** Inserts a ticket. Fails if the reference is already in use. */
    public void insert(Ticket ticket) throws SQLException {
        Sql.update(connection,
                "INSERT INTO tickets (" + COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, ticket.getReference());
                    Sql.setText(statement, 2, ticket.getEventId());
                    Sql.setText(statement, 3, ticket.getTicketTypeId());
                    Sql.setText(statement, 4, ticket.getAttendeeId());
                    Sql.setText(statement, 5, ticket.getAttendeeName());
                    Sql.setText(statement, 6, ticket.getAttendeeEmail());
                    Sql.setText(statement, 7, ticket.getTierName());
                    Sql.setText(statement, 8, ticket.getAccessTier().name());
                    Sql.setMoney(statement, 9, new java.math.BigDecimal(ticket.getPricePaid()));
                    Sql.setText(statement, 10, ticket.getDiscountCode());
                    Sql.setText(statement, 11, ticket.getStatus().name());
                    Sql.setInstant(statement, 12, ticket.getIssuedAt());
                    Sql.setInstant(statement, 13, ticket.getHoldExpiresAt());
                    Sql.setInstant(statement, 14, ticket.getCheckedInAt());
                    Sql.setText(statement, 15, ticket.getGate());
                    Sql.setText(statement, 16, ticket.getCheckedInBy());
                    Sql.setText(statement, 17, ticket.getSeatLabel());
                    Sql.setText(statement, 18, ticket.getNotes());
                });
    }

    /** Rewrites a ticket, used when its state or arrival details change. */
    public void update(Ticket ticket) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE tickets SET status=?, checked_in_at=?, gate=?, checked_in_by=?,"
                        + " hold_expires=?, seat_label=?, notes=? WHERE reference=?",
                statement -> {
                    Sql.setText(statement, 1, ticket.getStatus().name());
                    Sql.setInstant(statement, 2, ticket.getCheckedInAt());
                    Sql.setText(statement, 3, ticket.getGate());
                    Sql.setText(statement, 4, ticket.getCheckedInBy());
                    Sql.setInstant(statement, 5, ticket.getHoldExpiresAt());
                    Sql.setText(statement, 6, ticket.getSeatLabel());
                    Sql.setText(statement, 7, ticket.getNotes());
                    Sql.setText(statement, 8, ticket.getReference());
                });
        if (changed == 0) {
            throw new SQLException("No ticket to update with reference " + ticket.getReference());
        }
    }

    /**
     * A ticket by its exact reference, or null.
     *
     * <p>Exact, not a prefix. Prefix matching is offered separately and deliberately:
     * it is convenient at the desk and unsafe as a lookup, so the two are not the
     * same method.
     */
    public Ticket findByReference(String reference) throws SQLException {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return Sql.queryOne(connection,
                "SELECT " + COLUMNS + " FROM tickets WHERE reference=?",
                statement -> Sql.setText(statement, 1, reference.trim().toUpperCase(
                        java.util.Locale.ROOT)), TicketStore::read);
    }

    /**
     * Tickets whose reference starts with the given text.
     *
     * <p>For the desk, where a ticket is read aloud and misheard. Returns everything
     * matching rather than guessing, so the operator chooses from a list rather than
     * being silently admitted as somebody else.
     */
    public List<Ticket> findByReferencePrefix(String prefix, int limit) throws SQLException {
        if (prefix == null || prefix.length() < 4) {
            // Shorter than this would match most of the book and tell nobody anything.
            return List.of();
        }
        List<Ticket> matches = Sql.query(connection,
                "SELECT " + COLUMNS + " FROM tickets WHERE reference LIKE ?"
                        + " ORDER BY reference LIMIT " + Math.max(1, limit),
                statement -> Sql.setText(statement, 1,
                        prefix.trim().toUpperCase(java.util.Locale.ROOT) + "%"),
                TicketStore::read);
        return matches;
    }

    /** Every ticket for an event. */
    public List<Ticket> findByEvent(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + COLUMNS + " FROM tickets WHERE event_id=?"
                        + " ORDER BY issued_at DESC",
                statement -> Sql.setText(statement, 1, eventId), TicketStore::read);
    }
    /** The tickets held by one attendee for one event. */
    public List<Ticket> findByAttendee(String eventId, String attendeeId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + COLUMNS + " FROM tickets WHERE event_id=? AND attendee_id=?"
                        + " ORDER BY issued_at",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, attendeeId);
                }, TicketStore::read);
    }
    /** How many tickets an event has in a given state. */
    public int countByStatus(String eventId, TicketStatus status) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM tickets WHERE event_id=? AND status=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, status.name());
                });
    }

    /** How many tickets have been sold and used across every event. */
    public int countSold(String eventId) throws SQLException {
        return countByStatus(eventId, TicketStatus.SOLD) + countByStatus(eventId, TicketStatus.CHECKED_IN);
    }

    /** How many tickets a tier has sold. */
    public int countSoldForType(String typeId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM tickets WHERE type_id=? AND status IN ('SOLD','CHECKED_IN')",
                statement -> Sql.setText(statement, 1, typeId));
    }
    /** Whether a reference is already taken, so a new one can be minted instead. */
    public boolean referenceExists(String reference) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM tickets WHERE reference=?",
                statement -> Sql.setText(statement, 1, reference)) > 0;
    }

    /**
     * Finds a reference that is not in use.
     *
     * <p>Ten characters make a collision vanishingly unlikely, but "unlikely" is not
     * "impossible" and a duplicate primary key would lose a sale. Trying the next
     * random value costs one query only in the case that never happens.
     */
    public String mintReference() throws SQLException {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = Ticket.newReference();
            if (!referenceExists(candidate)) {
                return candidate;
            }
        }
        // Twenty collisions is not chance; something is wrong with the generator and
        // continuing would keep generating the same values.
        throw new SQLException("Could not find an unused ticket reference");
    }

    /** Total paid across an event's sold tickets. */
    public java.math.BigDecimal sumPaid(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT SUM(price_paid) FROM tickets WHERE event_id=?"
                        + " AND status IN ('SOLD','CHECKED_IN')",
                statement -> Sql.setText(statement, 1, eventId));
    }
    /** The average paid ticket price on an event. Zero when nothing has sold. */
    public java.math.BigDecimal averagePaid(String eventId) throws SQLException {
        java.math.BigDecimal count = java.math.BigDecimal.valueOf(countSold(eventId));
        if (count.signum() == 0) {
            return java.math.BigDecimal.ZERO;
        }
        return sumPaid(eventId).divide(count, 2, java.math.RoundingMode.HALF_UP);
    }

    /** Tickets whose unpaid hold has passed its deadline. */
    public List<Ticket> findExpiredHolds(Instant now) throws SQLException {
        return Sql.query(connection,
                "SELECT " + COLUMNS + " FROM tickets WHERE status='RESERVED'"
                        + " AND hold_expires IS NOT NULL AND hold_expires<?",
                statement -> Sql.setInstant(statement, 1, now), TicketStore::read);
    }
    /** What was given away by discount across an event, for the finance reconciliation. */
    public java.math.BigDecimal sumDiscountGiven(String eventId) throws SQLException {
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (Ticket ticket : findByEvent(eventId)) {
            if (!ticket.getDiscountCode().isEmpty() && ticket.isSold()) {
                total = total.add(new java.math.BigDecimal(ticket.getPricePaid()));
            }
        }
        return total;
    }

    /** How many tickets at each access level have sold, for the access chart. */
    public java.util.Map<String, Integer> soldCountByTierName(String eventId) throws SQLException {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Ticket ticket : findByEvent(eventId)) {
            if (ticket.isSold()) {
                counts.merge(ticket.getAccessTier().getLabel(), 1, Integer::sum);
            }
        }
        return counts;
    }
    // ---------------------------------------------------------------- mapping

    private static Ticket read(java.sql.ResultSet results) throws SQLException {
        Ticket ticket = new Ticket(
                Sql.text(results, "reference"),
                Sql.text(results, "event_id"),
                Sql.text(results, "type_id"),
                Sql.text(results, "attendee_id"),
                Sql.text(results, "attendee_name"),
                Sql.text(results, "attendee_email"),
                Sql.text(results, "tier_name"),
                AccessTier.fromLabel(Sql.text(results, "access_tier")),
                Sql.money(results, "price_paid").toPlainString(),
                Sql.text(results, "discount_code"),
                results.getTimestamp("issued_at") == null ? Instant.now()
                        : results.getTimestamp("issued_at").toInstant(),
                results.getTimestamp("hold_expires") == null ? null
                        : results.getTimestamp("hold_expires").toInstant(),
                Sql.text(results, "seat_label"),
                Sql.text(results, "notes"));
        ticket.restore(TicketStatus.fromLabel(Sql.text(results, "status")),
                results.getTimestamp("checked_in_at") == null ? null
                        : results.getTimestamp("checked_in_at").toInstant(),
                Sql.text(results, "gate"), Sql.text(results, "checked_in_by"));
        return ticket;
    }
}
