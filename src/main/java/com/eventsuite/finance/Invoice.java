package com.eventsuite.finance;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A bill sent to somebody: a sponsor's invoice, or an attendee's receipt.
 *
 * <p>Invoices exist for the two cases where money and goods are separated in time.
 * A ticket buyer pays and receives in the same moment and needs a receipt, not an
 * invoice; a sponsor pays three weeks after the event and needs a document with
 * payment terms on it. Both are the same record, because the reconciliation is the
 * same work: what was invoiced, what has been paid, what is still owed.
 *
 * <p>The subtotal, tax and total are stored as they were calculated rather than
 * recomputed on demand. An invoice is a historical document; if the tax rate
 * changes next year, last year's issued invoice must still add up to the figure
 * that was actually demanded.
 */
public final class Invoice implements Serializable {
    private static final long serialVersionUID = 1L;

    /** One line on an invoice. */
    public static final class Line implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String description;
        private final BigDecimal quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal taxRate;
        private final BudgetCategory category;

        public Line(String description,
                    BigDecimal quantity,
                    BigDecimal unitPrice,
                    BigDecimal taxRate,
                    BudgetCategory category) {
            this.description = Objects.requireNonNull(description, "description");
            this.quantity = Money.of(quantity).max(BigDecimal.ZERO);
            this.unitPrice = Money.of(unitPrice);
            this.taxRate = Money.of(taxRate).max(BigDecimal.ZERO);
            this.category = category;
        }

        public String getDescription() {
            return description;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        /** The tax rate as a percentage, so 20 for twenty per cent. */
        public BigDecimal getTaxRate() {
            return taxRate;
        }

        public BudgetCategory getCategory() {
            return category;
        }

        /** The line total before tax. */
        public BigDecimal getAmount() {
            return Money.of(unitPrice.multiply(quantity));
        }

        /** The tax on this line. */
        public BigDecimal getTaxAmount() {
            if (taxRate.signum() == 0) {
                return Money.ZERO;
            }
            return Money.percentOf(getAmount(), taxRate);
        }

        /** The line total including tax. */
        public BigDecimal getTotal() {
            return Money.sum(getAmount(), getTaxAmount());
        }

        public String getDisplayLine() {
            StringBuilder line = new StringBuilder(getDescription());
            line.append("  x").append(quantity.stripTrailingZeros().toPlainString());
            line.append(" @ ").append(Money.format(unitPrice));
            if (taxRate.signum() > 0) {
                line.append("  +").append(taxRate.stripTrailingZeros().toPlainString()).append("% tax");
            }
            line.append("  = ").append(Money.format(getTotal()));
            return line.toString();
        }

        @Override
        public String toString() {
            return getDisplayLine();
        }
    }

    private final String id;
    private final String eventId;
    private final String number;
    private final String billedTo;
    private final String billedEmail;
    private final String billingAddress;
    private final String purchaseOrder;
    private final LocalDate issuedOn;
    private final LocalDate dueOn;
    private final String notes;
    private final ArrayList<Line> lines;
    private final BigDecimal subtotal;
    private final BigDecimal taxTotal;

    private InvoiceStatus status;
    private BigDecimal paidAmount;
    private Instant paidAt;

    public Invoice(String id,
                   String eventId,
                   String number,
                   String billedTo,
                   String billedEmail,
                   String billingAddress,
                   String purchaseOrder,
                   LocalDate issuedOn,
                   LocalDate dueOn,
                   String notes,
                   List<Line> lines,
                   BigDecimal paidAmount,
                   Instant paidAt,
                   InvoiceStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.number = Objects.requireNonNull(number, "number");
        this.billedTo = Objects.requireNonNull(billedTo, "billedTo");
        this.billedEmail = billedEmail == null ? "" : billedEmail;
        this.billingAddress = billingAddress == null ? "" : billingAddress;
        this.purchaseOrder = purchaseOrder == null ? "" : purchaseOrder;
        this.issuedOn = Objects.requireNonNull(issuedOn, "issuedOn");
        this.dueOn = dueOn == null ? issuedOn.plusDays(30) : dueOn;
        this.notes = notes == null ? "" : notes;
        this.lines = new ArrayList<>(lines == null ? List.of() : lines);
        this.subtotal = recalculateSubtotal();
        this.taxTotal = recalculateTax();
        this.paidAmount = Money.of(paidAmount);
        this.paidAt = paidAt;
        this.status = status == null ? InvoiceStatus.DRAFT : status;
    }

    /** An invoice with no lines, ready to have some added. */
    public static Invoice draft(String id, String eventId, String number, String billedTo,
                                String billedEmail, LocalDate issuedOn, LocalDate dueOn) {
        return new Invoice(id, eventId, number, billedTo, billedEmail, "", "", issuedOn, dueOn, "",
                List.of(), Money.ZERO, null, InvoiceStatus.DRAFT);
    }

    private BigDecimal recalculateSubtotal() {
        BigDecimal total = Money.ZERO;
        for (Line line : lines) {
            total = Money.sum(total, line.getAmount());
        }
        return total;
    }

