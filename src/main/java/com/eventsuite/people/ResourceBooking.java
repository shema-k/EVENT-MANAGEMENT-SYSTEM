package com.eventsuite.people;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Some of a {@link ResourceItem} reserved for one event, for a window.
 *
 * <p>Bookings are what make stock finite. Without them every screen would report
 * the full quantity and two events in the same week would both believe they had
 * forty chairs. A booking is not a transfer of ownership: the kit goes back, which
 * is why the window has an end and why the stock is never decremented permanently.
 */
public final class ResourceBooking implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String eventId;
    private final String resourceId;
    private final String resourceName;
    private final int quantity;
    private final LocalDateTime from;
    private final LocalDateTime until;
    private final String forPurpose;
    private final String notes;
    private final BigDecimal agreedCost;

    private boolean returned;
    private boolean damaged;

    public ResourceBooking(String id,
                           String eventId,
                           String resourceId,
                           String resourceName,
                           int quantity,
                           LocalDateTime from,
                           LocalDateTime until,
                           String forPurpose,
                           String notes,
                           BigDecimal agreedCost) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.resourceName = resourceName == null ? "" : resourceName;
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Booking must be for at least one " + getResourceName());
        }
        this.quantity = quantity;
        this.from = from;
        this.until = until;
        this.forPurpose = forPurpose == null ? "" : forPurpose;
        this.notes = notes == null ? "" : notes;
        this.agreedCost = Money.of(agreedCost);
        if (from != null && until != null && until.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Booking of " + getResourceName() + " ends before it starts");
        }
    }

    /** A booking for a whole event day. */
    public static ResourceBooking forDay(String id, String eventId, ResourceItem item,
                                         int quantity, LocalDateTime from, LocalDateTime until,
                                         String purpose, BigDecimal cost) {
        return new ResourceBooking(id, eventId, item.getId(), item.getName(), quantity, from, until,
                purpose, "", cost);
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public LocalDateTime getUntil() {
        return until;
    }

    /** What it is for: "main stage", "registration desk", and so on. */
    public String getForPurpose() {
        return forPurpose;
    }

    public String getNotes() {
        return notes;
    }

    /** What was agreed to pay for it, whether hired or a write-down. */
    public BigDecimal getAgreedCost() {
        return agreedCost;
    }

    /** Whether the kit has come back. */
    public boolean isReturned() {
        return returned;
    }

    /** Whether anything came back broken. */
    public boolean isDamaged() {
        return damaged;
    }

    /** Whether the booking still holds stock, which stops when it is returned. */
    public boolean isActive() {
        return !returned;
    }
    /**
     * Whether this booking collides with another.
     *
     * <p>Windows that touch at an endpoint do not collide: a chair booked until
     * 17:00 and another from 17:00 is a handover, not a double booking.
     */
    public boolean clashesWith(ResourceBooking other) {
        if (other == null || !getResourceId().equals(other.getResourceId())
                || !getEventId().equals(other.getEventId())) {
            return false;
        }
        if (from == null || until == null || other.from == null || other.until == null) {
            // With no window recorded, assume the kit is committed for the whole day
            // and treat any other open-ended booking as a collision.
            return true;
        }
        return from.isBefore(other.until) && other.from.isBefore(until);
    }

    /** Records that the kit has come back intact. */
    public void markReturned() {
        returned = true;
    }

    /** Records that the kit has come back damaged, for the write-off. */
    public void markDamaged() {
        damaged = true;
    }
    /** A line for the kit booking list. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(quantity + " x " + getResourceName());
        if (!forPurpose.isEmpty()) {
            line.append("  for ").append(forPurpose);
        }
        if (agreedCost.signum() > 0) {
            line.append("  ").append(Money.format(agreedCost));
        }
        if (returned) {
            line.append("  [returned");
            if (damaged) {
                line.append(", damaged");
            }
            line.append("]");
        }
        return line.toString();
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
