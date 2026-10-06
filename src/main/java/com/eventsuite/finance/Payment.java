package com.eventsuite.finance;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One movement of money, in either direction.
 *
 * <p>This is the atom of the whole financial system. A ticket sale, a sponsor's
 * instalment, a supplier's bill and a refunded ticket are all the same shape: an
 * amount, a direction, a method, a counterparty and a settlement state. Keeping
 * them uniform is what makes reconciliation a fold over a list rather than a
 * reconciliation of four different tables.
 *
 * <p>A payment can point at several things at once, which is deliberate. The same
 * record is the sale, the receipt and the accounting entry: it carries the ticket
 * reference that proves what was bought, the registration it settles and the
 * invoice it pays off. Duplicating that into separate documents would let them
 * disagree.
 */
public final class Payment implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.UK);

    private final String id;
    private final String eventId;
    private final String reference;
    private final String counterparty;
    private final String description;
    private final TransactionDirection direction;
    private final PaymentMethod method;
    private final BigDecimal amount;
    private final BigDecimal providerFee;
    private final String category;
    private final String ticketReference;
    private final String attendeeId;
    private final String invoiceId;
    private final String note;
    private final Instant raisedAt;
    private final boolean vatApplicable;

    /** When the money arrived. Set when the payment settles, so not final. */
    private Instant settledAt;

    private PaymentStatus status;

    public Payment(String id,
                   String eventId,
                   String reference,
                   String counterparty,
                   String description,
                   TransactionDirection direction,
                   PaymentMethod method,
                   BigDecimal amount,
                   BigDecimal providerFee,
                   String category,
                   String ticketReference,
                   String attendeeId,
                   String invoiceId,
                   String note,
                   Instant raisedAt,
                   Instant settledAt,
                   boolean vatApplicable,
                   PaymentStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.reference = reference == null ? "" : reference.trim();
        this.counterparty = counterparty == null ? "" : counterparty.trim();
        this.description = description == null ? "" : description.trim();
        this.direction = direction == null ? TransactionDirection.INFLOW : direction;
        this.method = method == null ? PaymentMethod.OTHER : method;
        this.amount = Money.of(amount);
        this.providerFee = Money.of(providerFee);
        this.category = category == null ? "" : category.trim();
        this.ticketReference = ticketReference == null ? "" : ticketReference.trim();
        this.attendeeId = attendeeId == null ? "" : attendeeId.trim();
        this.invoiceId = invoiceId == null ? "" : invoiceId.trim();
        this.note = note == null ? "" : note.trim();
        this.raisedAt = Objects.requireNonNull(raisedAt, "raisedAt");
        this.settledAt = settledAt;
        this.vatApplicable = vatApplicable;
        this.status = status == null ? PaymentStatus.PENDING : status;
    }

    /** A settled sale, which is what almost every ticket purchase is. */
    public static Payment sale(String id,
                               String eventId,
                               String reference,
                               String payerName,
                               BigDecimal amount,
                               PaymentMethod method,
                               String ticketReference,
                               String attendeeId,
                               Instant at) {
        return new Payment(id, eventId, reference, payerName, "Ticket sale", TransactionDirection.INFLOW,
                method, amount, method.providerFeeOn(amount), "Tickets", ticketReference, attendeeId,
                "", "", at, at, false, PaymentStatus.SETTLED);
    }
    /** A bill being paid out, such as a supplier invoice. */
    public static Payment expense(String id,
                                  String eventId,
                                  String reference,
                                  String payee,
                                  String description,
                                  BigDecimal amount,
                                  PaymentMethod method,
                                  String category,
                                  String invoiceId,
                                  boolean vatApplicable,
                                  Instant at) {
        return new Payment(id, eventId, reference, payee, description, TransactionDirection.OUTFLOW,
                method, amount, Money.ZERO, category, "", "", invoiceId, "", at, null,
                vatApplicable, PaymentStatus.PENDING);
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    /** The human-facing receipt number. */
    public String getReference() {
        return reference;
    }

    /** Who paid, or who was paid. */
    public String getCounterparty() {
        return counterparty;
    }

    public String getDescription() {
        return description;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** What the card processor or transfer service deducted. */
    public BigDecimal getProviderFee() {
        return providerFee;
    }

    /** The budget heading this falls under, such as "Marketing" or "Production". */
    public String getCategory() {
        return category;
    }

    public String getTicketReference() {
        return ticketReference;
    }

    public String getAttendeeId() {
        return attendeeId;
    }

    /** The invoice this money settles, if any. */
    public String getInvoiceId() {
        return invoiceId;
    }

    public String getNote() {
        return note;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    /** When the money actually arrived, or null if it never has. */
    public Instant getSettledAt() {
        return settledAt;
    }

    /** Whether this figure includes tax that has to be reported. */
    public boolean isVatApplicable() {
        return vatApplicable;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    /** Whether the money has arrived and is being kept. */
    public boolean isCleared() {
        return status.isCleared();
    }

    /**
     * The amount that reaches the bank.
     *
     * <p>Gross less the provider's fee. This is the figure that has to match the
     * statement, and comparing gross against a net statement is the most common way
     * a reconciliation ends up looking short by exactly the processing fees.
     */
    public BigDecimal getNetAmount() {
        return Money.subtract(amount, providerFee);
    }
    /** Whether the money is expected but has not arrived yet. */
    public boolean isOutstanding() {
        return status == PaymentStatus.PENDING;
    }

    /** When the money should be in the account, if it is going to be. */
    public ZonedDateTime getExpectedSettlement() {
        ZonedDateTime raised = ZonedDateTime.ofInstant(raisedAt, ZoneId.systemDefault());
        return raised.plusDays(method.getSettlementDays());
    }

    /** Whether settlement is now late. Only meaningful for pending inflows. */
    public boolean isOverdue(Instant now) {
        return isOutstanding() && direction.isInflow() && now.isAfter(getExpectedSettlement().toInstant());
    }

    /** Days late on a pending inflow, zero if it is not late. */
    public long getDaysOverdue(Instant now) {
        if (!isOverdue(now)) {
            return 0;
        }
        return java.time.Duration.between(getExpectedSettlement().toInstant(), now).toDays();
    }
    /**
     * A copy recorded as refunded, as a row of its own.
     *
     * <p>The id is deliberately not reused. A reversal is a separate transaction with
     * its own identity, and giving it the original's id would collide on the primary
     * key — and would also lose the fact that two things happened rather than one.
     */
    public Payment refunded(Instant when, BigDecimal refundedAmount) {
        return new Payment(com.eventsuite.data.Ids.payment(), eventId, reference, counterparty,
                description, direction, method, refundedAmount, providerFee, category,
                ticketReference, attendeeId, invoiceId, note, raisedAt, when, vatApplicable,
                PaymentStatus.REFUNDED);
    }
    /**
     * Moves to a new settlement state.
     *
     * @throws IllegalStateException when the transition is not allowed
     */
    public void moveTo(PaymentStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Payment " + reference + " cannot go from "
                    + status.getLabel() + " to " + (next == null ? "nothing" : next.getLabel()));
        }
        status = next;
        if (next.isCleared() && settledAt == null) {
            settledAt = Instant.now();
        }
    }

    /** Records that the money arrived. */
    public void settle(Instant when) {
        moveTo(PaymentStatus.SETTLED);
        if (when != null) {
            settledAt = when;
        }
    }

    /** Restores a state read back from the database. */
    public void restoreStatus(PaymentStatus restored) {
        this.status = restored == null ? PaymentStatus.PENDING : restored;
    }

    /** The tax portion of this amount, at the standard UK rate. */
    public BigDecimal getVatAmount() {
        if (!vatApplicable) {
            return Money.ZERO;
        }
        // The amount is VAT-inclusive, so VAT is the part that is 1/6 of the total.
        return Money.of(amount.divide(new BigDecimal("6"), 2, java.math.RoundingMode.HALF_UP));
    }

    /** The amount excluding tax. */
    public BigDecimal getNetOfVat() {
        if (!vatApplicable) {
            return amount;
        }
        return Money.subtract(amount, getVatAmount());
    }

    /** A line for the ledger screen. */
    public String getDisplayLine() {
        String sign = direction.isInflow() ? "+" : "-";
        StringBuilder line = new StringBuilder(sign + " " + Money.format(amount));
        if (!getDescription().isEmpty()) {
            line.append("  ").append(getDescription());
        }
        if (!getCounterparty().isEmpty()) {
            line.append("  (").append(getCounterparty()).append(")");
        }
        line.append("  ").append(getMethod().getLabel()).append("  ").append(status.getLabel());
        return line.toString();
    }

    /** The date shown in the ledger, preferring when the money settled. */
    public String getWhenLabel() {
        return WHEN.format(ZonedDateTime.ofInstant(
                settledAt != null ? settledAt : raisedAt, ZoneId.systemDefault()));
    }

    /** A one-line receipt for the attendee. */
    public String getReceiptLine() {
        return getDisplayLine() + "   ref " + getReference();
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
