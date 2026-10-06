package com.eventsuite.finance;

/**
 * Where money physically moved, which decides how it clears and when it counts.
 *
 * <p>The distinction that matters for reconciliation is between money that is in
 * the bank and money that has been promised. A card payment and a mobile money
 * transfer are in the account within minutes; a bank transfer initiated on the
 * last day of an event may not arrive before the reconciliation is run. Both are
 * legitimate sales, but only one of them is cash in hand, and reporting the two
 * as the same figure is how an event ends up claiming money it has not received.
 */
public enum PaymentMethod {

    CARD("Card", 1, 0.014, true),
    MOBILE_MONEY("Mobile money", 1, 0.015, true),
    BANK_TRANSFER("Bank transfer", 3, 0.0, true),
    CASH("Cash", 0, 0.0, false),
    CHEQUE("Cheque", 5, 0.0, true),
    CREDIT("Card on account", 30, 0.029, false),
    OTHER("Other", 0, 0.0, true);

    private final String label;
    private final int settlementDays;
    private final double providerFeePercent;
    private final boolean traceable;

    PaymentMethod(String label, int settlementDays, double providerFeePercent, boolean traceable) {
        this.label = label;
        this.settlementDays = settlementDays;
        this.providerFeePercent = providerFeePercent;
        this.traceable = traceable;
    }

    public String getLabel() {
        return label;
    }

    /**
     * How long before the money is actually available.
     *
     * <p>Cash is same-day because it is in the box. Card and mobile money clear in
     * a day. A credit agreement is the slow one, which is why an unpaid sponsor
     * invoice should never be counted as revenue on the night.
     */
    public int getSettlementDays() {
        return settlementDays;
    }

    /** What the payment provider keeps, as a percentage of the amount. */
    public double getProviderFeePercent() {
        return providerFeePercent;
    }

    /**
     * Whether this method can be matched against a bank statement line.
     *
     * <p>Cash cannot, which is why a cash-heavy event needs a counted float and a
     * signed reconciliation sheet rather than a bank feed.
     */
    public boolean isTraceable() {
        return traceable;
    }

    /** Whether the money lands straight away. */
    public boolean isImmediate() {
        return settlementDays <= 1;
    }

    /**
     * What the provider takes from this amount.
     *
     * <p>The rate is held as a fraction — 0.014 for one and a half per cent — so it is
     * multiplied, not treated as a percentage. Passing it to a percentage helper would
     * divide by a hundred a second time and make every fee a hundred times too small,
     * which on a cheap ticket rounds to nothing at all and quietly overstates takings
     * by the whole processing cost.
     */
    public java.math.BigDecimal providerFeeOn(java.math.BigDecimal amount) {
        if (amount == null || providerFeePercent <= 0) {
            return Money.ZERO;
        }
        return Money.of(Money.of(amount).multiply(
                java.math.BigDecimal.valueOf(providerFeePercent)));
    }

    /** What actually lands in the account after the provider's cut. */
    public java.math.BigDecimal netOf(java.math.BigDecimal amount) {
        return Money.subtract(Money.of(amount), providerFeeOn(amount));
    }

    public static PaymentMethod fromLabel(String text) {
        if (text == null) {
            return OTHER;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (PaymentMethod method : values()) {
            if (method.name().replace("_", "").equalsIgnoreCase(needle)) {
                return method;
            }
            if (method.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return method;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
