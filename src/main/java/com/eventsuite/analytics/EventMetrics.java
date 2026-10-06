package com.eventsuite.analytics;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Everything the dashboards need about one event, in one immutable snapshot.
 *
 * <p>Metrics are gathered in one pass and handed to the screens as a value. That is
 * deliberate: a dashboard that runs twelve queries while drawing is slow, and one
 * that runs its queries at different moments can show a total that does not match
 * the breakdown beside it. Taking a single snapshot means every tile on a screen
 * describes the same instant.
 *
 * <p>The percentages are worked out here rather than in the screens, and each one is
 * defined by what it divides by. {@link #getAttendanceRate()} is checked in against
 * tickets sold, not against capacity, because a sold-out event with a third of its
 * holders not turn up is not a full house — it is a sold-out event with a third of
 * its holders not turning up, and those are different problems.
 */
public final class EventMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String eventId;
    private final String eventName;
    private final String categoryLabel;
    private final int capacity;
    private final int sellableCapacity;

    private final int ticketsSold;
    private final int ticketsReserved;
    private final int ticketsCheckedIn;
    private final int ticketsRefunded;
    private final int ticketsVoided;
    private final int rejectedEntries;

    private final int registrationsConfirmed;
    private final int registrationsWaitlisted;
    private final int registrationsCancelled;
    private final int uniqueAttendees;

    private final BigDecimal grossRevenue;
    private final BigDecimal netRevenue;
    private final BigDecimal otherRevenue;
    private final BigDecimal outstandingMoney;
    private final BigDecimal providerFees;

    private final BigDecimal budgetPlanned;
    private final BigDecimal budgetCommitted;
    private final BigDecimal budgetActual;
    private final int budgetHeadingsOverspent;

    private final BigDecimal marketingSpend;
    private final int campaignsRun;

    private final int speakersConfirmed;
    private final int speakersTotal;
    private final int speakersWithdrawn;

    private final int feedbackResponses;
    private final double averageFeedback;
    private final int promoterScore;
    private final double recommendationRate;

    private final int engagementRecords;
    private final int engagedAttendees;

    private final BigDecimal carbonKg;
    private final BigDecimal recyclingRate;

    /**
     * Arrivals and revenue series.
     *
     * <p>Declared as {@link ArrayList} rather than {@code List} so the fields are
     * concretely serialisable; the accessors hand out unmodifiable views.
     */
    private final ArrayList<TrendPoint> arrivalTrend;
    private final ArrayList<TrendPoint> revenueTrend;
    private final RoiCalculator roi;

    private EventMetrics(Builder builder) {
        this.eventId = builder.eventId;
        this.eventName = builder.eventName;
        this.categoryLabel = builder.categoryLabel;
        this.capacity = builder.capacity;
        this.sellableCapacity = builder.sellableCapacity;
        this.ticketsSold = builder.ticketsSold;
        this.ticketsReserved = builder.ticketsReserved;
        this.ticketsCheckedIn = builder.ticketsCheckedIn;
        this.ticketsRefunded = builder.ticketsRefunded;
        this.ticketsVoided = builder.ticketsVoided;
        this.rejectedEntries = builder.rejectedEntries;
        this.registrationsConfirmed = builder.registrationsConfirmed;
        this.registrationsWaitlisted = builder.registrationsWaitlisted;
        this.registrationsCancelled = builder.registrationsCancelled;
        this.uniqueAttendees = builder.uniqueAttendees;
        this.grossRevenue = builder.grossRevenue;
        this.netRevenue = builder.netRevenue;
        this.otherRevenue = builder.otherRevenue;
        this.outstandingMoney = builder.outstandingMoney;
        this.providerFees = builder.providerFees;
        this.budgetPlanned = builder.budgetPlanned;
        this.budgetCommitted = builder.budgetCommitted;
        this.budgetActual = builder.budgetActual;
        this.budgetHeadingsOverspent = builder.budgetHeadingsOverspent;
        this.marketingSpend = builder.marketingSpend;
        this.campaignsRun = builder.campaignsRun;
        this.speakersConfirmed = builder.speakersConfirmed;
        this.speakersTotal = builder.speakersTotal;
        this.speakersWithdrawn = builder.speakersWithdrawn;
        this.feedbackResponses = builder.feedbackResponses;
        this.averageFeedback = builder.averageFeedback;
        this.promoterScore = builder.promoterScore;
        this.recommendationRate = builder.recommendationRate;
        this.engagementRecords = builder.engagementRecords;
        this.engagedAttendees = builder.engagedAttendees;
        this.carbonKg = builder.carbonKg;
        this.recyclingRate = builder.recyclingRate;
        this.arrivalTrend = new ArrayList<>(builder.arrivalTrend);
        this.revenueTrend = new ArrayList<>(builder.revenueTrend);
        this.roi = builder.roi;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }
    /** Simultaneous capacity of the room. */
    public int getCapacity() {
        return capacity;
    }

    /** Total tickets that may ever be sold, which may be capacity times days. */
    public int getSellableCapacity() {
        return sellableCapacity;
    }

    /** Tickets paid for and valid. */
    public int getTicketsSold() {
        return ticketsSold;
    }
    /** Tickets whose holders have been through the door. */
    public int getTicketsCheckedIn() {
        return ticketsCheckedIn;
    }
    /** How many people were turned away or hit a problem at the door. */
    public int getRejectedEntries() {
        return rejectedEntries;
    }

    public int getRegistrationsConfirmed() {
        return registrationsConfirmed;
    }

    public int getRegistrationsWaitlisted() {
        return registrationsWaitlisted;
    }
    /** How many distinct people have actually been at the event. */
    public int getUniqueAttendees() {
        return uniqueAttendees;
    }

    /** Ticket money as taken, before processor fees. */
    public BigDecimal getGrossRevenue() {
        return grossRevenue;
    }

    /** Ticket money after processor fees. This is what arrives. */
    public BigDecimal getNetRevenue() {
        return netRevenue;
    }

    /** Sponsorship, exhibitors, grants and anything else. */
    public BigDecimal getOtherRevenue() {
        return otherRevenue;
    }

    /** Money that is owed but has not arrived. */
    public BigDecimal getOutstandingMoney() {
        return outstandingMoney;
    }

    public BigDecimal getProviderFees() {
        return providerFees;
    }

    public BigDecimal getBudgetPlanned() {
        return budgetPlanned;
    }

    public BigDecimal getBudgetCommitted() {
        return budgetCommitted;
    }

    public BigDecimal getBudgetActual() {
        return budgetActual;
    }

    /** How many budget headings are already over their allocation. */
    public int getBudgetHeadingsOverspent() {
        return budgetHeadingsOverspent;
    }

    public BigDecimal getMarketingSpend() {
        return marketingSpend;
    }
    public int getSpeakersConfirmed() {
        return speakersConfirmed;
    }

    public int getSpeakersTotal() {
        return speakersTotal;
    }

    public int getSpeakersWithdrawn() {
        return speakersWithdrawn;
    }

    public int getFeedbackResponses() {
        return feedbackResponses;
    }

    /** Average rating out of five, zero when nobody has answered. */
    public double getAverageFeedback() {
        return averageFeedback;
    }

    /** The net promoter style score, from minus one hundred to plus one hundred. */
    public int getPromoterScore() {
        return promoterScore;
    }

    /** The share of respondents who would recommend the event, from zero to one. */
    public double getRecommendationRate() {
        return recommendationRate;
    }
    /** The event's carbon footprint in kilograms. */
    public BigDecimal getCarbonKg() {
        return carbonKg;
    }

    /** The share of waste recycled, as a percentage. */
    public BigDecimal getRecyclingRate() {
        return recyclingRate;
    }
    /** Ticket revenue over time, cumulative. */
    public List<TrendPoint> getRevenueTrend() {
        return revenueTrend;
    }

    public RoiCalculator getRoi() {
        return roi;
    }

    // ---------------------------------------------------------------- derived

    /** How much of the sellable inventory has gone. */
    public BigDecimal getSellThroughPercent() {
        return Money.sharePercent(BigDecimal.valueOf(ticketsSold),
                BigDecimal.valueOf(sellableCapacity));
    }

    /** Tickets left to sell, never below zero. */
    public int getTicketsRemaining() {
        return Math.max(0, sellableCapacity - ticketsSold);
    }

    /** Whether the event has sold everything it can. */
    public boolean isSoldOut() {
        return getTicketsRemaining() == 0;
    }

    /**
     * How many of those who bought turned up.
     *
     * <p>Against tickets sold rather than capacity. The no-show figure is the
     * complement of this one.
     */
    public BigDecimal getAttendanceRate() {
        return Money.sharePercent(BigDecimal.valueOf(ticketsCheckedIn),
                BigDecimal.valueOf(ticketsSold));
    }

    /**
     * The share of ticket holders who did not arrive.
     *
     * <p>Zero before the event opens, because nobody is late for something that has
     * not started. Showing a 100% no-show rate for an event selling tickets a month
     * ahead would be nonsense.
     */
    public BigDecimal getNoShowRate() {
        if (ticketsSold <= 0 || ticketsCheckedIn == 0) {
            return Money.ZERO;
        }
        BigDecimal rate = Money.sharePercent(
                BigDecimal.valueOf(ticketsSold - ticketsCheckedIn),
                BigDecimal.valueOf(ticketsSold));
        return Money.of(rate.setScale(1, RoundingMode.HALF_UP));
    }

    /** How full the room is, against capacity rather than against tickets sold. */
    public BigDecimal getOccupancyPercent() {
        return Money.sharePercent(BigDecimal.valueOf(ticketsCheckedIn),
                BigDecimal.valueOf(capacity));
    }

    /** What the average ticket came to. Zero when nothing has sold. */
    public BigDecimal getAverageTicketValue() {
        if (ticketsSold <= 0) {
            return Money.ZERO;
        }
        return Money.of(grossRevenue.divide(BigDecimal.valueOf(ticketsSold), 2,
                RoundingMode.HALF_UP));
    }

    /** What is left in the budget after commitments and spend. */
    public BigDecimal getBudgetRemaining() {
        BigDecimal left = Money.subtract(Money.subtract(budgetPlanned, budgetCommitted),
                budgetActual);
        return left.signum() < 0 ? Money.ZERO : left;
    }

    /** How much of the budget is spoken for. */
    public BigDecimal getBudgetUsedPercent() {
        return Money.sharePercent(Money.sum(budgetCommitted, budgetActual), budgetPlanned);
    }

    /** Whether any budget heading has gone over. */
    public boolean isOverBudget() {
        return budgetHeadingsOverspent > 0;
    }
    /** Whether the feedback is bad enough to need looking at. */
    public boolean hasPoorFeedback() {
        return feedbackResponses > 0 && averageFeedback < 3.0;
    }

    /** The share of attendees who did anything that counts as engaging. */
    public BigDecimal getEngagementRate() {
        if (uniqueAttendees <= 0) {
            return Money.ZERO;
        }
        return Money.sharePercent(BigDecimal.valueOf(engagedAttendees),
                BigDecimal.valueOf(uniqueAttendees));
    }
    /** Speakers not yet confirmed, which is the number to chase. */
    public int getSpeakersUnconfirmed() {
        return Math.max(0, speakersTotal - speakersConfirmed - speakersWithdrawn);
    }

    /** The carbon footprint as a readable figure. */
    public String getCarbonLabel() {
        if (carbonKg.signum() <= 0) {
            return "Not recorded";
        }
        if (carbonKg.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return carbonKg.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " tonnes";
        }
        return carbonKg.setScale(0, RoundingMode.HALF_UP).toPlainString() + " kg";
    }
    /** The busiest arrival interval, or null when there is no trend. */
    public TrendPoint getBusiestInterval() {
        TrendPoint busiest = null;
        for (TrendPoint point : arrivalTrend) {
            if (busiest == null || point.getValue() > busiest.getValue()) {
                busiest = point;
            }
        }
        return busiest;
    }
    /**
     * A one-line health summary.
     *
     * <p>Written for somebody glancing at a dashboard, so it names the single thing
     * that most needs attention rather than listing everything.
     */
    public String getHealthSummary() {
        if (isOverBudget()) {
            return budgetHeadingsOverspent + " budget heading(s) over their allocation.";
        }
        if (getTicketsRemaining() == 0 && ticketsCheckedIn == 0) {
            return "Sold out with nobody through the door yet.";
        }
        if (rejectedEntries > 0 && ticketsCheckedIn > 0
                && rejectedEntries * 4 > ticketsCheckedIn) {
            return "One in four arrivals hit a problem at the door.";
        }
        if (hasPoorFeedback()) {
            return String.format(Locale.UK, "Feedback averages %.1f out of 5.", averageFeedback);
        }
        if (getSpeakersUnconfirmed() > 0 && speakersTotal > 0) {
            return getSpeakersUnconfirmed() + " speaker(s) still unconfirmed.";
        }
        if (isSoldOut()) {
            return "Sold out.";
        }
        if (ticketsSold > 0) {
            return getTicketsRemaining() + " tickets still to sell.";
        }
        return "Nothing sold yet.";
    }

    /** Builds a metrics snapshot. */
    public static Builder builder(String eventId, String eventName) {
        return new Builder(eventId, eventName);
    }

    /**
     * Assembles a snapshot.
     *
     * <p>Every value defaults to zero rather than being required, so a brand new
     * event with no sales, no budget and no speakers still produces a complete set of
     * metrics rather than a null.
     */
    public static final class Builder {
        private final String eventId;
        private final String eventName;
        private String categoryLabel = "";
        private int capacity;
        private int sellableCapacity;
        private int ticketsSold;
        private int ticketsReserved;
        private int ticketsCheckedIn;
        private int ticketsRefunded;
        private int ticketsVoided;
        private int rejectedEntries;
        private int registrationsConfirmed;
        private int registrationsWaitlisted;
        private int registrationsCancelled;
        private int uniqueAttendees;
        private BigDecimal grossRevenue = Money.ZERO;
        private BigDecimal netRevenue = Money.ZERO;
        private BigDecimal otherRevenue = Money.ZERO;
        private BigDecimal outstandingMoney = Money.ZERO;
        private BigDecimal providerFees = Money.ZERO;
        private BigDecimal budgetPlanned = Money.ZERO;
        private BigDecimal budgetCommitted = Money.ZERO;
        private BigDecimal budgetActual = Money.ZERO;
        private int budgetHeadingsOverspent;
        private BigDecimal marketingSpend = Money.ZERO;
        private int campaignsRun;
        private int speakersConfirmed;
        private int speakersTotal;
        private int speakersWithdrawn;
        private int feedbackResponses;
        private double averageFeedback;
        private int promoterScore;
        private double recommendationRate;
        private int engagementRecords;
        private int engagedAttendees;
        private BigDecimal carbonKg = Money.ZERO;
        private BigDecimal recyclingRate = Money.ZERO;
        private List<TrendPoint> arrivalTrend = new ArrayList<>();
        private List<TrendPoint> revenueTrend = new ArrayList<>();
        private RoiCalculator roi;

        private Builder(String eventId, String eventName) {
            this.eventId = eventId == null ? "" : eventId;
            this.eventName = eventName == null ? "" : eventName;
        }

        public Builder category(String label) {
            this.categoryLabel = label == null ? "" : label;
            return this;
        }

        public Builder capacity(int value, int sellable) {
            this.capacity = Math.max(0, value);
            this.sellableCapacity = Math.max(this.capacity, sellable);
            return this;
        }

        public Builder tickets(int sold, int reserved, int checkedIn, int refunded, int voided) {
            this.ticketsSold = Math.max(0, sold);
            this.ticketsReserved = Math.max(0, reserved);
            this.ticketsCheckedIn = Math.max(0, checkedIn);
            this.ticketsRefunded = Math.max(0, refunded);
            this.ticketsVoided = Math.max(0, voided);
            return this;
        }

        public Builder rejectedEntries(int value) {
            this.rejectedEntries = Math.max(0, value);
            return this;
        }

        public Builder registrations(int confirmed, int waitlisted, int cancelled, int unique) {
            this.registrationsConfirmed = Math.max(0, confirmed);
            this.registrationsWaitlisted = Math.max(0, waitlisted);
            this.registrationsCancelled = Math.max(0, cancelled);
            this.uniqueAttendees = Math.max(0, unique);
            return this;
        }

        public Builder revenue(BigDecimal gross, BigDecimal net, BigDecimal other,
                                BigDecimal outstanding, BigDecimal fees) {
            this.grossRevenue = Money.of(gross);
            this.netRevenue = Money.of(net);
            this.otherRevenue = Money.of(other);
            this.outstandingMoney = Money.of(outstanding);
            this.providerFees = Money.of(fees);
            return this;
        }

        public Builder budget(BigDecimal planned, BigDecimal committed, BigDecimal actual,
                              int overspent) {
            this.budgetPlanned = Money.of(planned);
            this.budgetCommitted = Money.of(committed);
            this.budgetActual = Money.of(actual);
            this.budgetHeadingsOverspent = Math.max(0, overspent);
            return this;
        }

        public Builder marketing(BigDecimal spend, int campaigns) {
            this.marketingSpend = Money.of(spend);
            this.campaignsRun = Math.max(0, campaigns);
            return this;
        }

        public Builder speakers(int confirmed, int total, int withdrawn) {
            this.speakersConfirmed = Math.max(0, confirmed);
            this.speakersTotal = Math.max(0, total);
            this.speakersWithdrawn = Math.max(0, withdrawn);
            return this;
        }

        public Builder feedback(int responses, double average, int promoterScore,
                                double recommendationRate) {
            this.feedbackResponses = Math.max(0, responses);
            this.averageFeedback = average;
            this.promoterScore = promoterScore;
            this.recommendationRate = recommendationRate;
            return this;
        }

        public Builder engagement(int records, int engagedAttendees) {
            this.engagementRecords = Math.max(0, records);
            this.engagedAttendees = Math.max(0, engagedAttendees);
            return this;
        }

        public Builder sustainability(BigDecimal carbon, BigDecimal recyclingRate) {
            this.carbonKg = Money.of(carbon);
            this.recyclingRate = Money.of(recyclingRate);
            return this;
        }

        public Builder arrivalTrend(List<TrendPoint> points) {
            this.arrivalTrend = points == null ? new ArrayList<>() : points;
            return this;
        }

        public Builder revenueTrend(List<TrendPoint> points) {
            this.revenueTrend = points == null ? new ArrayList<>() : points;
            return this;
        }

        /** Supplies the return-on-investment figures. */
        public Builder roi(RoiCalculator calculator) {
            this.roi = calculator;
            return this;
        }

        public EventMetrics build() {
            if (roi == null) {
                // Derived from what is already here, so a builder that forgets to set
                // the calculator still produces coherent return figures.
                roi = new RoiCalculator(grossRevenue, otherRevenue, budgetActual,
                        budgetCommitted, marketingSpend, providerFees,
                        BigDecimal.valueOf(uniqueAttendees), BigDecimal.valueOf(capacity),
                        grossRevenue.signum() > 0 && ticketsSold > 0
                                ? grossRevenue.divide(BigDecimal.valueOf(ticketsSold), 2,
                                        RoundingMode.HALF_UP) : Money.ZERO);
            }
            return new EventMetrics(this);
        }
    }

    @Override
    public String toString() {
        return eventName + ": " + getHealthSummary();
    }
}
