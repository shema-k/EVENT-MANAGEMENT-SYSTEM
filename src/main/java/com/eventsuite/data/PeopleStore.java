package com.eventsuite.data;

import com.eventsuite.people.Attendee;
import com.eventsuite.people.ResourceBooking;
import com.eventsuite.people.ResourceItem;
import com.eventsuite.people.Speaker;
import com.eventsuite.people.SpeakerStatus;
import com.eventsuite.ticketing.Registration;
import com.eventsuite.ticketing.RegistrationStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Stores and reads the people attached to events.
 *
 * <p>Five kinds of record live here: attendees, registrations, speakers, kit and kit
 * bookings. They are grouped because they answer to the same questions — who is
 * coming, who is speaking, and what has been reserved — and because a single event
 * needs all of them at once when the pre-event checklist is assembled.
 *
 * <p>Attendees are global and registrations are not. That split is the reason someone
 * who has been to three events is one row here and three rows in
 * {@code registrations}, which is what makes repeat attendance and lifetime value
 * answerable without stitching names together.
 */
public final class PeopleStore {

    private static final String ATTENDEE_COLUMNS =
            "id, first_name, last_name, email, phone, organisation, job_title, dietary,"
                    + " accessibility, consent_marketing";

    private static final String REGISTRATION_COLUMNS =
            "id, event_id, attendee_id, attendee_name, attendee_email, ticket_ref, referral,"
                    + " fee, fee_paid, status, waitlist_pos, registered_at";

    private static final String SPEAKER_COLUMNS =
            "id, event_id, first_name, last_name, email, phone, organisation, job_title,"
                    + " biography, session_title, session_abstract, room_name, session_start,"
                    + " session_end, fee, travel_budget, travel_notes, dietary, accessibility,"
                    + " needs_hotel, hotel_notes, photo_path, status";

    private static final String RESOURCE_COLUMNS =
            "id, name, kind, quantity, unit_cost, replacement, supplier, notes, hired";

    private static final String BOOKING_COLUMNS =
            "id, event_id, resource_id, quantity, from_at, until_at, purpose, notes, cost,"
                    + " returned, damaged";

    private final Connection connection;

    public PeopleStore(Connection connection) {
        this.connection = connection;
    }

    // ---------------------------------------------------------------- attendees

