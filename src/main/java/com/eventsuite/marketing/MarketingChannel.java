package com.eventsuite.marketing;

import java.util.Locale;

/**
 * Where an event is promoted, and how each channel behaves.
 *
 * <p>The channels differ in ways that change how their numbers should be read, and
 * those differences are encoded rather than left to whoever writes the report. A
 * poster has a reach but no clicks. Email has a list you own and a cost per send
 * near zero. Paid social costs money before anybody arrives. Reading a return on
 * investment across all of them as though they were the same is the standard way
 * marketing attribution goes wrong, so each channel carries its own cost model.
 */
public enum MarketingChannel {

    ORGANIC_SOCIAL("Organic social", 0, 0, 0.01, false),
    PAID_SOCIAL("Paid social", 0, 0, 1.20, true),
    EMAIL("Email", 0, 0.02, 0.10, false),
    PAID_SEARCH("Paid search", 0, 0.12, 3.50, true),
    PRINT("Print and press", 0, 0, 0.40, false),
    RADIO("Radio", 0, 0, 8.00, true),
    TV("Television", 0, 0, 45.00, true),
    OUTDOOR("Outdoor and billboards", 0, 0, 12.00, true),
    EVENTS_AND_TRADE("Trade and partner events", 0, 0, 15.00, true),
    REFERRAL("Referral", 0, 0.05, 5.00, true),
    PARTNERSHIP("Partnership and sponsorship", 0, 0, 0.00, false),
    INFLUENCER("Influencer", 0, 0, 350.00, true),
    SEO("Search engine optimisation", 0, 0, 25.00, true),
    OTHER("Other", 0, 0, 5.00, true);

    private final String label;
    private final double fixedCost;
    private final double costPerThousandReach;
    private final double costPerRegistration;
    private final boolean paid;

    MarketingChannel(String label, double fixedCost, double costPerThousandReach,
                     double costPerRegistration, boolean paid) {
        this.label = label;
        this.fixedCost = fixedCost;
        this.costPerThousandReach = costPerThousandReach;
        this.costPerRegistration = costPerRegistration;
        this.paid = paid;
    }

    public String getLabel() {
        return label;
    }

    /** Whether money was spent on this channel, as opposed to time and effort. */
    public boolean isPaid() {
        return paid;
    }
    /** What one registration obtained through this channel costs, in pounds. */
    public double getCostPerRegistration() {
        return costPerRegistration;
    }

    /**
     * Whether the channel can be attributed to a specific person.
     *
     * <p>Direct and referral traffic can be traced back to who sent it. A billboard
     * cannot be: attributing a sale to it is a guess, and the dashboard labels
     * unattributable spend separately so the guess is visible rather than buried.
     */
    public boolean isAttributable() {
        return this == REFERRAL || this == PARTNERSHIP || this == EMAIL;
    }
    /**
     * What this channel would cost to reach {@code reach} people.
     *
     * <p>A rate quoted "per thousand" is divided by a thousand, not multiplied by it.
     * Multiplying would make a campaign that reached forty thousand people cost forty
     * times a campaign that reached a thousand, when the relationship is the other way
     * round — and it is the kind of error that makes a channel look far too cheap.
     *
     * <p>The registration cost is added on top, because some of what was spent bought
     * registrations rather than impressions, and the two are not the same money.
     */
    public java.math.BigDecimal estimateCostFor(int reach, int registrations) {
        java.math.BigDecimal reachCost = java.math.BigDecimal.valueOf(
                Math.max(0, reach) * costPerThousandReach / 1000.0);
        java.math.BigDecimal registrationCost = java.math.BigDecimal.valueOf(
                Math.max(0, registrations) * costPerRegistration);
        return com.eventsuite.finance.Money.sum(reachCost, registrationCost);
    }

    public static MarketingChannel fromLabel(String text) {
        if (text == null) {
            return OTHER;
        }
        String needle = text.trim().replace(" ", "").replace("&", "").replace("_", "");
        for (MarketingChannel channel : values()) {
            if (channel.name().replace("_", "").equalsIgnoreCase(needle)) {
                return channel;
            }
            if (channel.label.replace(" ", "").replace("&", "").equalsIgnoreCase(needle)) {
                return channel;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
