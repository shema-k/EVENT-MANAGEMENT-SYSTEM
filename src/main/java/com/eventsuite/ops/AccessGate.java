package com.eventsuite.ops;

import com.eventsuite.ticketing.AccessTier;

/**
 * A doorway, and what passing through it needs.
 *
 * <p>Gates are separate from tickets because access is checked at boundaries, not
 * at the event. The main entrance admits anyone with a valid ticket; the VIP
 * lounge needs a rank; the green room needs a speaker, staff or press ticket; the
 * loading bay needs nothing from an attendee at all. Modelling each as a gate with
 * its own requirement is what lets the desk screen say "wrong door" instead of
 * simply "no".
 */
public final class AccessGate {

    private final String id;
    private final String eventId;
    private final String name;
    private final AccessTier requiredTier;
    private final boolean staffOnly;
    private final boolean countedForCapacity;

    /** Whether the door is currently usable. Opened and closed on the day. */
    private boolean open;

    public AccessGate(String id,
                      String eventId,
                      String name,
                      AccessTier requiredTier,
                      boolean staffOnly,
                      boolean countedForCapacity) {
        this.id = id == null ? "" : id;
        this.eventId = eventId == null ? "" : eventId;
        this.name = name == null || name.isBlank() ? "Entrance" : name.trim();
        this.requiredTier = requiredTier == null ? AccessTier.GENERAL : requiredTier;
        this.staffOnly = staffOnly;
        this.countedForCapacity = countedForCapacity;
        this.open = true;
    }

    /** The main door: open to any valid ticket. */
    public static AccessGate mainEntrance(String eventId) {
        return new AccessGate("gate-main", eventId, "Main entrance", AccessTier.GENERAL,
                false, true);
    }

    /** A VIP or lounge area. */
    public static AccessGate vipArea(String eventId, String name) {
        return new AccessGate("gate-vip", eventId, name, AccessTier.VIP, false, false);
    }

    /** Behind the scenes: green room, production, load-in. */
    public static AccessGate staffOnly(String eventId, String name) {
        return new AccessGate("gate-staff", eventId, name, AccessTier.STAFF, true, false);
    }

    /** Where press and photographers work. */
    public static AccessGate mediaPoint(String eventId) {
        return new AccessGate("gate-press", eventId, "Media point", AccessTier.PRESS, false, false);
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    /** The weakest ticket that gets through. */
    public AccessTier getRequiredTier() {
        return requiredTier;
    }
    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public void open() {
        open = true;
    }

    /**
     * Whether a ticket gets through this door.
     *
     * <p>A closed gate admits nobody regardless of ticket. That is what makes
     * closing the loading bay at the end of load-in enforceable rather than a note
     * in the running order.
     */
    public boolean admits(AccessTier held, boolean ticketValid) {
        if (!open || !ticketValid) {
            return false;
        }
        if (staffOnly) {
            return held == AccessTier.STAFF;
        }
        return held.canOpen(requiredTier);
    }

    /** A label for the access screen. */
    public String getRequirementLabel() {
        if (staffOnly) {
            return "Staff only";
        }
        if (requiredTier == AccessTier.GENERAL) {
            return "Any valid ticket";
        }
        return requiredTier.getLabel() + " and above";
    }

    @Override
    public String toString() {
        return getName();
    }
}
