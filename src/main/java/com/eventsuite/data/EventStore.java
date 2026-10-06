package com.eventsuite.data;

import com.eventsuite.core.Event;
import com.eventsuite.core.EventCategory;
import com.eventsuite.core.EventStatus;
import com.eventsuite.core.Venue;
import com.eventsuite.ticketing.TicketType;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stores and reads events, the venues they use and the ticket tiers they sell.
 *
 * <p>Events, venues and ticket types live together because they are set up together:
 * a new event names a venue and is immediately given its tiers. They are three
 * tables rather than one because a venue outlives the events in it and a tier's
 * history has to survive the event it belonged to.
 *
 * <p>Every method here takes the connection from the caller. Nothing opens its own,
 * so a whole operation — creating an event, its tiers and its budget headings — runs
 * in one transaction and either all of it lands or none of it does. A half-created
 * event with tiers but no venue is worse than a failure.
 */
public final class EventStore {

    private static final String EVENT_COLUMNS =
            "id, name, category, status, venue_id, venue_name, start_date, end_date, doors_open,"
                    + " start_time, end_time, organiser, description, brand_primary, brand_accent,"
                    + " website_url, capacity, budget_planned, social_handle, tags, published,"
                    + " created_at";

    private static final String TYPE_COLUMNS =
            "id, event_id, name, description, price, quantity, access_tier, sales_open,"
                    + " sales_close, refundable, sort_order";

    private final Connection connection;

    public EventStore(Connection connection) {
        this.connection = connection;
    }

    // ---------------------------------------------------------------- events

