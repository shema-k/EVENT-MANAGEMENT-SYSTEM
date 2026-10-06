package com.eventsuite.ops;

import com.eventsuite.ticketing.TicketStatus;

/**
 * What happened when somebody reached the door.
 *
 * <p>Recording only successful admissions would leave no way to answer the two
 * questions that matter on the night: how many people tried to get in and could
 * not, and why. A rejected scan is the signal that the door list is wrong, that a
 * sponsor has under-allocated, or that somebody is being moved between sessions.
 * All three are worth seeing, so a rejection is a record rather than a non-event.
 */
public enum CheckInOutcome {

    /** Admitted against a valid ticket. */
    ADMITTED("Admitted", true, true),

    /** Admitted, but the ticket itself did not cover this door. */
    WRONG_DOOR("Wrong door", true, true),

    /** No ticket found at all. Admitted anyway, as a courtesy or a correction. */
    NO_TICKET("No ticket", true, true),

    /** Refused: the ticket was already used. */
    DUPLICATE("Duplicate ticket", false, false),

    /** Refused: the ticket is valid but for a different event or date. */
    WRONG_EVENT("Wrong event", false, false),

    /** Refused: the ticket was refunded or void. */
    INVALID("Invalid ticket", false, false),

    /** Refused: the ticket is not valid yet, or the sale window has closed. */
    NOT_YET_VALID("Not yet valid", false, false),

    /** Refused: the event is at capacity. */
    AT_CAPACITY("Event full", false, false);

    private final String label;
    private final boolean admitted;
    private final boolean countsAsAttendance;
    private final boolean needsAttention;

    CheckInOutcome(String label, boolean admitted, boolean countsAsAttendance) {
        this.label = label;
        this.admitted = admitted;
        this.countsAsAttendance = countsAsAttendance;
        this.needsAttention = !admitted;
    }

    public String getLabel() {
        return label;
    }

    /** Whether the person got in. */
    public boolean isAdmitted() {
        return admitted;
    }

    /** Whether this counts towards the attendance figure. */
    public boolean countsAsAttendance() {
        return countsAsAttendance;
    }
    /** Whether this outcome follows from the ticket being in {@code status}. */
    public boolean matches(TicketStatus status) {
        if (status == null) {
            return this == INVALID;
        }
        return switch (this) {
            case ADMITTED, WRONG_DOOR -> status.isAdmissible();
            case DUPLICATE -> status == TicketStatus.CHECKED_IN;
            case INVALID -> status == TicketStatus.REFUNDED || status == TicketStatus.VOID;
            case NOT_YET_VALID -> status == TicketStatus.RESERVED;
            default -> false;
        };
    }

    /** The message shown to the person at the desk. */
    public String getMessage() {
        return switch (this) {
            case ADMITTED -> "Welcome in.";
            case WRONG_DOOR -> "Admitted, but this door needs a different ticket type.";
            case NO_TICKET -> "Admitted without a ticket. Please collect one inside.";
            case DUPLICATE -> "This ticket has already been used to enter.";
            case WRONG_EVENT -> "This ticket is for a different event.";
            case INVALID -> "This ticket was refunded or cancelled.";
            case NOT_YET_VALID -> "This ticket has not been paid for yet.";
            case AT_CAPACITY -> "The event is at capacity.";
        };
    }

    public static CheckInOutcome fromLabel(String text) {
        if (text == null) {
            return INVALID;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (CheckInOutcome outcome : values()) {
            if (outcome.name().replace("_", "").equalsIgnoreCase(needle)) {
                return outcome;
            }
            if (outcome.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return outcome;
            }
        }
        return INVALID;
    }

    @Override
    public String toString() {
        return label;
    }
}
