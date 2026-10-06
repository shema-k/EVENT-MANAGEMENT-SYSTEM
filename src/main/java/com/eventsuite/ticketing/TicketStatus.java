package com.eventsuite.ticketing;

/**
 * Where a ticket has got to.
 *
 * <p>Only two of these states move forward. A ticket is sold, and then it is used.
 * Everything in between — reserved, held, refunded — is a detour that either
 * returns to available or leaves for good, and saying so explicitly is what stops
 * a refund leaving a seat counted as occupied.
 */
public enum TicketStatus {

    /** Offered but not yet bought. Not a ticket; an intention to sell. */
    AVAILABLE("Available", false),

    /** Held for someone who has not paid yet. Expires without payment. */
    RESERVED("Reserved", false),

    /** Paid for and valid. */
    SOLD("Sold", true),

    /** The holder has arrived and been admitted. */
    CHECKED_IN("Checked in", true),

    /** Given back before use; the seat returns to the pool. */
    REFUNDED("Refunded", false),

    /** Withdrawn after use, for instance a chargeback or a dispute. */
    VOID("Void", false);

    private final String label;
    private final boolean countsAsSold;

    TicketStatus(String label, boolean countsAsSold) {
        this.label = label;
        this.countsAsSold = countsAsSold;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Whether a ticket in this state takes up part of the allocation.
     *
     * <p>This is the definition that keeps the numbers honest. A refunded ticket
     * does not, a checked-in ticket does, and a reserved ticket does not until the
     * money arrives.
     */
    public boolean countsAsSold() {
        return countsAsSold;
    }

    /** Whether this ticket admits its holder right now. */
    public boolean isAdmissible() {
        return this == SOLD || this == CHECKED_IN;
    }

    /** Whether the ticket is gone for good and cannot be reinstated. */
    public boolean isDead() {
        return this == VOID;
    }

    /** Whether the ticket can still become a sale. */
    public boolean isOpenForSale() {
        return this == AVAILABLE || this == RESERVED;
    }

    /**
     * Whether moving from this state to {@code next} is allowed.
     *
     * <p>Available to reserved is a hold, reserved to sold is payment, sold to
     * checked-in is the door. Going the other way is a refund or a cancellation,
     * and a checked-in ticket cannot be un-admitted because the person is already
     * inside.
     */
    public boolean canMoveTo(TicketStatus next) {
        if (next == null || next == this) {
            return false;
        }
        if (next == VOID) {
            // A void is always possible: disputes and chargebacks happen at any
            // point and must not be blocked by the ticket's current state.
            return !isDead();
        }
        switch (this) {
            case AVAILABLE:
                return next == RESERVED || next == SOLD;
            case RESERVED:
                return next == SOLD || next == AVAILABLE;
            case SOLD:
                return next == CHECKED_IN || next == REFUNDED;
            case CHECKED_IN:
                return next == VOID;
            default:
                return false;
        }
    }
    public static TicketStatus fromLabel(String text) {
        if (text == null) {
            return AVAILABLE;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (TicketStatus status : values()) {
            if (status.name().replace("_", "").equalsIgnoreCase(needle)) {
                return status;
            }
            if (status.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return status;
            }
        }
        return AVAILABLE;
    }

    @Override
    public String toString() {
        return label;
    }
}
