package com.eventsuite.ticketing;

/**
 * Whether someone has a place at an event.
 *
 * <p>A registration is the promise of a place; a ticket is the proof of payment.
 * They are tracked separately because a great many registrations never become
 * tickets. An invited speaker is registered and given a complimentary ticket, a
 * walk-up is registered and paid for at the desk, and somebody on the waiting list
 * is registered without either. Counting registrations as attendance would
 * overstate every number on the dashboards.
 */
public enum RegistrationStatus {

    /** Form started but not submitted. */
    PENDING("Pending", false, false),

    /** Has a place and is expected to attend. */
    CONFIRMED("Confirmed", true, false),

    /** No place available; waiting for a cancellation. */
    WAITLISTED("Waitlisted", false, false),

    /** Turned up on the day without booking, and was admitted. */
    WALK_IN("Walk-in", true, true),

    /** Gave up the place. */
    CANCELLED("Cancelled", false, false),

    /** Attended. Set at check-in, and never set by hand. */
    ATTENDED("Attended", true, true);

    private final String label;
    private final boolean holdsPlace;
    private final boolean countsAsAttendee;

    RegistrationStatus(String label, boolean holdsPlace, boolean countsAsAttendee) {
        this.label = label;
        this.holdsPlace = holdsPlace;
        this.countsAsAttendee = countsAsAttendee;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Whether this status takes up one of the event's places.
     *
     * <p>Waitlisted registrations do not, which is the whole point of a waiting
     * list: it must not consume capacity or the promotion would be pointless.
     */
    public boolean holdsPlace() {
        return holdsPlace;
    }
    /** Whether the person is still expected, so reminders are worth sending. */
    public boolean isActive() {
        return this != CANCELLED;
    }

    /**
     * Whether moving from this state to {@code next} is allowed.
     *
     * <p>Once somebody has attended, their record is history: it cannot be walked
     * in again or put back on the waiting list, because the seat was physically
     * used.
     */
    public boolean canMoveTo(RegistrationStatus next) {
        if (next == null || next == this) {
            return false;
        }
        if (next == CANCELLED) {
            // Giving up a place is always allowed, even after attending, since the
            // attendee may still need their record closed off.
            return true;
        }
        if (this == CANCELLED) {
            return false;
        }
        if (this == ATTENDED) {
            return false;
        }
        switch (this) {
            case PENDING:
                return next == CONFIRMED || next == WAITLISTED;
            case WAITLISTED:
                return next == CONFIRMED || next == CANCELLED;
            case CONFIRMED:
                return next == ATTENDED || next == WAITLISTED;
            default:
                return false;
        }
    }

    public static RegistrationStatus fromLabel(String text) {
        if (text == null) {
            return PENDING;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (RegistrationStatus status : values()) {
            if (status.name().replace("_", "").equalsIgnoreCase(needle)) {
                return status;
            }
            if (status.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return status;
            }
        }
        return PENDING;
    }

    @Override
    public String toString() {
        return label;
    }
}
