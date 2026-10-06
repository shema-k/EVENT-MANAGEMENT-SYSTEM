package com.eventsuite.core;

/**
 * How big an event is, worked out from its capacity rather than chosen by hand.
 *
 * <p>Scale is derived on purpose. It decides which operational weight an event
 * carries: a workshop needs one room and a speaker list, a festival needs a
 * command centre, a perimeter and a crowd plan. Deriving it from capacity means
 * an event cannot be quietly mis-graded, and it means the same number drives the
 * dashboards and the checklist alike.
 */
public enum EventScale {

    MICRO("Micro", 0, 50),
    SMALL("Small", 50, 250),
    MEDIUM("Medium", 250, 1000),
    LARGE("Large", 1000, 5000),
    MAJOR("Major", 5000, Integer.MAX_VALUE);

    private final String label;
    private final int minimumCapacity;
    private final int upperCapacity;

    EventScale(String label, int minimumCapacity, int upperCapacity) {
        this.label = label;
        this.minimumCapacity = minimumCapacity;
        this.upperCapacity = upperCapacity;
    }

    public String getLabel() {
        return label;
    }
    /** The scale an event of this capacity falls into. */
    public static EventScale of(int capacity) {
        for (EventScale scale : values()) {
            if (capacity < scale.upperCapacity) {
                return scale;
            }
        }
        return MAJOR;
    }

    /**
     * Whether this scale needs the full operational apparatus.
     *
     * <p>The cut sits at a thousand people, which is roughly where a second check-in
     * station, a stewarding rota and an incident log start to be necessary rather
     * than optional.
     */
    public boolean needsCommandCentre() {
        return this == LARGE || this == MAJOR;
    }

    /** Whether the event needs a timed entry plan rather than a single door. */
    public boolean needsStaggeredEntry() {
        return this == MAJOR;
    }
    public static EventScale fromLabel(String text) {
        if (text == null) {
            return MICRO;
        }
        for (EventScale scale : values()) {
            if (scale.label.equalsIgnoreCase(text.trim()) || scale.name().equalsIgnoreCase(text.trim())) {
                return scale;
            }
        }
        return MICRO;
    }

    @Override
    public String toString() {
        return label;
    }
}
