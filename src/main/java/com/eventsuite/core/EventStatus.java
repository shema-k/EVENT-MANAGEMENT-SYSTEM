package com.eventsuite.core;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where an event sits between "an idea" and "a memory".
 *
 * <p>The lifecycle is enforced rather than decorative. Money cannot be taken for
 * a draft event, check-in desks cannot open for something that has not been
 * published, and a completed event cannot quietly go back on sale. Encoding that
 * here means one rule holds for every screen instead of a check in each of them.
 */
public enum EventStatus {

    /** Being written up. Nothing is for sale and nothing is published. */
    DRAFT("Draft", false, false),

    /** Details agreed, budget set, suppliers booked, but not announced yet. */
    PLANNING("Planning", false, false),

    /** Announced and on the website, but tickets have not opened. */
    PUBLISHED("Published", false, false),

    /** Tickets are on sale and revenue is being taken. */
    ON_SALE("On sale", true, false),

    /** Capacity reached. Sales stay closed but the event is still live. */
    SOLD_OUT("Sold out", false, false),

    /** Happening now. Check-in is open. */
    IN_PROGRESS("In progress", false, true),

    /** Finished. Analytics, feedback and reconciliation belong to this state. */
    COMPLETED("Completed", false, false),

    /** Called off. Any money held has to be refunded. */
    CANCELLED("Cancelled", false, false);

    private final String label;
    private final boolean takesMoney;
    private final boolean live;

    EventStatus(String label, boolean takesMoney, boolean live) {
        this.label = label;
        this.takesMoney = takesMoney;
        this.live = live;
    }

    public String getLabel() {
        return label;
    }

    /** Whether tickets and registration fees may be charged in this state. */
    public boolean acceptsSales() {
        return takesMoney;
    }
    /** Whether the event has finished and its results can be measured. */
    public boolean isSettled() {
        return this == COMPLETED || this == CANCELLED;
    }
    /**
     * Whether moving from this state to {@code next} is allowed.
     *
     * <p>Going backwards is allowed only while nothing has been sold, because
     * un-selling tickets is a refund and a refund is not a status change.
     */
    public boolean canMoveTo(EventStatus next) {
        if (next == null || next == this) {
            return false;
        }
        if (next == CANCELLED) {
            // Cancellation is always available: an event can be called off at any
            // point, and refusing it during a live event would be absurd.
            return true;
        }
        if (this == CANCELLED) {
            return false;
        }
        if (isSettled()) {
            return false;
        }
        Set<EventStatus> forward = EnumSet.noneOf(EventStatus.class);
        switch (this) {
            case DRAFT:
                forward = EnumSet.of(PLANNING, PUBLISHED);
                break;
            case PLANNING:
                forward = EnumSet.of(PUBLISHED);
                break;
            case PUBLISHED:
                forward = EnumSet.of(ON_SALE, PLANNING);
                break;
            case ON_SALE:
                forward = EnumSet.of(SOLD_OUT, PUBLISHED, IN_PROGRESS);
                break;
            case SOLD_OUT:
                forward = EnumSet.of(IN_PROGRESS);
                break;
            case IN_PROGRESS:
                forward = EnumSet.of(COMPLETED);
                break;
            default:
                forward = EnumSet.noneOf(EventStatus.class);
        }
        return forward.contains(next);
    }

    /** The states this one can move to, for populating a menu. */
    public Set<EventStatus> allowedMoves() {
        Set<EventStatus> allowed = EnumSet.noneOf(EventStatus.class);
        for (EventStatus candidate : values()) {
            if (canMoveTo(candidate)) {
                allowed.add(candidate);
            }
        }
        return allowed;
    }

    public static EventStatus fromLabel(String text) {
        if (text == null) {
            return DRAFT;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (EventStatus status : values()) {
            if (status.name().replace("_", "").equalsIgnoreCase(needle)) {
                return status;
            }
            if (status.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return status;
            }
        }
        return DRAFT;
    }

    @Override
    public String toString() {
        return label;
    }
}
