package com.eventsuite.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Whether an invoice is a draft, a demand, or money in the bank.
 *
 * <p>The distinction that matters is between an invoice that has been issued and
 * one that has been paid. Only a paid invoice is revenue, and an issued one is a
 * receivable. Counting them together is how an organiser ends up planning a
 * following-up event on the strength of a sponsorship they were never paid.
 */
public enum InvoiceStatus {

    /** Being put together. Not visible to the payer, not a receivable. */
    DRAFT("Draft", false, false),

    /** Sent and awaiting payment. A receivable. */
    ISSUED("Issued", false, true),

    /** Part paid. Still a receivable for the remainder. */
    PART_PAID("Part paid", false, true),

    /** Paid in full. Revenue. */
    PAID("Paid", true, false),

    /** Payment date passed with money still outstanding. */
    OVERDUE("Overdue", false, true),

    /** Withdrawn. A mistake, a cancellation, or a sponsor walking away. */
    VOID("Void", false, false);

    private final String label;
    private final boolean settled;
    private final boolean receivable;

    InvoiceStatus(String label, boolean settled, boolean receivable) {
        this.label = label;
        this.settled = settled;
        this.receivable = receivable;
    }

    public String getLabel() {
        return label;
    }

    /** Whether the invoice has been paid in full. */
    public boolean isSettled() {
        return settled;
    }

    /** Whether money is owed on this invoice. */
    public boolean isReceivable() {
        return receivable;
    }

    /** Whether this state may move to {@code next}. */
    public boolean canMoveTo(InvoiceStatus next) {
        if (next == null || next == this) {
            return false;
        }
        if (next == VOID) {
            // A draft can be thrown away, and so can an issued one if it turns out
            // to be wrong. A paid invoice cannot be voided, because the money moved.
            return this != PAID;
        }
        if (this == VOID || this == PAID) {
            return false;
        }
        if (next == PAID) {
            return true;
        }
        if (next == OVERDUE) {
            return this == ISSUED || this == PART_PAID;
        }
        if (next == ISSUED) {
            return this == DRAFT || this == OVERDUE;
        }
        return next == PART_PAID && (this == ISSUED || this == OVERDUE);
    }

    /** Whether the state should read as overdue given today's date and terms. */
    public boolean shouldBeOverdue(LocalDate today, LocalDate dueDate, boolean settled) {
        if (settled) {
            return false;
        }
        return (this == ISSUED || this == PART_PAID) && dueDate != null && today.isAfter(dueDate);
    }

    /** How many days late, or zero if it is not late. */
    public long getDaysOverdue(LocalDate today, LocalDate dueDate) {
        if (dueDate == null || !today.isAfter(dueDate)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, today);
    }

    public static InvoiceStatus fromLabel(String text) {
        if (text == null) {
            return DRAFT;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (InvoiceStatus status : values()) {
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
