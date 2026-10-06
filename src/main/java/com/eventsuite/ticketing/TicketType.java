package com.eventsuite.ticketing;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A tier of ticket an event sells: "Early Bird", "VIP", "Standard".
 *
 * <p>The tier holds the price and the allocation; the {@link Ticket} objects hold
 * the people. Keeping them apart is what makes capacity arithmetic possible
 * without counting rows, and it is what allows a tier to be repriced after
 * nothing in it has sold while being locked once it has.
 *
 * <p>Allocations may deliberately exceed the event's capacity for early-bird
 * tiers. Selling a hundred cheap tickets to create urgency and then selling fewer
 * full-price ones is ordinary practice, so the inventory check compares against
 * the tier's own quantity rather than against the room.
 */
public final class TicketType implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String eventId;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final int quantity;
    private final AccessTier accessTier;
    private final LocalDate salesOpen;
    private final LocalDate salesClose;
    private final boolean refundable;
    private final int sortOrder;

    public TicketType(String id,
                      String eventId,
                      String name,
                      String description,
                      BigDecimal price,
                      int quantity,
                      AccessTier accessTier,
                      LocalDate salesOpen,
                      LocalDate salesClose,
                      boolean refundable,
                      int sortOrder) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.price = Money.of(price);
        this.quantity = Math.max(0, quantity);
        this.accessTier = accessTier == null ? AccessTier.GENERAL : accessTier;
        this.salesOpen = salesOpen;
        this.salesClose = salesClose;
        this.refundable = refundable;
        this.sortOrder = sortOrder;
        if (salesOpen != null && salesClose != null && salesClose.isBefore(salesOpen)) {
            throw new IllegalArgumentException(
                    "Sales window for '" + name + "' closes before it opens");
        }
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

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /** How many of this tier may be sold. */
    public int getQuantity() {
        return quantity;
    }

    public AccessTier getAccessTier() {
        return accessTier;
    }

    public LocalDate getSalesOpen() {
        return salesOpen;
    }

    public LocalDate getSalesClose() {
        return salesClose;
    }

    public boolean isRefundable() {
        return refundable;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** Whether the tier is on sale on the given date. */
    public boolean isOnSaleAt(LocalDate date) {
        if (salesOpen != null && date.isBefore(salesOpen)) {
            return false;
        }
        return salesClose == null || !date.isAfter(salesClose);
    }

    /** Whether the window has not opened yet. */
    public boolean isUpcoming(LocalDate date) {
        return salesOpen != null && date.isBefore(salesOpen);
    }
    /** Whether a price above zero means money is taken at the point of sale. */
    public boolean isPaid() {
        return Money.isPositive(price);
    }

    /** Whether this tier is free, such as speakers, press or staff entry. */
    public boolean isFree() {
        return !isPaid();
    }
    /**
     * Whether this many tickets have been sold against an allocation.
     *
     * <p>Refunded tickets do not count: the seat is back on sale, and counting it
     * would permanently shrink the allocation by every cancellation.
     */
    public boolean isSoldOut(int soldCount) {
        return soldCount >= quantity;
    }

    /** How many are left, never below zero. */
    public int remaining(int soldCount) {
        return Math.max(0, quantity - soldCount);
    }

    /** What taking {@code soldCount} of these at this price brings in. */
    public BigDecimal revenueFor(int soldCount) {
        return Money.of(price.multiply(BigDecimal.valueOf(Math.max(0, soldCount))));
    }

    public String getPriceLabel() {
        return isFree() ? "Free" : Money.format(price);
    }

    @Override
    public String toString() {
        return getName() + " - " + getPriceLabel();
    }
}
