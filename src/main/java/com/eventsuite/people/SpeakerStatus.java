package com.eventsuite.people;

import java.util.Locale;

/**
 * Where a speaker is in the process of being booked.
 *
 * <p>Speaking is a negotiation that takes weeks, so the states track the
 * conversation rather than the day. Knowing that half your lineup is still at
 * "approached" three weeks out is the single most useful thing a conference
 * dashboard can say, and it cannot be derived from whether they turned up.
 */
public enum SpeakerStatus {

    /** Suggested or nominated, not yet asked. */
    PROSPECT("Prospect", false, false, false),

    /** Asked, waiting to hear back. */
    APPROACHED("Approached", false, false, false),

    /** Said yes, not yet contracted. */
    CONFIRMED("Confirmed", true, false, false),

    /** Contracted and paid, or scheduled to be. */
    CONTRACTED("Contracted", true, true, false),

    /** Booked, but has withdrawn. */
    WITHDRAWN("Withdrawn", false, false, true),

    /** Declined. Kept so they are not asked again next year. */
    DECLINED("Declined", false, false, true);

    private final String label;
    private final boolean confirmed;
    private final boolean costed;
    private final boolean closed;

    SpeakerStatus(String label, boolean confirmed, boolean costed, boolean closed) {
        this.label = label;
        this.confirmed = confirmed;
        this.costed = costed;
        this.closed = closed;
    }

    public String getLabel() {
        return label;
    }

    /** Whether they are expected on the programme. */
    public boolean isConfirmed() {
        return confirmed;
    }
    /**
     * Whether this is a final state.
     *
     * <p>Closed speakers still count on the historical record, which is how the
     * system remembers who has already said no.
     */
    public boolean isClosed() {
        return closed;
    }
    /** Whether this state may move to {@code next}. */
    public boolean canMoveTo(SpeakerStatus next) {
        if (next == null || next == this) {
            return false;
        }
        if (isClosed()) {
            // Someone who declined can be asked again next year, so re-opening is
            // allowed. Someone who withdrew is also worth re-approaching.
            return next == PROSPECT || next == APPROACHED;
        }
        switch (this) {
            case PROSPECT:
                return next == APPROACHED || next == DECLINED;
            case APPROACHED:
                return next == CONFIRMED || next == DECLINED;
            case CONFIRMED:
                return next == CONTRACTED || next == WITHDRAWN;
            case CONTRACTED:
                return next == WITHDRAWN;
            default:
                return false;
        }
    }

    public static SpeakerStatus fromLabel(String text) {
        if (text == null) {
            return PROSPECT;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (SpeakerStatus status : values()) {
            if (status.name().replace("_", "").equalsIgnoreCase(needle)) {
                return status;
            }
            if (status.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return status;
            }
        }
        return PROSPECT;
    }

    @Override
    public String toString() {
        return label;
    }
}
