package com.eventsuite.ticketing;

import com.eventsuite.people.Attendee;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One ticket, held by one person for one event.
 *
 * <p>This is the physical thing. It carries the code printed on it, the tier it
 * was sold under, what it cost, and where it has got to. A {@link TicketType} says
 * what a VIP costs; a Ticket says that this particular person bought one.
 *
 * <p>The reference is what the door scans, so it is generated once and never
 * changed. It is deliberately longer than it needs to be for a person to type,
 * because it is read aloud across a noisy desk and misheard codes cost admissions.
 */
public final class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Characters used in a reference: no 0/O or 1/I, which get misheard. */
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final DateTimeFormatter PRINTED_AT =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.UK).withZone(ZoneId.systemDefault());

    private final String reference;
    private final String eventId;
    private final String ticketTypeId;
    private final String attendeeId;
    private final String attendeeName;
    private final String attendeeEmail;
    private final String tierName;
    private final AccessTier accessTier;
    private final String pricePaid;
    private final String discountCode;
    private final Instant issuedAt;
    private final String seatLabel;
    private final String notes;

    /**
     * When an unpaid hold lapses. Cleared once the ticket is sold, because a
     * deadline that no longer applies must not later release a paid seat.
     */
    private Instant holdExpiresAt;

    private TicketStatus status;
    private Instant checkedInAt;
    private String gate;
    private String checkedInBy;

    public Ticket(String reference,
                  String eventId,
                  String ticketTypeId,
                  String attendeeId,
                  String attendeeName,
                  String attendeeEmail,
                  String tierName,
                  AccessTier accessTier,
                  String pricePaid,
                  String discountCode,
                  Instant issuedAt,
                  Instant holdExpiresAt,
                  String seatLabel,
                  String notes) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.ticketTypeId = ticketTypeId == null ? "" : ticketTypeId;
        this.attendeeId = Objects.requireNonNull(attendeeId, "attendeeId");
        this.attendeeName = Objects.requireNonNull(attendeeName, "attendeeName");
        this.attendeeEmail = attendeeEmail == null ? "" : attendeeEmail;
        this.tierName = tierName == null ? "Standard" : tierName;
        this.accessTier = accessTier == null ? AccessTier.GENERAL : accessTier;
        this.pricePaid = pricePaid == null ? "0.00" : pricePaid;
        this.discountCode = discountCode == null ? "" : discountCode.trim().toUpperCase(Locale.ROOT);
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.holdExpiresAt = holdExpiresAt;
        this.seatLabel = seatLabel == null ? "" : seatLabel.trim();
        this.notes = notes == null ? "" : notes.trim();
        this.status = TicketStatus.AVAILABLE;
    }

    /** Convenience factory for a ticket being issued from a real attendee record. */
    public static Ticket issue(String reference,
                               String eventId,
                               TicketType type,
                               Attendee attendee,
                               String pricePaid,
                               String discountCode,
                               Instant issuedAt,
                               String seatLabel) {
        return new Ticket(reference, eventId, type.getId(), attendee.getId(),
                attendee.getFullName(), attendee.getEmail(), type.getName(), type.getAccessTier(),
                pricePaid, discountCode, issuedAt, null, seatLabel, "");
    }

    /**
     * Builds a reference of the requested length.
     *
     * <p>Uniqueness comes from length rather than from checking a table: at 10
     * characters there are 31^10 possibilities, so two tickets colliding is not a
     * thing that happens in practice, and a lookup on every sale is not needed to
     * prove it.
     */
    public static String newReference(int length) {
        int size = Math.max(6, Math.min(length, 24));
        java.util.random.RandomGenerator random = java.util.random.RandomGenerator.getDefault();
        StringBuilder code = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public static String newReference() {
        return newReference(10);
    }

    public String getReference() {
        return reference;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTicketTypeId() {
        return ticketTypeId;
    }

    public String getAttendeeId() {
        return attendeeId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public String getAttendeeEmail() {
        return attendeeEmail;
    }

    public String getTierName() {
        return tierName;
    }

    public AccessTier getAccessTier() {
        return accessTier;
    }

    /** The amount actually charged, as text, so it is stored exactly as paid. */
    public String getPricePaid() {
        return pricePaid;
    }

    public String getDiscountCode() {
        return discountCode;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    /** When an unpaid hold lapses, or null once the ticket is sold. */
    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    /** A seat, table or bay where the venue is seated. */
    public String getSeatLabel() {
        return seatLabel;
    }

    public String getNotes() {
        return notes;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    /** Which entrance or desk the holder used. */
    public String getGate() {
        return gate;
    }

    public String getCheckedInBy() {
        return checkedInBy;
    }

    public boolean isSold() {
        return status.countsAsSold();
    }

    public boolean isCheckedIn() {
        return status == TicketStatus.CHECKED_IN;
    }

    public boolean isFree() {
        return "0.00".equals(getPricePaid()) || "0".equals(getPricePaid());
    }

    /**
     * Moves the ticket to a new state.
     *
     * @throws IllegalStateException when the lifecycle does not allow the move
     */
    public void moveTo(TicketStatus next) {
        if (status.canMoveTo(next)) {
            status = next;
            if (next != TicketStatus.RESERVED) {
                // Once paid for, the hold no longer applies. Leaving it set would
                // let an expiry job later release a seat that was paid for.
                holdExpiresAt = null;
            }
        } else {
            throw new IllegalStateException(
                    "Ticket " + reference + " cannot go from " + status.getLabel()
                            + " to " + (next == null ? "nothing" : next.getLabel()));
        }
    }

    /** Records arrival at the door. */
    public void checkIn(Instant when, String atGate, String byWhom) {
        if (status == TicketStatus.SOLD) {
            status = TicketStatus.CHECKED_IN;
        } else if (status != TicketStatus.CHECKED_IN) {
            throw new IllegalStateException(
                    "Ticket " + reference + " is " + status.getLabel()
                            + ", so it cannot admit anyone");
        }
        this.checkedInAt = when;
        this.gate = atGate == null ? "" : atGate;
        this.checkedInBy = byWhom == null ? "" : byWhom;
    }

    /** Puts a paid ticket back on sale, returning its money. */
    public void refund() {
        if (status != TicketStatus.SOLD) {
            throw new IllegalStateException(
                    "Only a sold ticket can be refunded; " + reference + " is " + status.getLabel());
        }
        status = TicketStatus.REFUNDED;
        checkedInAt = null;
        gate = "";
        checkedInBy = "";
    }

    /** Whether an unpaid hold has passed its deadline. */
    public boolean isHoldExpired(Instant now) {
        return status == TicketStatus.RESERVED && holdExpiresAt != null && now.isAfter(holdExpiresAt);
    }

    /** Whether the ticket can admit its holder at the door right now. */
    public boolean admitsAt(Instant now) {
        return status.isAdmissible() && !isHoldExpired(now);
    }

    /** Whether this ticket lets its holder through a door needing {@code required}. */
    public boolean grants(AccessTier required) {
        return admitsAt(Instant.now()) && accessTier.canOpen(required);
    }

    /** Restores a state read back from the database. */
    public void restore(TicketStatus restored, Instant checkedIn, String atGate, String byWhom) {
        this.status = restored == null ? TicketStatus.AVAILABLE : restored;
        this.checkedInAt = checkedIn;
        this.gate = atGate == null ? "" : atGate;
        this.checkedInBy = byWhom == null ? "" : byWhom;
    }

    /** The arrival time in the room's local zone, for the door list. */
    public String getCheckedInLabel() {
        return checkedInAt == null ? "" : PRINTED_AT.format(checkedInAt);
    }

    /** The issue time, formatted for a receipt. */
    public String getIssuedLabel() {
        return PRINTED_AT.format(issuedAt);
    }

    /** Whether this ticket matches a typed code at the desk. */
    public boolean matchesCode(String typed) {
        if (typed == null) {
            return false;
        }
        String needle = typed.trim().toUpperCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return false;
        }
        if (needle.equals(reference)) {
            return true;
        }
        // Desk staff get the first few characters read off a badly printed ticket,
        // and a full scan is not always possible, so a unique prefix is accepted.
        return reference.startsWith(needle) && needle.length() >= 4;
    }

    /** The line a badge or receipt shows for this ticket. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(getTierName());
        line.append(" - ").append(getAttendeeName());
        if (!getSeatLabel().isEmpty()) {
            line.append(" - seat ").append(getSeatLabel());
        }
        return line.toString();
    }

    /** Convenience for building a ticket from just an event and an attendee. */
    public static Ticket forAttendee(String reference, String eventId, TicketType type,
                                     Attendee attendee, String pricePaid, Instant issuedAt) {
        return issue(reference, eventId, type, attendee, pricePaid, "", issuedAt, "");
    }

    /** Convenience for a ticket on the local clock. */
    public static Ticket forAttendee(String reference, String eventId, TicketType type,
                                     Attendee attendee, String pricePaid) {
        return forAttendee(reference, eventId, type, attendee, pricePaid, Instant.now());
    }

    /** The time a ticket was issued, converted for display. */
    public LocalDateTime getIssuedLocalTime() {
        return LocalDateTime.ofInstant(issuedAt, ZoneId.systemDefault());
    }

    @Override
    public String toString() {
        return reference + " " + getTierName() + " (" + getAttendeeName() + ")";
    }
}