    private BigDecimal recalculateTax() {
        BigDecimal total = Money.ZERO;
        for (Line line : lines) {
            total = Money.sum(total, line.getTaxAmount());
        }
        return total;
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    /** The printed invoice number, such as INV-2026-014. */
    public String getNumber() {
        return number;
    }

    public String getBilledTo() {
        return billedTo;
    }

    public String getBilledEmail() {
        return billedEmail;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    /** The buyer's own reference, which their accounts department will ask for. */
    public String getPurchaseOrder() {
        return purchaseOrder;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public String getNotes() {
        return notes;
    }

    public List<Line> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    /** What the payer owes in total, tax included. */
    public BigDecimal getTotal() {
        return Money.sum(subtotal, taxTotal);
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    /** What is still owed on this invoice. Never negative. */
    public BigDecimal getOutstanding() {
        BigDecimal due = Money.subtract(getTotal(), paidAmount);
        return due.signum() < 0 ? Money.ZERO : due;
    }

    /** Whether the invoice has been paid in full. */
    public boolean isPaid() {
        return getOutstanding().signum() == 0 && getTotal().signum() > 0;
    }

    /** Whether some but not all of it has been paid. */
    public boolean isPartPaid() {
        return Money.isPositive(paidAmount) && getOutstanding().signum() > 0;
    }

    /** Whether money is outstanding. */
    public boolean isOutstanding() {
        return getOutstanding().signum() > 0;
    }

    /** How many days the payer has been given, or has already had. */
    public long getPaymentTermDays() {
        return ChronoUnit.DAYS.between(issuedOn, dueOn);
    }

    /** How many days late the payment is, zero if it is not late. */
    public long getDaysOverdue(LocalDate today) {
        if (!isOutstanding()) {
            return 0;
        }
        long days = getStatus().getDaysOverdue(today, dueOn);
        if (days == 0) {
            // An invoice whose due date has passed but whose status has not been
            // updated still reports as late. Status is bookkeeping, not truth.
            return ChronoUnit.DAYS.between(dueOn, today) > 0
                    ? ChronoUnit.DAYS.between(dueOn, today) : 0;
        }
        return days;
    }

    /** Whether the invoice is past its due date with money outstanding. */
    public boolean isOverdue(LocalDate today) {
        return isOutstanding() && today.isAfter(dueOn);
    }

    /** A copy with a line added. */
    public Invoice withLine(Line line) {
        ArrayList<Line> combined = new ArrayList<>(lines);
        combined.add(Objects.requireNonNull(line, "line"));
        return new Invoice(id, eventId, number, billedTo, billedEmail, billingAddress,
                purchaseOrder, issuedOn, dueOn, notes, combined, paidAmount, paidAt, status);
    }

    /** A copy at a new status, used when it is issued or paid. */
    public Invoice withStatus(InvoiceStatus newStatus) {
        return new Invoice(id, eventId, number, billedTo, billedEmail, billingAddress,
                purchaseOrder, issuedOn, dueOn, notes, lines, paidAmount, paidAt, newStatus);
    }

    /** A copy recording money received against it. */
    public Invoice withPayment(BigDecimal amount, Instant when) {
        BigDecimal combined = Money.sum(paidAmount, amount);
        InvoiceStatus nextStatus = status;
        if (Money.subtract(getTotal(), combined).signum() == 0 && getTotal().signum() > 0) {
            nextStatus = InvoiceStatus.PAID;
        } else if (combined.signum() > 0) {
            nextStatus = InvoiceStatus.PART_PAID;
        }
        return new Invoice(id, eventId, number, billedTo, billedEmail, billingAddress,
                purchaseOrder, issuedOn, dueOn, notes, lines, combined, when, nextStatus);
    }

    /**
     * Moves the invoice to a new state.
     *
     * @throws IllegalStateException when the transition is not allowed
     */
    public void moveTo(InvoiceStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Invoice " + number + " cannot go from "
                    + status.getLabel() + " to " + (next == null ? "nothing" : next.getLabel()));
        }
        status = next;
    }

    /** Records money received. */
    public void recordPayment(BigDecimal amount, Instant when) {
        paidAmount = Money.sum(paidAmount, amount);
        if (Money.subtract(getTotal(), paidAmount).signum() == 0 && getTotal().signum() > 0) {
            status = InvoiceStatus.PAID;
            paidAt = when;
        } else if (paidAmount.signum() > 0) {
            status = InvoiceStatus.PART_PAID;
        }
    }
    /** Restores a state read back from the database. */
    public void restoreStatus(InvoiceStatus restored) {
        this.status = restored == null ? InvoiceStatus.DRAFT : restored;
    }
    /** A printable invoice. */
    public String render() {
        StringBuilder text = new StringBuilder();
        text.append("INVOICE ").append(number).append('\n');
        text.append("Event reference: ").append(eventId).append('\n');
        text.append("Billed to: ").append(billedTo).append('\n');
        if (!billedEmail.isEmpty()) {
            text.append("           ").append(billedEmail).append('\n');
        }
        if (!billingAddress.isEmpty()) {
            text.append("           ").append(billingAddress).append('\n');
        }
        if (!purchaseOrder.isEmpty()) {
            text.append("Your reference: ").append(purchaseOrder).append('\n');
        }
        text.append("Issued: ").append(issuedOn).append('\n');
        text.append("Due: ").append(dueOn).append('\n');
        text.append('\n');
        for (Line line : lines) {
            text.append("  ").append(line.getDisplayLine()).append('\n');
        }
        text.append('\n');
        text.append("Subtotal:  ").append(Money.format(subtotal)).append('\n');
        if (taxTotal.signum() > 0) {
            text.append("Tax:       ").append(Money.format(taxTotal)).append('\n');
        }
        text.append("TOTAL:     ").append(Money.format(getTotal())).append('\n');
        text.append("Paid:      ").append(Money.format(paidAmount)).append('\n');
        text.append("Outstand:  ").append(Money.format(getOutstanding())).append('\n');
        if (!notes.isEmpty()) {
            text.append('\n').append(notes).append('\n');
        }
        return text.toString();
    }

    /** A one-line summary for the finance list. */
    public String getDisplayLine() {
        return number + "  " + billedTo + "  " + Money.format(getTotal())
                + "  " + status.getLabel();
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
