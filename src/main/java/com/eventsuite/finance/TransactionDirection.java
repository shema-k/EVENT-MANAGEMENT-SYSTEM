package com.eventsuite.finance;

/**
 * Which way the money went.
 *
 * <p>One record type covers both directions. An event's finances are a single
 * ledger, and splitting takings from costs into two tables would mean every
 * reconciliation joining them back together to answer the only question anybody
 * actually asks: are we up or down.
 */
public enum TransactionDirection {

    /** Money arriving: a ticket sale, a sponsorship, a grant. */
    INFLOW("Money in", 1),

    /** Money leaving: a supplier invoice, a speaker fee, a staff payment. */
    OUTFLOW("Money out", -1);

    private final String label;
    private final int sign;

    TransactionDirection(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String getLabel() {
        return label;
    }

    /** +1 for money in, -1 for money out, so totals can be summed. */
    public int getSign() {
        return sign;
    }

    /** Whether this direction increases the event's balance. */
    public boolean isInflow() {
        return this == INFLOW;
    }

    /** The amount with the direction's sign applied, for running totals. */
    public java.math.BigDecimal signed(java.math.BigDecimal amount) {
        java.math.BigDecimal value = Money.of(amount);
        return sign < 0 ? value.negate() : value;
    }

    public static TransactionDirection fromLabel(String text) {
        if (text != null) {
            String needle = text.trim().replace(" ", "").replace("_", "");
            for (TransactionDirection direction : values()) {
                if (direction.name().equalsIgnoreCase(needle)) {
                    return direction;
                }
            }
            if (needle.equalsIgnoreCase("income") || needle.equalsIgnoreCase("sale")) {
                return INFLOW;
            }
            if (needle.equalsIgnoreCase("expense") || needle.equalsIgnoreCase("cost")) {
                return OUTFLOW;
            }
        }
        return INFLOW;
    }

    @Override
    public String toString() {
        return label;
    }
}