    /** Inserts a new event. */
    public void insert(Event event) throws SQLException {
        Sql.update(connection,
                "INSERT INTO events (" + EVENT_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, event.getId());
                    Sql.setText(statement, 2, event.getName());
                    Sql.setText(statement, 3, event.getCategory().name());
                    Sql.setText(statement, 4, event.getStatus().name());
                    Sql.setText(statement, 5, event.getVenueId());
                    Sql.setText(statement, 6, event.getVenueName());
                    Sql.setDate(statement, 7, event.getStartDate());
                    Sql.setDate(statement, 8, event.getEndDate());
                    Sql.setTime(statement, 9, event.getDoorsOpen());
                    Sql.setTime(statement, 10, event.getStartTime());
                    Sql.setTime(statement, 11, event.getEndTime());
                    Sql.setText(statement, 12, event.getOrganiser());
                    Sql.setText(statement, 13, event.getDescription());
                    Sql.setText(statement, 14, event.getBrandPrimary());
                    Sql.setText(statement, 15, event.getBrandAccent());
                    Sql.setText(statement, 16, event.getWebsiteUrl());
                    Sql.setInt(statement, 17, event.getCapacity());
                    Sql.setMoney(statement, 18, event.getBudgetPlanned());
                    Sql.setText(statement, 19, event.getSocialHandle());
                    Sql.setText(statement, 20, Sql.joinList(event.getTags()));
                    Sql.setFlag(statement, 21, event.isPublished());
                    Sql.setInstant(statement, 22, Instant.now());
                });
    }

    /** Rewrites an event that already exists. */
    public void update(Event event) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE events SET name=?, category=?, status=?, venue_id=?, venue_name=?,"
                        + " start_date=?, end_date=?, doors_open=?, start_time=?, end_time=?,"
                        + " organiser=?, description=?, brand_primary=?, brand_accent=?,"
                        + " website_url=?, capacity=?, budget_planned=?, social_handle=?, tags=?,"
                        + " published=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, event.getName());
                    Sql.setText(statement, 2, event.getCategory().name());
                    Sql.setText(statement, 3, event.getStatus().name());
                    Sql.setText(statement, 4, event.getVenueId());
                    Sql.setText(statement, 5, event.getVenueName());
                    Sql.setDate(statement, 6, event.getStartDate());
                    Sql.setDate(statement, 7, event.getEndDate());
                    Sql.setTime(statement, 8, event.getDoorsOpen());
                    Sql.setTime(statement, 9, event.getStartTime());
                    Sql.setTime(statement, 10, event.getEndTime());
                    Sql.setText(statement, 11, event.getOrganiser());
                    Sql.setText(statement, 12, event.getDescription());
                    Sql.setText(statement, 13, event.getBrandPrimary());
                    Sql.setText(statement, 14, event.getBrandAccent());
                    Sql.setText(statement, 15, event.getWebsiteUrl());
                    Sql.setInt(statement, 16, event.getCapacity());
                    Sql.setMoney(statement, 17, event.getBudgetPlanned());
                    Sql.setText(statement, 18, event.getSocialHandle());
                    Sql.setText(statement, 19, Sql.joinList(event.getTags()));
                    Sql.setFlag(statement, 20, event.isPublished());
                    Sql.setText(statement, 21, event.getId());
                });
        if (changed == 0) {
            throw new SQLException("No event to update with id " + event.getId());
        }
    }

    /**
     * Saves an event, inserting or updating as needed.
     *
     * <p>Callers use this rather than choosing, because choosing wrongly is the easy
     * mistake and produces a duplicate or a silent no-op depending on which way it
     * went wrong.
     */
    public void save(Event event) throws SQLException {
        if (exists(event.getId())) {
            update(event);
        } else {
            insert(event);
        }
    }

    /** Whether an event with this id is stored. */
    public boolean exists(String eventId) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM events WHERE id=?",
                statement -> Sql.setText(statement, 1, eventId)) > 0;
    }

    /** One event, or null. */
    public Event findById(String eventId) throws SQLException {
        return Sql.queryOne(connection, "SELECT " + EVENT_COLUMNS + " FROM events WHERE id=?",
                statement -> Sql.setText(statement, 1, eventId), EventStore::readEvent);
    }

    /** Every event, soonest first. */
    public List<Event> findAll() throws SQLException {
        return Sql.query(connection, "SELECT " + EVENT_COLUMNS + " FROM events ORDER BY start_date",
                null, EventStore::readEvent);
    }

    /** Every event in a category. */
    public List<Event> findByCategory(EventCategory category) throws SQLException {
        return Sql.query(connection,
                "SELECT " + EVENT_COLUMNS + " FROM events WHERE category=? ORDER BY start_date",
                statement -> Sql.setText(statement, 1, category.name()), EventStore::readEvent);
    }

    /**
     * Events grouped by category, soonest first within each group.
     *
     * <p>The ordering is the point of the method. The catalogue's first question is
     * "what kind of things am I running", so the categories are the top level and the
     * dates are the thing you look at once you are inside one.
     */
    public java.util.Map<EventCategory, List<Event>> groupByCategory() throws SQLException {
        java.util.EnumMap<EventCategory, List<Event>> grouped =
                new java.util.EnumMap<>(EventCategory.class);
        for (EventCategory category : EventCategory.values()) {
            grouped.put(category, new ArrayList<>());
        }
        for (Event event : findAll()) {
            grouped.computeIfAbsent(event.getCategory(), key -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    /**
     * Events at or after a date, soonest first.
     *
     * <p>Used by the upcoming panel. A date rather than "not finished" because an
     * event whose date has passed and which is still in planning is worth seeing.
     */
    public List<Event> findFrom(LocalDate date) throws SQLException {
        return Sql.query(connection,
                "SELECT " + EVENT_COLUMNS + " FROM events WHERE start_date>=? ORDER BY start_date",
                statement -> Sql.setDate(statement, 1, date), EventStore::readEvent);
    }

    /**
     * Events happening between two dates.
     *
     * <p>Matched on overlap rather than on the start date, so a four-day conference
     * that began yesterday appears in a report for a day inside it. A start-date-only
     * test would silently drop it and understate what was running.
     */
    public List<Event> findOverlapping(LocalDate from, LocalDate to) throws SQLException {
        return Sql.query(connection,
                "SELECT " + EVENT_COLUMNS + " FROM events"
                        + " WHERE start_date<=? AND end_date>=? ORDER BY start_date",
                statement -> {
                    Sql.setDate(statement, 1, to);
                    Sql.setDate(statement, 2, from);
                }, EventStore::readEvent);
    }
    /** How many events are stored, for the dashboard. */
    public int countAll() throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM events", null);
    }

    /** How many events sit in a given state. */
    public int countByStatus(EventStatus status) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM events WHERE status=?",
                statement -> Sql.setText(statement, 1, status.name()));
    }

    /**
     * Whether an event could be removed without losing anything.
     *
     * <p>Read-only. It counts what is attached to the event and answers, without
     * touching anything — which matters because this is called from a screen that is
     * asking permission, and a question must never destroy the thing it is asking
     * about.
     */
    public boolean isDeletable(String eventId) throws SQLException {
        return reasonNotDeletable(eventId).isEmpty();
    }

    /**
     * Why an event cannot be removed, or an empty string when it can.
     *
     * <p>The check lives in one place so the screen that asks and the operation that
     * deletes cannot disagree about the answer. The message names what is in the way,
     * because "cannot delete" on its own tells the person nothing to act on.
     */
    public String reasonNotDeletable(String eventId) throws SQLException {
        int sold = Sql.count(connection,
                "SELECT COUNT(*) FROM tickets WHERE event_id=? AND status IN"
                        + " ('SOLD','CHECKED_IN')",
                statement -> Sql.setText(statement, 1, eventId));
        if (sold > 0) {
            return sold + " ticket(s) have been sold against it, so its sales history is"
                    + " the only record they happened";
        }
        int paid = Sql.count(connection,
                "SELECT COUNT(*) FROM payments WHERE event_id=? AND status='SETTLED'",
                statement -> Sql.setText(statement, 1, eventId));
        if (paid > 0) {
            return paid + " settled payment(s) are recorded against it";
        }
        int arrived = Sql.count(connection,
                "SELECT COUNT(*) FROM check_ins WHERE event_id=? AND outcome IN"
                        + " ('ADMITTED','WRONG_DOOR','NO_TICKET')",
                statement -> Sql.setText(statement, 1, eventId));
        if (arrived > 0) {
            return arrived + " people have already checked in";
        }
        return "";
    }

    /**
     * Removes an event, but only if nothing has been sold against it.
     *
     * <p>An event with tickets, payments or check-ins is refused rather than deleted.
     * Its financial and attendance history is the only evidence that the event
     * happened, and cascading it away because somebody tidied up a list is not a
     * recoverable mistake.
     *
     * @return true when it was deleted
     * @throws SQLException when the event has history, with the reason as the message
     */
    public boolean deleteIfUnused(String eventId) throws SQLException {
        String blocked = reasonNotDeletable(eventId);
        if (!blocked.isEmpty()) {
            throw new SQLException("Cannot delete " + eventId + ": " + blocked);
        }
        return Sql.delete(connection, "DELETE FROM events WHERE id=?",
                statement -> Sql.setText(statement, 1, eventId)) > 0;
    }

    // ---------------------------------------------------------------- venues

    /** Inserts a venue. */
    public void insertVenue(Venue venue) throws SQLException {
        Sql.update(connection,
                "INSERT INTO venues (id, name, address, city, capacity, rooms, step_free, parking,"
                        + " internet, contact_name, contact_email, contact_phone, notes, facilities,"
                        + " room_names) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, venue.getId());
                    Sql.setText(statement, 2, venue.getName());
                    Sql.setText(statement, 3, venue.getAddress());
                    Sql.setText(statement, 4, venue.getCity());
                    Sql.setInt(statement, 5, venue.getCapacity());
                    Sql.setInt(statement, 6, venue.getRooms());
                    Sql.setFlag(statement, 7, venue.isStepFreeAccess());
                    Sql.setFlag(statement, 8, venue.hasParking());
                    Sql.setFlag(statement, 9, venue.hasInternetAccess());
                    Sql.setText(statement, 10, venue.getContactName());
                    Sql.setText(statement, 11, venue.getContactEmail());
                    Sql.setText(statement, 12, venue.getContactPhone());
                    Sql.setText(statement, 13, venue.getNotes());
                    Sql.setText(statement, 14, Sql.joinList(venue.getFacilities()));
                    Sql.setText(statement, 15, Sql.joinList(venue.getRoomNames()));
                });
    }

    /** Rewrites a venue. */
    public void updateVenue(Venue venue) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE venues SET name=?, address=?, city=?, capacity=?, rooms=?, step_free=?,"
                        + " parking=?, internet=?, contact_name=?, contact_email=?, contact_phone=?,"
                        + " notes=?, facilities=?, room_names=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, venue.getName());
                    Sql.setText(statement, 2, venue.getAddress());
                    Sql.setText(statement, 3, venue.getCity());
                    Sql.setInt(statement, 4, venue.getCapacity());
                    Sql.setInt(statement, 5, venue.getRooms());
                    Sql.setFlag(statement, 6, venue.isStepFreeAccess());
                    Sql.setFlag(statement, 7, venue.hasParking());
                    Sql.setFlag(statement, 8, venue.hasInternetAccess());
                    Sql.setText(statement, 9, venue.getContactName());
                    Sql.setText(statement, 10, venue.getContactEmail());
                    Sql.setText(statement, 11, venue.getContactPhone());
                    Sql.setText(statement, 12, venue.getNotes());
                    Sql.setText(statement, 13, Sql.joinList(venue.getFacilities()));
                    Sql.setText(statement, 14, Sql.joinList(venue.getRoomNames()));
                    Sql.setText(statement, 15, venue.getId());
                });
        if (changed == 0) {
            throw new SQLException("No venue to update with id " + venue.getId());
        }
    }
    private static final String VENUE_COLUMNS =
            "id, name, address, city, capacity, rooms, step_free, parking, internet,"
                    + " contact_name, contact_email, contact_phone, notes, facilities, room_names";
    /** Every venue, by name. */
    public List<Venue> findAllVenues() throws SQLException {
        return Sql.query(connection, "SELECT " + VENUE_COLUMNS + " FROM venues ORDER BY name",
                null, EventStore::readVenue);
    }
    // ---------------------------------------------------------------- ticket tiers

    /** Inserts a ticket tier. */
    public void insertTicketType(TicketType type) throws SQLException {
        Sql.update(connection,
                "INSERT INTO ticket_types (" + TYPE_COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, type.getId());
                    Sql.setText(statement, 2, type.getEventId());
                    Sql.setText(statement, 3, type.getName());
                    Sql.setText(statement, 4, type.getDescription());
                    Sql.setMoney(statement, 5, type.getPrice());
                    Sql.setInt(statement, 6, type.getQuantity());
                    Sql.setText(statement, 7, type.getAccessTier().name());
                    Sql.setDate(statement, 8, type.getSalesOpen());
                    Sql.setDate(statement, 9, type.getSalesClose());
                    Sql.setFlag(statement, 10, type.isRefundable());
                    Sql.setInt(statement, 11, type.getSortOrder());
                });
    }
    /** One ticket tier, or null. */
    public TicketType findTicketType(String typeId) throws SQLException {
        return Sql.queryOne(connection,
                "SELECT " + TYPE_COLUMNS + " FROM ticket_types WHERE id=?",
                statement -> Sql.setText(statement, 1, typeId), EventStore::readTicketType);
    }

    /** The tiers for an event, in the order the organiser arranged them. */
    public List<TicketType> findTicketTypes(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + TYPE_COLUMNS
                        + " FROM ticket_types WHERE event_id=? ORDER BY sort_order, price",
                statement -> Sql.setText(statement, 1, eventId), EventStore::readTicketType);
    }
    /** The next sort position for a new tier on an event. */
    public int nextTicketTypeOrder(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM ticket_types WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /**
     * How many tickets of a tier have been sold.
     *
     * <p>Only counts states that take up the allocation. Refunded tickets have gone
     * back on sale, so counting them would permanently shrink the tier.
     */
    public int countSoldForType(String typeId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM tickets WHERE type_id=? AND status IN ('SOLD','CHECKED_IN')",
                statement -> Sql.setText(statement, 1, typeId));
    }

    /** Total tickets sold across every tier of an event. */
    public int countSoldForEvent(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM tickets WHERE event_id=? AND status IN ('SOLD','CHECKED_IN')",
                statement -> Sql.setText(statement, 1, eventId));
    }
    // ---------------------------------------------------------------- mapping

    private static Event readEvent(java.sql.ResultSet results) throws SQLException {
        Event event = new Event(
                Sql.text(results, "id"),
                Sql.text(results, "name"),
                EventCategory.fromLabel(Sql.text(results, "category")),
                Sql.text(results, "venue_id"),
                Sql.text(results, "venue_name"),
                Sql.date(results, "start_date"),
                Sql.date(results, "end_date"),
                Sql.time(results, "doors_open"),
                Sql.time(results, "start_time"),
                Sql.time(results, "end_time"),
                Sql.text(results, "organiser"),
                Sql.text(results, "description"),
                Sql.text(results, "brand_primary"),
                Sql.text(results, "brand_accent"),
                Sql.text(results, "website_url"),
                Sql.number(results, "capacity"),
                Sql.splitList(Sql.text(results, "tags")),
                EventStatus.fromLabel(Sql.text(results, "status")),
                Sql.money(results, "budget_planned"),
                Sql.text(results, "social_handle"));
        // The status is restored directly rather than through moveTo, because a row
        // read from disk must be able to hold a state today's rules would not produce.
        // Otherwise an old database could not be opened.
        event.restoreStatus(EventStatus.fromLabel(Sql.text(results, "status")));
        return event;
    }

    private static Venue readVenue(java.sql.ResultSet results) throws SQLException {
        return new Venue(
                Sql.text(results, "id"),
                Sql.text(results, "name"),
                Sql.text(results, "address"),
                Sql.text(results, "city"),
                Sql.number(results, "capacity"),
                Sql.number(results, "rooms"),
                Sql.flag(results, "step_free"),
                Sql.flag(results, "parking"),
                Sql.flag(results, "internet"),
                Sql.text(results, "contact_name"),
                Sql.text(results, "contact_email"),
                Sql.text(results, "contact_phone"),
                Sql.text(results, "notes"),
                Sql.splitList(Sql.text(results, "facilities")),
                Sql.splitList(Sql.text(results, "room_names")));
    }

    private static TicketType readTicketType(java.sql.ResultSet results) throws SQLException {
        return new TicketType(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "name"),
                Sql.text(results, "description"),
                Sql.money(results, "price"),
                Sql.number(results, "quantity"),
                com.eventsuite.ticketing.AccessTier.fromLabel(Sql.text(results, "access_tier")),
                Sql.date(results, "sales_open"),
                Sql.date(results, "sales_close"),
                Sql.flag(results, "refundable"),
                Sql.number(results, "sort_order"));
    }
}
