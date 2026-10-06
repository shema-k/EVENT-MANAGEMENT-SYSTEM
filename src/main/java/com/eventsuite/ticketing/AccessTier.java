package com.eventsuite.ticketing;

import java.util.Locale;

/**
 * What a ticket gets its holder into, and how much of the event they see.
 *
 * <p>Access is a property of the ticket rather than of the person, because one
 * person can legitimately hold several: a speaker who also buys a guest seat, a
 * sponsor with two passes and a staff wristband. Checking the door against the
 * ticket is therefore the only consistent rule, and it means a speaker who turns
 * up with a standard ticket is turned away to the right place rather than being
 * waved through.
 */
public enum AccessTier {

    /** Ordinary admission, no special areas. */
    GENERAL("General", 0, false),

    /** Early access, reserved seating or a better view. */
    PREMIUM("Premium", 1, false),

    /** Front of house, lounge, or a meal included. */
    VIP("VIP", 2, true),

    /** On the programme, with access to the green room and the speaker area. */
    SPEAKER("Speaker", 3, true),

    /** Organisers and crew, who need the areas staff use. */
    STAFF("Staff", 3, true),

    /** Press and photographers, who need the media point. */
    PRESS("Press", 2, true),

    /** A guest of a speaker or sponsor, who inherits their host's access. */
    COMPANION("Companion", 1, false);

    private final String label;
    private final int rank;
    private final boolean restrictedAreas;

    AccessTier(String label, int rank, boolean restrictedAreas) {
        this.label = label;
        this.rank = rank;
        this.restrictedAreas = restrictedAreas;
    }

    public String getLabel() {
        return label;
    }
    /** Whether this tier may pass a door marked as restricted. */
    public boolean grantsRestrictedAreas() {
        return restrictedAreas;
    }

    /** Whether the holder may bring someone in on the same ticket. */
    public boolean allowsPlusOne() {
        return this == VIP || this == SPEAKER || this == COMPANION;
    }

    /** Whether the tier is held back for organisers rather than sold. */
    public boolean isComplimentary() {
        return this == SPEAKER || this == STAFF || this == PRESS;
    }

    /**
     * Whether a holder of {@code mine} may use a door that {@code required} opens.
     *
     * <p>Ranking is compared, so a VIP can use a premium door but not the other way
     * round. Restricted areas additionally require the tier to grant them, which
     * stops a standard ticket from opening the green room by outranking nothing.
     */
    public boolean canOpen(AccessTier required) {
        if (required == null || required == GENERAL) {
            return true;
        }
        if (required.grantsRestrictedAreas()) {
            return grantsRestrictedAreas() && rank >= required.rank;
        }
        return rank >= required.rank;
    }

    public static AccessTier fromLabel(String text) {
        if (text == null) {
            return GENERAL;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (AccessTier tier : values()) {
            if (tier.name().replace("_", "").equalsIgnoreCase(needle)) {
                return tier;
            }
            if (tier.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return tier;
            }
        }
        return GENERAL;
    }

    @Override
    public String toString() {
        return label;
    }
}
