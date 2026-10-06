package com.eventsuite.analytics;

import com.eventsuite.finance.BudgetCategory;
import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The post-event check that the books balance.
 *
 * <p>Reconciliation is the least glamorous part of running an event and the only one
 * that cannot be skipped. It answers three questions: did the money recorded in the
 * system match what was actually taken, does everything invoiced have been settled,
 * and what is still owed by whom. Everything else in the finance module produces
 * numbers; this one checks them.
 *
 * <p>Small variances are expected and are reported as such. Card processing rounds,
 * a cash float counted twice, a fee for a currency conversion — a few pounds on a
 * nine-thousand-pound event is not an error worth escalating, and treating it as one
 * trains people to ignore the report. Anything larger is a real discrepancy and is
 * listed with what it is worth.
 */
public final class ReconciliationReport implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * A difference under this, as a fraction of the money handled, is treated as
     * rounding rather than as a problem. One pound in a thousand is roughly the
     * processing spread an event should expect.
     */
    public static final BigDecimal TOLERANCE = BigDecimal.valueOf(0.01);

    /** Whether the books balance, and by how much. */
    public enum Status {
        BALANCED("Balanced"),
        ROUNDING("Balanced within rounding"),
        DISCREPANCY("Discrepancy"),
        UNRECONCILED("Not reconciled");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean isAcceptable() {
            return this == BALANCED || this == ROUNDING;
        }
    }

    /** One line of the report: something that does not add up. */
    public static final class Discrepancy implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String description;
        private final BigDecimal expected;
        private final BigDecimal recorded;
        private final BudgetCategory category;
        private final String reference;

        public Discrepancy(String description, BigDecimal expected, BigDecimal recorded,
                           BudgetCategory category, String reference) {
            this.description = Objects.requireNonNull(description, "description");
            this.expected = Money.of(expected);
            this.recorded = Money.of(recorded);
            this.category = category;
            this.reference = reference == null ? "" : reference;
        }

        public String getDescription() {
            return description;
        }

        /** What it should have been. */
        public BigDecimal getExpected() {
            return expected;
        }

        /** What the system says it was. */
        public BigDecimal getRecorded() {
            return recorded;
        }

        /** Recorded less expected. Negative means money is missing. */
        public BigDecimal getVariance() {
            return Money.subtract(recorded, expected);
        }

        public BudgetCategory getCategory() {
            return category;
        }

        /** The invoice or payment reference this concerns, where there is one. */
        public String getReference() {
            return reference;
        }

        /** Whether money is short rather than over. */
        public boolean isShortfall() {
            return getVariance().signum() < 0;
        }

        /** What an organiser should do about it. */
        public String getSuggestedAction() {
            if (category != null && isShortfall()) {
                return "Check the " + category.getLabel().toLowerCase(Locale.ROOT)
                        + " invoice against the bank statement.";
            }
            if (isShortfall()) {
                return "Money is recorded as less than expected. Check for an unrecorded payment"
                        + " or an unreturned refund.";
            }
            return "More money is recorded than expected. Check for a duplicated entry"
                    + (reference.isEmpty() ? "." : " against reference " + reference + ".");
        }

        public String getDisplayLine() {
            return description + "  expected " + Money.format(expected)
                    + ", recorded " + Money.format(recorded)
                    + ", out by " + Money.format(getVariance());
        }

        @Override
        public String toString() {
            return getDisplayLine();
        }
    }

    private final String eventId;
    private final String eventName;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final BigDecimal expectedRevenue;
    private final BigDecimal recordedRevenue;
    private final BigDecimal totalCosts;
    private final BigDecimal cashCollected;
    private final BigDecimal moneyOutstanding;
    private final int invoicesIssued;
    private final int invoicesPaid;
    private final int invoicesOverdue;
    private final int paymentsUnmatched;
    private final BigDecimal cashFloatVariance;
    /** Declared as {@link ArrayList} so the field is concretely serialisable. */
    private final ArrayList<Discrepancy> discrepancies;

    private ReconciliationReport(Builder builder) {
        this.eventId = builder.eventId;
        this.eventName = builder.eventName;
        this.periodStart = builder.periodStart;
        this.periodEnd = builder.periodEnd;
        this.expectedRevenue = Money.of(builder.expectedRevenue);
        this.recordedRevenue = Money.of(builder.recordedRevenue);
        this.totalCosts = Money.of(builder.totalCosts);
        this.cashCollected = Money.of(builder.cashCollected);
        this.moneyOutstanding = Money.of(builder.moneyOutstanding);
        this.invoicesIssued = builder.invoicesIssued;
        this.invoicesPaid = builder.invoicesPaid;
        this.invoicesOverdue = builder.invoicesOverdue;
        this.paymentsUnmatched = builder.paymentsUnmatched;
        this.cashFloatVariance = Money.of(builder.cashFloatVariance);
        this.discrepancies = new ArrayList<>(builder.discrepancies);
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    /** What the tickets and invoices say should have been taken. */
    public BigDecimal getExpectedRevenue() {
        return expectedRevenue;
    }

    /** What the payment records say was actually taken. */
    public BigDecimal getRecordedRevenue() {
        return recordedRevenue;
    }

    public BigDecimal getTotalCosts() {
        return totalCosts;
    }

    /** Cash physically held, which includes the float. */
    public BigDecimal getCashCollected() {
        return cashCollected;
    }

    /** Money that is still owed. */
    public BigDecimal getMoneyOutstanding() {
        return moneyOutstanding;
    }

    public int getInvoicesIssued() {
        return invoicesIssued;
    }

    public int getInvoicesPaid() {
        return invoicesPaid;
    }

    public int getInvoicesOverdue() {
        return invoicesOverdue;
    }

    /** Payments that could not be matched to a ticket or an invoice. */
    public int getPaymentsUnmatched() {
        return paymentsUnmatched;
    }

    /**
     * How far the counted cash box is from what it should hold.
     *
     * <p>Only meaningful when the event took cash. Negative means the box is short,
     * which is the direction that matters.
     */
    public BigDecimal getCashFloatVariance() {
        return cashFloatVariance;
    }

    public List<Discrepancy> getDiscrepancies() {
        return discrepancies;
    }

    /** Recorded less expected. Negative means money is unaccounted for. */
    public BigDecimal getRevenueVariance() {
        return Money.subtract(recordedRevenue, expectedRevenue);
    }

    /** Whether money is short by more than the tolerance. */
    public boolean isShort() {
        return getRevenueVariance().signum() < 0;
    }

    /** Whether more was recorded than expected. */
    public boolean isOver() {
        return getRevenueVariance().signum() > 0;
    }

    /** What proportion of the money handled the variance represents. */
    public BigDecimal getVarianceRatio() {
        BigDecimal handled = Money.sum(expectedRevenue, recordedRevenue);
        if (handled.signum() == 0) {
            return Money.ZERO;
        }
        return Money.of(Money.abs(getRevenueVariance()).multiply(BigDecimal.valueOf(100))
                .divide(handled, 2, RoundingMode.HALF_UP));
    }

    /**
     * Whether the difference is small enough to be rounding.
     *
     * <p>Judged against the money handled, not against a fixed amount. Three pounds
     * on a small workshop is a different matter from three pounds on a festival, and
     * a fixed threshold would flag one and miss the other.
     */
    public boolean isWithinTolerance() {
        return getVarianceRatio().compareTo(TOLERANCE.multiply(BigDecimal.valueOf(100))) <= 0;
    }

    /** The overall verdict. */
    public Status getStatus() {
        if (expectedRevenue.signum() == 0 && recordedRevenue.signum() == 0
                && totalCosts.signum() == 0) {
            return Status.UNRECONCILED;
        }
        if (getRevenueVariance().signum() == 0) {
            return Status.BALANCED;
        }
        if (isWithinTolerance() && discrepancies.isEmpty()) {
            return Status.ROUNDING;
        }
        if (discrepancies.size() > 2 || getVarianceRatio().doubleValue() > 5.0) {
            return Status.DISCREPANCY;
        }
        return isWithinTolerance() ? Status.ROUNDING : Status.DISCREPANCY;
    }

    /** Whether the event is finished with money. */
    public boolean isReconciled() {
        return getStatus().isAcceptable() && moneyOutstanding.signum() == 0
                && invoicesOverdue == 0 && paymentsUnmatched == 0;
    }

    /** What should be in the bank: everything taken less processor fees. */
    public BigDecimal getExpectedCash() {
        return Money.subtract(expectedRevenue, getProviderFees());
    }

    private BigDecimal providerFees;
    private BigDecimal getProviderFees() {
        return providerFees == null ? Money.ZERO : providerFees;
    }

    /** Sets the processor fees, so the expected bank figure can be netted. */
    public ReconciliationReport withProviderFees(BigDecimal fees) {
        this.providerFees = Money.of(fees);
        return this;
    }

    /** The share of invoiced money that has been paid. */
    public BigDecimal getInvoiceSettlementRate() {
        if (invoicesIssued <= 0) {
            return Money.ZERO;
        }
        return Money.sharePercent(BigDecimal.valueOf(invoicesPaid),
                BigDecimal.valueOf(invoicesIssued));
    }

    /** What the event is left with, everything settled. */
    public BigDecimal getNetPosition() {
        return Money.subtract(recordedRevenue, totalCosts);
    }

    /** Whether the net position is positive. */
    public boolean isInProfit() {
        return getNetPosition().signum() > 0;
    }

    /** A printable summary for the file saved after the event. */
    public String render() {
        StringBuilder text = new StringBuilder();
        text.append("FINANCIAL RECONCILIATION\n");
        text.append("Event: ").append(eventName).append('\n');
        text.append("Period: ").append(periodStart).append(" to ").append(periodEnd).append('\n');
        text.append("Status: ").append(getStatus().getLabel()).append('\n');
        text.append('\n');
        text.append("Expected revenue:  ").append(Money.format(expectedRevenue)).append('\n');
        text.append("Recorded revenue:  ").append(Money.format(recordedRevenue)).append('\n');
        text.append("Variance:          ").append(Money.signed(getRevenueVariance())).append('\n');
        text.append("Total costs:       ").append(Money.format(totalCosts)).append('\n');
        text.append("Net position:      ").append(Money.signed(getNetPosition())).append('\n');
        text.append('\n');
        text.append("Invoices issued:   ").append(invoicesIssued).append('\n');
        text.append("Invoices paid:     ").append(invoicesPaid).append('\n');
        text.append("Invoices overdue:  ").append(invoicesOverdue).append('\n');
        text.append("Money outstanding: ").append(Money.format(moneyOutstanding)).append('\n');
        text.append("Unmatched payments:").append(paymentsUnmatched).append('\n');
        text.append("Cash float:        ").append(Money.signed(cashFloatVariance)).append('\n');
        if (!discrepancies.isEmpty()) {
            text.append('\n');
            text.append("DISCREPANCIES\n");
            for (Discrepancy discrepancy : discrepancies) {
                text.append("  ").append(discrepancy.getDisplayLine()).append('\n');
                text.append("    -> ").append(discrepancy.getSuggestedAction()).append('\n');
            }
        } else {
            text.append('\n').append("No discrepancies found.").append('\n');
        }
        return text.toString();
    }

    /** A one-line summary for the dashboard. */
    public String getSummaryLine() {
        if (getStatus() == Status.BALANCED) {
            return "The books balance for " + eventName + ".";
        }
        return "Reconciliation " + getStatus().getLabel().toLowerCase(Locale.ROOT) + ": "
                + Money.signed(getRevenueVariance()) + " out on "
                + getEventName() + ".";
    }

    public static Builder builder(String eventId, String eventName, LocalDate periodStart,
                                  LocalDate periodEnd) {
        return new Builder(eventId, eventName, periodStart, periodEnd);
    }

    /** Assembles a reconciliation report. */
    public static final class Builder {
        private final String eventId;
        private final String eventName;
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private BigDecimal expectedRevenue = Money.ZERO;
        private BigDecimal recordedRevenue = Money.ZERO;
        private BigDecimal totalCosts = Money.ZERO;
        private BigDecimal cashCollected = Money.ZERO;
        private BigDecimal moneyOutstanding = Money.ZERO;
        private int invoicesIssued;
        private int invoicesPaid;
        private int invoicesOverdue;
        private int paymentsUnmatched;
        private BigDecimal cashFloatVariance = Money.ZERO;
        private final List<Discrepancy> discrepancies = new ArrayList<>();

        private Builder(String eventId, String eventName, LocalDate periodStart,
                        LocalDate periodEnd) {
            this.eventId = eventId == null ? "" : eventId;
            this.eventName = eventName == null ? "" : eventName;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }

        public Builder revenue(BigDecimal expected, BigDecimal recorded) {
            this.expectedRevenue = Money.of(expected);
            this.recordedRevenue = Money.of(recorded);
            return this;
        }

        public Builder costs(BigDecimal amount) {
            this.totalCosts = Money.of(amount);
            return this;
        }

        public Builder cash(BigDecimal collected) {
            this.cashCollected = Money.of(collected);
            return this;
        }

        public Builder outstanding(BigDecimal amount) {
            this.moneyOutstanding = Money.of(amount);
            return this;
        }

        public Builder invoices(int issued, int paid, int overdue) {
            this.invoicesIssued = Math.max(0, issued);
            this.invoicesPaid = Math.max(0, paid);
            this.invoicesOverdue = Math.max(0, overdue);
            return this;
        }

        public Builder unmatchedPayments(int count) {
            this.paymentsUnmatched = Math.max(0, count);
            return this;
        }

        public Builder cashFloatVariance(BigDecimal amount) {
            this.cashFloatVariance = Money.of(amount);
            return this;
        }

        public Builder addDiscrepancy(Discrepancy discrepancy) {
            if (discrepancy != null) {
                discrepancies.add(discrepancy);
            }
            return this;
        }

        public ReconciliationReport build() {
            return new ReconciliationReport(this);
        }
    }

    @Override
    public String toString() {
        return getSummaryLine();
    }
}
