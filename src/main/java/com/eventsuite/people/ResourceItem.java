package com.eventsuite.people;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * A piece of kit, furniture or stock an event needs, and how much of it there is.
 *
 * <p>The quantity is the point. Discovering on the day that a 400-seat lecture
 * needs 400 chairs, 4 lecterns, 6 radio mics and a riser is the single most
 * common way an event goes wrong, and it is entirely preventable by keeping an
 * inventory with quantities against it.
 *
 * <p>Availability is derived, not stored. The unit cost is what the event will pay;
 * the replacement value is what it would cost to buy new, which is a different
 * question and matters for the insurance and end-of-event write-off.
 */
public final class ResourceItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Broad groupings, used to filter the kit list and to spot gaps. */
    public enum Kind {
        SEATING("Seating"),
        AV("Audio & visual"),
        STAGING("Staging"),
        LIGHTING("Lighting"),
        CATERING("Catering"),
        SIGNAGE("Signage & print"),
        SAFETY("Safety & security"),
        POWER("Power & cabling"),
        FURNITURE("Furniture"),
        OTHER("Other");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
        /** The kind with this name, or OTHER when it is not one of ours. */
        public static Kind fromLabel(String text) {
            if (text != null) {
                for (Kind kind : values()) {
                    if (kind.name().equalsIgnoreCase(text.trim())) {
                        return kind;
                    }
                }
            }
            return OTHER;
        }
    }

    private final String id;
    private final String name;
    private final Kind kind;
    private final int quantity;
    private final BigDecimal unitCost;
    private final BigDecimal replacementValue;
    private final String supplier;
    private final String notes;
    private final boolean hiredNotOwned;

    public ResourceItem(String id,
                        String name,
                        Kind kind,
                        int quantity,
                        BigDecimal unitCost,
                        BigDecimal replacementValue,
                        String supplier,
                        String notes,
                        boolean hiredNotOwned) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = kind == null ? Kind.OTHER : kind;
        this.quantity = Math.max(0, quantity);
        this.unitCost = Money.of(unitCost);
        this.replacementValue = Money.of(replacementValue);
        this.supplier = supplier == null ? "" : supplier.trim();
        this.notes = notes == null ? "" : notes.trim();
        this.hiredNotOwned = hiredNotOwned;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    /** How many are in stock, total, before anything is booked out. */
    public int getQuantity() {
        return quantity;
    }

    /** What one costs to hire or buy. */
    public BigDecimal getUnitCost() {
        return unitCost;
    }

    /** What one would cost to replace. Zero means it is not a tracked asset. */
    public BigDecimal getReplacementValue() {
        return replacementValue;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getNotes() {
        return notes;
    }

    /** Whether this is hired in for the event rather than owned. */
    public boolean isHired() {
        return hiredNotOwned;
    }

    /** What all of them cost to hire or buy. */
    public BigDecimal getTotalCost() {
        return Money.of(unitCost.multiply(BigDecimal.valueOf(quantity)));
    }
    /**
     * How many are free once {@code bookedOut} are committed elsewhere.
     *
     * <p>Clamped at zero rather than going negative, because a negative availability
     * is not useful information: it means the booking is impossible, not that there
     * are minus three chairs.
     */
    public int available(int bookedOut) {
        return Math.max(0, quantity - Math.max(0, bookedOut));
    }

    /**
     * Whether {@code wanted} of these can still be booked once {@code alreadyBooked}
     * are committed elsewhere.
     *
     * <p>Zero is rejected rather than allowed. Booking nothing is always a mistake,
     * and letting it through creates a row that occupies a slot in the schedule
     * while reserving no stock at all.
     */
    public boolean canBook(int wanted, int alreadyBooked) {
        return wanted > 0 && available(alreadyBooked) >= wanted;
    }
    /** A copy with a different quantity in stock. */
    public ResourceItem withQuantity(int newQuantity) {
        return new ResourceItem(id, name, kind, newQuantity, unitCost, replacementValue, supplier,
                notes, hiredNotOwned);
    }
    /** Whether this item matches a typed search. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return name.toLowerCase(Locale.ROOT).contains(needle)
                || supplier.toLowerCase(Locale.ROOT).contains(needle)
                || kind.name().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** A line for the kit list. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(getQuantity() + " x " + getName());
        line.append("  (").append(getKind().getLabel()).append(")");
        if (!supplier.isEmpty()) {
            line.append("  from ").append(supplier);
        }
        if (unitCost.signum() > 0) {
            line.append("  ").append(Money.format(getTotalCost()));
        }
        return line.toString();
    }

    @Override
    public String toString() {
        return name;
    }
}