    /** Inserts an attendee. Fails if the email is already on file. */
    public void insertAttendee(Attendee attendee) throws SQLException {
        Sql.update(connection,
                "INSERT INTO attendees (" + ATTENDEE_COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, attendee.getId());
                    Sql.setText(statement, 2, attendee.getFirstName());
                    Sql.setText(statement, 3, attendee.getLastName());
                    Sql.setText(statement, 4, attendee.getEmail());
                    Sql.setText(statement, 5, attendee.getPhone());
                    Sql.setText(statement, 6, attendee.getOrganisation());
                    Sql.setText(statement, 7, attendee.getJobTitle());
                    Sql.setText(statement, 8, attendee.getDietaryNotes());
                    Sql.setText(statement, 9, attendee.getAccessibilityNotes());
                    Sql.setFlag(statement, 10, attendee.hasMarketingConsent());
                });
    }

    /** Rewrites an attendee. */
    public void updateAttendee(Attendee attendee) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE attendees SET first_name=?, last_name=?, email=?, phone=?,"
                        + " organisation=?, job_title=?, dietary=?, accessibility=?,"
                        + " consent_marketing=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, attendee.getFirstName());
                    Sql.setText(statement, 2, attendee.getLastName());
                    Sql.setText(statement, 3, attendee.getEmail());
                    Sql.setText(statement, 4, attendee.getPhone());
                    Sql.setText(statement, 5, attendee.getOrganisation());
                    Sql.setText(statement, 6, attendee.getJobTitle());
                    Sql.setText(statement, 7, attendee.getDietaryNotes());
                    Sql.setText(statement, 8, attendee.getAccessibilityNotes());
                    Sql.setFlag(statement, 9, attendee.hasMarketingConsent());
                    Sql.setText(statement, 10, attendee.getId());
                });
        if (changed == 0) {
            throw new SQLException("No attendee to update with id " + attendee.getId());
        }
    }

    /** Saves an attendee, inserting or updating as needed. */
    public void saveAttendee(Attendee attendee) throws SQLException {
        if (Sql.count(connection, "SELECT COUNT(*) FROM attendees WHERE id=?",
                statement -> Sql.setText(statement, 1, attendee.getId())) > 0) {
            updateAttendee(attendee);
        } else {
            insertAttendee(attendee);
        }
    }

    /** One attendee, or null. */
    public Attendee findAttendee(String attendeeId) throws SQLException {
        return Sql.queryOne(connection,
                "SELECT " + ATTENDEE_COLUMNS + " FROM attendees WHERE id=?",
                statement -> Sql.setText(statement, 1, attendeeId), PeopleStore::readAttendee);
    }

    /** Every attendee, by name. */
    public List<Attendee> findAllAttendees() throws SQLException {
        return Sql.query(connection,
                "SELECT " + ATTENDEE_COLUMNS + " FROM attendees ORDER BY last_name, first_name",
                null, PeopleStore::readAttendee);
    }
    /**
     * The attendee with this email address, or null.
     *
     * <p>Used when a returning attendee books again, so a second registration reuses
     * the existing record instead of creating a near-duplicate person.
     */
    public Attendee findAttendeeByEmail(String email) throws SQLException {
        if (email == null || email.isBlank()) {
            return null;
        }
        return Sql.queryOne(connection,
                "SELECT " + ATTENDEE_COLUMNS + " FROM attendees WHERE email=?",
                statement -> Sql.setText(statement, 1, email.trim().toLowerCase(
                        java.util.Locale.ROOT)), PeopleStore::readAttendee);
    }

    // ---------------------------------------------------------------- registrations

    /** Inserts a registration. */
    public void insertRegistration(Registration registration) throws SQLException {
        Sql.update(connection,
                "INSERT INTO registrations (" + REGISTRATION_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, registration.getId());
                    Sql.setText(statement, 2, registration.getEventId());
                    Sql.setText(statement, 3, registration.getAttendeeId());
                    Sql.setText(statement, 4, registration.getAttendeeName());
                    Sql.setText(statement, 5, registration.getAttendeeEmail());
                    Sql.setText(statement, 6, registration.getTicketReference());
                    Sql.setText(statement, 7, registration.getReferralSource());
                    Sql.setMoney(statement, 8, registration.getFee());
                    Sql.setMoney(statement, 9, registration.getFeePaid());
                    Sql.setText(statement, 10, registration.getStatus().name());
                    Sql.setText(statement, 11, registration.getWaitlistPosition());
                    Sql.setInstant(statement, 12, registration.getRegisteredAt());
                });
    }

    /** Rewrites a registration. */
    public void updateRegistration(Registration registration) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE registrations SET ticket_ref=?, referral=?, fee=?, fee_paid=?, status=?,"
                        + " waitlist_pos=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, registration.getTicketReference());
                    Sql.setText(statement, 2, registration.getReferralSource());
                    Sql.setMoney(statement, 3, registration.getFee());
                    Sql.setMoney(statement, 4, registration.getFeePaid());
                    Sql.setText(statement, 5, registration.getStatus().name());
                    Sql.setText(statement, 6, registration.getWaitlistPosition());
                    Sql.setText(statement, 7, registration.getId());
                });
        if (changed == 0) {
            throw new SQLException("No registration to update with id " + registration.getId());
        }
    }
    /** Every registration for an event. */
    public List<Registration> findRegistrations(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + REGISTRATION_COLUMNS + " FROM registrations WHERE event_id=?"
                        + " ORDER BY registered_at",
                statement -> Sql.setText(statement, 1, eventId),
                PeopleStore::readRegistration);
    }

    /** Registrations in a given state, for the waiting list and the cancelled list. */
    public List<Registration> findRegistrationsByStatus(String eventId, RegistrationStatus status)
            throws SQLException {
        return Sql.query(connection,
                "SELECT " + REGISTRATION_COLUMNS
                        + " FROM registrations WHERE event_id=? AND status=?"
                        + " ORDER BY registered_at",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, status.name());
                }, PeopleStore::readRegistration);
    }

    /** The registration of an attendee at an event, or null. */
    public Registration findRegistrationOf(String eventId, String attendeeId) throws SQLException {
        return Sql.queryOne(connection,
                "SELECT " + REGISTRATION_COLUMNS
                        + " FROM registrations WHERE event_id=? AND attendee_id=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, attendeeId);
                }, PeopleStore::readRegistration);
    }

    /** Registrations for somebody across every event, newest first. */
    public List<Registration> findRegistrationsOfAttendee(String attendeeId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + REGISTRATION_COLUMNS
                        + " FROM registrations WHERE attendee_id=? ORDER BY registered_at DESC",
                statement -> Sql.setText(statement, 1, attendeeId),
                PeopleStore::readRegistration);
    }

    /** How many registrations an event has. */
    public int countRegistrations(String eventId) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM registrations WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many registrations an event has in a given state. */
    public int countRegistrationsByStatus(String eventId, RegistrationStatus status)
            throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM registrations WHERE event_id=? AND status=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, status.name());
                });
    }

    /** Sum of the fees raised on an event's registrations. */
    public java.math.BigDecimal sumRegistrationFees(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(fee) FROM registrations WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Sum of what has actually been paid on an event's registrations. */
    public java.math.BigDecimal sumRegistrationFeesPaid(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(fee_paid) FROM registrations WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }
    // ---------------------------------------------------------------- speakers

    /** Inserts a speaker. */
    public void insertSpeaker(Speaker speaker) throws SQLException {
        Sql.update(connection,
                "INSERT INTO speakers (" + SPEAKER_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, speaker.getId());
                    Sql.setText(statement, 2, speaker.getEventId());
                    bindSpeakerDetails(statement, speaker, 3);
                });
    }

    /** Rewrites a speaker. */
    public void updateSpeaker(Speaker speaker) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE speakers SET first_name=?, last_name=?, email=?, phone=?,"
                        + " organisation=?, job_title=?, biography=?, session_title=?,"
                        + " session_abstract=?, room_name=?, session_start=?, session_end=?,"
                        + " fee=?, travel_budget=?, travel_notes=?, dietary=?, accessibility=?,"
                        + " needs_hotel=?, hotel_notes=?, photo_path=?, status=? WHERE id=?",
                statement -> {
                    bindSpeakerDetails(statement, speaker, 1);
                    Sql.setText(statement, 22, speaker.getId());
                });
        if (changed == 0) {
            throw new SQLException("No speaker to update with id " + speaker.getId());
        }
    }

    /**
     * Binds the twenty-one speaker fields that both the insert and the update share.
     *
     * <p>The update has no {@code id} or {@code event_id} column to fill, so its
     * parameters start one row earlier than the insert's. Binding from an explicit
     * start index is what keeps the two in step without duplicating every line, and it
     * is why the start index is passed rather than assumed.
     */
    private void bindSpeakerDetails(java.sql.PreparedStatement statement, Speaker speaker,
                                    int firstIndex) throws SQLException {
        Sql.setText(statement, firstIndex, speaker.getFirstName());
        Sql.setText(statement, firstIndex + 1, speaker.getLastName());
        Sql.setText(statement, firstIndex + 2, speaker.getEmail());
        Sql.setText(statement, firstIndex + 3, speaker.getPhone());
        Sql.setText(statement, firstIndex + 4, speaker.getOrganisation());
        Sql.setText(statement, firstIndex + 5, speaker.getJobTitle());
        Sql.setText(statement, firstIndex + 6, speaker.getBiography());
        Sql.setText(statement, firstIndex + 7, speaker.getSessionTitle());
        Sql.setText(statement, firstIndex + 8, speaker.getSessionAbstract());
        Sql.setText(statement, firstIndex + 9, speaker.getRoomName());
        Sql.setDateTime(statement, firstIndex + 10, speaker.getSessionStart());
        Sql.setDateTime(statement, firstIndex + 11, speaker.getSessionEnd());
        Sql.setMoney(statement, firstIndex + 12, speaker.getFeeAgreed());
        Sql.setMoney(statement, firstIndex + 13, speaker.getTravelBudget());
        Sql.setText(statement, firstIndex + 14, speaker.getTravelNotes());
        Sql.setText(statement, firstIndex + 15, speaker.getDietaryNotes());
        Sql.setText(statement, firstIndex + 16, speaker.getAccessibilityNotes());
        Sql.setFlag(statement, firstIndex + 17, speaker.needsAccommodation());
        Sql.setText(statement, firstIndex + 18, speaker.getAccommodationNotes());
        Sql.setText(statement, firstIndex + 19, speaker.getPhotoPath());
        Sql.setText(statement, firstIndex + 20, speaker.getStatus().name());
    }
    /** Every speaker on an event's programme. */
    public List<Speaker> findSpeakers(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + SPEAKER_COLUMNS
                        + " FROM speakers WHERE event_id=? ORDER BY session_start, last_name",
                statement -> Sql.setText(statement, 1, eventId), PeopleStore::readSpeaker);
    }
    /** How many speakers an event has. */
    public int countSpeakers(String eventId) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM speakers WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many speakers are in a given booking state. */
    public int countSpeakersByStatus(String eventId, SpeakerStatus status) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM speakers WHERE event_id=? AND status=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, status.name());
                });
    }

    /** What the confirmed speakers on an event will cost in fees. */
    public java.math.BigDecimal sumSpeakerFees(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT SUM(fee) FROM speakers WHERE event_id=?"
                        + " AND status IN ('CONFIRMED','CONTRACTED')",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** What is set aside for speakers' travel. */
    public java.math.BigDecimal sumSpeakerTravelBudget(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(travel_budget) FROM speakers WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /**
     * Speakers with a confirmed place but no slot in the programme.
     *
     * <p>The most common last-minute gap, so it gets its own query rather than being
     * filtered in the interface. A missing time and a missing room are both gaps, so
     * either one qualifies: a speaker with a time but nowhere to stand is as much a
     * hole as one with no time at all.
     */
    public List<Speaker> findConfirmedWithoutSession(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + SPEAKER_COLUMNS + " FROM speakers WHERE event_id=?"
                        + " AND status IN ('CONFIRMED','CONTRACTED')"
                        + " AND (session_start IS NULL OR room_name IS NULL OR room_name='')"
                        + " ORDER BY last_name",
                statement -> Sql.setText(statement, 1, eventId), PeopleStore::readSpeaker);
    }

    // ---------------------------------------------------------------- kit

    /** Inserts a kit item. */
    public void insertResource(ResourceItem item) throws SQLException {
        Sql.update(connection,
                "INSERT INTO resource_items (" + RESOURCE_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, item.getId());
                    Sql.setText(statement, 2, item.getName());
                    Sql.setText(statement, 3, item.getKind().name());
                    Sql.setInt(statement, 4, item.getQuantity());
                    Sql.setMoney(statement, 5, item.getUnitCost());
                    Sql.setMoney(statement, 6, item.getReplacementValue());
                    Sql.setText(statement, 7, item.getSupplier());
                    Sql.setText(statement, 8, item.getNotes());
                    Sql.setFlag(statement, 9, item.isHired());
                });
    }
    /** Every kit item, by name. */
    public List<ResourceItem> findAllResources() throws SQLException {
        return Sql.query(connection,
                "SELECT " + RESOURCE_COLUMNS + " FROM resource_items ORDER BY kind, name",
                null, PeopleStore::readResource);
    }
    /**
     * How many of a kit item are committed to bookings that have not been returned.
     *
     * <p>The availability figure depends on this, and it must exclude returned
     * bookings or stock would shrink permanently every time something came back.
     */
    public int countBookedOut(String resourceId) throws SQLException {
        return Sql.count(connection,
                "SELECT COALESCE(SUM(quantity),0) FROM resource_bookings"
                        + " WHERE resource_id=? AND returned=FALSE",
                statement -> Sql.setText(statement, 1, resourceId));
    }

    /** How many of a kit item are committed to a particular event. */
    public int countBookedForEvent(String resourceId, String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COALESCE(SUM(quantity),0) FROM resource_bookings"
                        + " WHERE resource_id=? AND event_id=? AND returned=FALSE",
                statement -> {
                    Sql.setText(statement, 1, resourceId);
                    Sql.setText(statement, 2, eventId);
                });
    }

    // ---------------------------------------------------------------- kit bookings

    /** Inserts a kit booking. */
    public void insertBooking(ResourceBooking booking) throws SQLException {
        Sql.update(connection,
                "INSERT INTO resource_bookings (" + BOOKING_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, booking.getId());
                    Sql.setText(statement, 2, booking.getEventId());
                    Sql.setText(statement, 3, booking.getResourceId());
                    Sql.setInt(statement, 4, booking.getQuantity());
                    Sql.setDateTime(statement, 5, booking.getFrom());
                    Sql.setDateTime(statement, 6, booking.getUntil());
                    Sql.setText(statement, 7, booking.getForPurpose());
                    Sql.setText(statement, 8, booking.getNotes());
                    Sql.setMoney(statement, 9, booking.getAgreedCost());
                    Sql.setFlag(statement, 10, booking.isReturned());
                    Sql.setFlag(statement, 11, booking.isDamaged());
                });
    }

    /** Rewrites a kit booking. */
    public void updateBooking(ResourceBooking booking) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE resource_bookings SET quantity=?, from_at=?, until_at=?, purpose=?,"
                        + " notes=?, cost=?, returned=?, damaged=? WHERE id=?",
                statement -> {
                    Sql.setInt(statement, 1, booking.getQuantity());
                    Sql.setDateTime(statement, 2, booking.getFrom());
                    Sql.setDateTime(statement, 3, booking.getUntil());
                    Sql.setText(statement, 4, booking.getForPurpose());
                    Sql.setText(statement, 5, booking.getNotes());
                    Sql.setMoney(statement, 6, booking.getAgreedCost());
                    Sql.setFlag(statement, 7, booking.isReturned());
                    Sql.setFlag(statement, 8, booking.isDamaged());
                    Sql.setText(statement, 9, booking.getId());
                });
        if (changed == 0) {
            throw new SQLException("No booking to update with id " + booking.getId());
        }
    }

    /** Every booking against a kit item, whether returned or not. */
    public List<ResourceBooking> findBookingsForResource(String resourceId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + BOOKING_COLUMNS
                        + " FROM resource_bookings WHERE resource_id=? ORDER BY from_at",
                statement -> Sql.setText(statement, 1, resourceId),
                PeopleStore::readBooking);
    }

    /** Every booking an event has made. */
    public List<ResourceBooking> findBookingsForEvent(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + BOOKING_COLUMNS
                        + " FROM resource_bookings WHERE event_id=? ORDER BY from_at",
                statement -> Sql.setText(statement, 1, eventId),
                PeopleStore::readBooking);
    }
    // ---------------------------------------------------------------- mapping

    private static Attendee readAttendee(java.sql.ResultSet results) throws SQLException {
        return new Attendee(
                Sql.text(results, "id"),
                Sql.text(results, "first_name"),
                Sql.text(results, "last_name"),
                Sql.text(results, "email"),
                Sql.text(results, "phone"),
                Sql.text(results, "organisation"),
                Sql.text(results, "job_title"),
                Sql.text(results, "dietary"),
                Sql.text(results, "accessibility"),
                Sql.flag(results, "consent_marketing"));
    }

    private static Registration readRegistration(java.sql.ResultSet results) throws SQLException {
        return new Registration(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "attendee_id"),
                Sql.text(results, "attendee_name"),
                Sql.text(results, "attendee_email"),
                Sql.text(results, "ticket_ref"),
                Sql.text(results, "referral"),
                Sql.money(results, "fee"),
                Sql.money(results, "fee_paid"),
                results.getTimestamp("registered_at") == null ? java.time.Instant.now()
                        : results.getTimestamp("registered_at").toInstant(),
                RegistrationStatus.fromLabel(Sql.text(results, "status")),
                Sql.text(results, "waitlist_pos"));
    }

    private static Speaker readSpeaker(java.sql.ResultSet results) throws SQLException {
        return new Speaker(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "first_name"),
                Sql.text(results, "last_name"),
                Sql.text(results, "email"),
                Sql.text(results, "phone"),
                Sql.text(results, "organisation"),
                Sql.text(results, "job_title"),
                Sql.text(results, "biography"),
                Sql.text(results, "session_title"),
                Sql.text(results, "session_abstract"),
                Sql.text(results, "room_name"),
                Sql.dateTime(results, "session_start"),
                Sql.dateTime(results, "session_end"),
                Sql.money(results, "fee"),
                Sql.money(results, "travel_budget"),
                Sql.text(results, "travel_notes"),
                Sql.text(results, "dietary"),
                Sql.text(results, "accessibility"),
                Sql.flag(results, "needs_hotel"),
                Sql.text(results, "hotel_notes"),
                Sql.text(results, "photo_path"),
                SpeakerStatus.fromLabel(Sql.text(results, "status")));
    }

    private static ResourceItem readResource(java.sql.ResultSet results) throws SQLException {
        return new ResourceItem(
                Sql.text(results, "id"),
                Sql.text(results, "name"),
                ResourceItem.Kind.fromLabel(Sql.text(results, "kind")),
                Sql.number(results, "quantity"),
                Sql.money(results, "unit_cost"),
                Sql.money(results, "replacement"),
                Sql.text(results, "supplier"),
                Sql.text(results, "notes"),
                Sql.flag(results, "hired"));
    }

    private static ResourceBooking readBooking(java.sql.ResultSet results) throws SQLException {
        ResourceBooking booking = new ResourceBooking(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "resource_id"),
                "",
                Sql.number(results, "quantity"),
                Sql.dateTime(results, "from_at"),
                Sql.dateTime(results, "until_at"),
                Sql.text(results, "purpose"),
                Sql.text(results, "notes"),
                Sql.money(results, "cost"));
        if (Sql.flag(results, "returned")) {
            booking.markReturned();
        }
        if (Sql.flag(results, "damaged")) {
            booking.markDamaged();
        }
        return booking;
    }
}
