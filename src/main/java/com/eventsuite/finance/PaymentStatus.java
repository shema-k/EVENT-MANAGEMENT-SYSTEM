package com.eventsuite.finance;

/**
 * Whether a money movement has actually happened.
 *
 * <p>Revenue is only revenue once it is settled. A payment that has been attempted
 * and declined, or one that is waiting on a bank transfer nobody has sent, is a
 * lead rather than income, and the dashboards are built to exclude both. Keeping
 * that distinction at the record rather than in the reporting code is what stops
 * a stalled invoice inflating the take.
 */
public enum PaymentStatus {

    /** Raised, but the money has not moved. Not revenue. */
    PENDING("Pending", false, false),

    /** Attempted and declined. */
    FAILED("Failed", false, false),

    /** Money received and in hand. This is revenue. */
    SETTLED("Settled", true, false),

    /** Given back to the payer. Counts against revenue. */
    REFUNDED("Refunded", false, true),

    /** Part-refunded: some money back, the rest kept. */
    PART_REFUNDED("Part refunded", true, true);

    private final String label;
    private final boolean countsAsRevenue;
    private final boolean isReversal;

    PaymentStatus(String label, boolean countsAsRevenue, boolean isReversal) {
        this.label = label;
        this.countsAsRevenue = countsAsRevenue;
        this.isReversal = isReversal;
    }

    public String getLabel() {
        return label;
    }

    /** Whether money has genuinely been received and is kept. */
    public boolean countsAsRevenue() {
        return countsAsRevenue;
    }

    /**
     * Whether this state takes money back out.
     *
     * <p>A reversal still counts as revenue when it is partial, because some of the
     * money was kept. That is why the flag is separate from
     * {@link #countsAsRevenue()}.
     */
    public boolean isReversal() {
        return isReversal;
    }

    /** Whether the money is in the account. */
    public boolean isCleared() {
        return this == SETTLED || this == PART_REFUNDED;
    }

    /** Whether this state may move to {@code next}. */
    public boolean canMoveTo(PaymentStatus next) {
        if (next == null || next == this) {
            return false;
        }
        switch (this) {
            case PENDING:
                return next == SETTLED || next == FAILED;
            case FAILED:
                return next == PENDING;
            case SETTLED:
                return next == REFUNDED || next == PART_REFUNDED;
            default:
                return false;
        }
    }

    public static PaymentStatus fromLabel(String text) {
        if (text == null) {
            return PENDING;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (PaymentStatus status : values()) {
            if (status.name().replace("_", "").equalsIgnoreCase(needle)) {
                return status;
            }
            if (status.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return status;
            }
        }
        return PENDING;
    }

    @Override
    public String toString() {
        return label;
    }
}
