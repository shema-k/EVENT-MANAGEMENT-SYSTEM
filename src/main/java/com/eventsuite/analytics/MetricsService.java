package com.eventsuite.analytics;

import com.eventsuite.data.EventStore;
import com.eventsuite.data.FinanceStore;
import com.eventsuite.data.MarketingStore;
import com.eventsuite.data.OpsStore;
import com.eventsuite.data.PeopleStore;
import com.eventsuite.data.TicketStore;
import com.eventsuite.core.Event;
import com.eventsuite.finance.BudgetLine;
import com.eventsuite.people.SpeakerStatus;
import com.eventsuite.ticketing.RegistrationStatus;
import com.eventsuite.ticketing.TicketStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Builds the dashboard snapshots from the stored records.
 *
 * <p>Every number on a dashboard comes from here, and nothing is computed in the
 * screens. That is the single most important decision in the analytics layer: a
 * figure calculated in two places is a figure that will disagree with itself, and the
 * first thing anybody notices when they trust a dashboard is two tiles that do not
 * add up.
 *
 * <p>The aggregation happens once per call rather than per tile, so a dashboard
 * showing twelve tiles reads the tables a fixed number of times no matter how many
 * tiles there are. That is why this takes the connection rather than opening its own.
 */
public final class MetricsService {

    private final Connection connection;
    private final EventStore events;
    private final TicketStore tickets;
    private final PeopleStore people;
    private final FinanceStore finance;
    private final OpsStore ops;
    private final MarketingStore marketing;

    public MetricsService(Connection connection) {
        this.connection = connection;
        this.events = new EventStore(connection);
        this.tickets = new TicketStore(connection);
        this.people = new PeopleStore(connection);
        this.finance = new FinanceStore(connection);
        this.ops = new OpsStore(connection);
        this.marketing = new MarketingStore(connection);
    }

    /**
     * Everything about one event, as a single snapshot.
     *
     * <p>Returns a zeroed snapshot rather than null for an unknown event, so a
     * dashboard that is pointed at a deleted event shows empty tiles instead of
     * failing to draw.
     */
    public EventMetrics forEvent(String eventId) throws SQLException {
        Event event = events.findById(eventId);
        if (event == null) {
            return EventMetrics.builder(eventId, "(removed event)").build();
        }
        EventMetrics.Builder builder = EventMetrics.builder(eventId, event.getName())
                .category(event.getCategory().getLabel())
                .capacity(event.getCapacity(), event.getSellableCapacity());

        addTickets(builder, eventId);
        addRegistrations(builder, eventId);
        addFinance(builder, eventId);
        addBudget(builder, eventId);
        addMarketing(builder, eventId);
        addSpeakers(builder, eventId);
        addFeedback(builder, eventId);
        addEngagement(builder, eventId);
        addSustainability(builder, eventId);
        addTrends(builder, eventId);

        builder.roi(computeRoi(eventId, builder));
        return builder.build();
    }

    private void addTickets(EventMetrics.Builder builder, String eventId) throws SQLException {
        int sold = tickets.countByStatus(eventId, TicketStatus.SOLD)
                + tickets.countByStatus(eventId, TicketStatus.CHECKED_IN);
        int reserved = tickets.countByStatus(eventId, TicketStatus.RESERVED);
        int checkedIn = tickets.countByStatus(eventId, TicketStatus.CHECKED_IN);
        int refunded = tickets.countByStatus(eventId, TicketStatus.REFUNDED);
        int voided = tickets.countByStatus(eventId, TicketStatus.VOID);
        int rejected = ops.countRefused(eventId);
        builder.tickets(sold, reserved, checkedIn, refunded, voided).rejectedEntries(rejected);
    }

    private void addRegistrations(EventMetrics.Builder builder, String eventId) throws SQLException {
        int confirmed = people.countRegistrationsByStatus(eventId, RegistrationStatus.CONFIRMED)
                + people.countRegistrationsByStatus(eventId, RegistrationStatus.ATTENDED)
                + people.countRegistrationsByStatus(eventId, RegistrationStatus.WALK_IN);
        int waitlisted = people.countRegistrationsByStatus(eventId, RegistrationStatus.WAITLISTED);
        int cancelled = people.countRegistrationsByStatus(eventId, RegistrationStatus.CANCELLED);
        // Distinct people admitted, not scans. Somebody who wandered out and back in
        // has still been one person, and counting the second scan would inflate
        // attendance above the number of tickets sold.
        int unique = ops.countDistinctAdmitted(eventId);
        builder.registrations(confirmed, waitlisted, cancelled, unique);
    }

    private void addFinance(EventMetrics.Builder builder, String eventId) throws SQLException {
        java.math.BigDecimal gross = finance.sumGrossTicketRevenue(eventId);
        java.math.BigDecimal fees = finance.sumProviderFees(eventId);
        java.math.BigDecimal net = finance.sumSettledInflow(eventId);
        java.math.BigDecimal other = finance.sumOtherInflow(eventId);
        java.math.BigDecimal outstanding = finance.sumOutstandingInflow(eventId)
                .add(finance.sumInvoiceOutstanding(eventId));
        builder.revenue(gross, net, other, outstanding, fees);
    }

    private void addBudget(EventMetrics.Builder builder, String eventId) throws SQLException {
        builder.budget(finance.sumBudgetPlanned(eventId), finance.sumBudgetCommitted(eventId),
                finance.sumBudgetActual(eventId), finance.countOverspentHeadings(eventId));
    }

    private void addMarketing(EventMetrics.Builder builder, String eventId) throws SQLException {
        builder.marketing(marketing.totalSpend(eventId),
                marketing.countCampaigns(eventId));
    }

    private void addSpeakers(EventMetrics.Builder builder, String eventId) throws SQLException {
        int total = people.countSpeakers(eventId);
        int confirmed = people.countSpeakersByStatus(eventId, SpeakerStatus.CONFIRMED)
                + people.countSpeakersByStatus(eventId, SpeakerStatus.CONTRACTED);
        int withdrawn = people.countSpeakersByStatus(eventId, SpeakerStatus.WITHDRAWN);
        builder.speakers(confirmed, total, withdrawn);
    }

    private void addFeedback(EventMetrics.Builder builder, String eventId) throws SQLException {
        int responses = ops.countFeedback(eventId);
        double average = ops.averageRatingOverall(eventId);
        int promoterScore = ops.promoterScore(eventId);
        double recommendation = ops.recommendationRate(eventId);
        builder.feedback(responses, average, promoterScore, recommendation);
    }

    private void addEngagement(EventMetrics.Builder builder, String eventId) throws SQLException {
        builder.engagement(people.countRegistrations(eventId) > 0 ? totalEngagement(eventId) : 0,
                ops.countEngagedAttendees(eventId));
    }

    private int totalEngagement(String eventId) throws SQLException {
        int total = 0;
        for (com.eventsuite.ops.EngagementType type
                : com.eventsuite.ops.EngagementType.values()) {
            total += ops.countEngagementByType(eventId, type);
        }
        return total;
    }

    private void addSustainability(EventMetrics.Builder builder, String eventId) throws SQLException {
        builder.sustainability(ops.totalCarbonKg(eventId), ops.diversionRate(eventId));
    }

    private void addTrends(EventMetrics.Builder builder, String eventId) throws SQLException {
        builder.arrivalTrend(ops.arrivalTrend(eventId))
                .revenueTrend(finance.revenueTrend(eventId));
    }

    /**
     * The return on investment figures for an event.
     *
     * <p>Built from the stored records rather than from the builder's partly filled
     * state, so the calculator sees the same numbers the rest of the metrics do.
     */
    private RoiCalculator computeRoi(String eventId, EventMetrics.Builder builder)
            throws SQLException {
        java.math.BigDecimal gross = finance.sumGrossTicketRevenue(eventId);
        java.math.BigDecimal other = finance.sumOtherInflow(eventId);
        java.math.BigDecimal spent = finance.sumSettledOutflow(eventId);
        java.math.BigDecimal committed = finance.sumBudgetCommitted(eventId);
        java.math.BigDecimal spend = marketing.totalSpend(eventId);
        java.math.BigDecimal fees = finance.sumProviderFees(eventId);
        int sold = tickets.countByStatus(eventId, TicketStatus.SOLD)
                + tickets.countByStatus(eventId, TicketStatus.CHECKED_IN);
        java.math.BigDecimal average = tickets.averagePaid(eventId);
        Event event = events.findById(eventId);
        int capacity = event == null ? 0 : event.getCapacity();
        int attendees = ops.countDistinctAdmitted(eventId);
        return new RoiCalculator(gross, other, spent, committed, spend, fees,
                java.math.BigDecimal.valueOf(attendees), java.math.BigDecimal.valueOf(capacity),
                average);
    }

    /**
     * The attendance curve for an event, in whichever resolution is useful.
     *
     * <p>Hourly for a report, quarter-hourly for the live counter on the night.
     */
    public java.util.List<TrendPoint> attendanceTrend(String eventId, boolean live) throws SQLException {
        return live ? ops.arrivalTrendQuarterHourly(eventId) : ops.arrivalTrend(eventId);
    }

    /** How many events are stored, for the top level dashboard. */
    public int countEvents() throws SQLException {
        return events.countAll();
    }

    /** How many events are in a given state, for the top level dashboard. */
    public int countEventsInState(com.eventsuite.core.EventStatus status) throws SQLException {
        return events.countByStatus(status);
    }

    /** Total money taken across every event, for the top level dashboard. */
    public java.math.BigDecimal totalRevenueAcrossAllEvents() throws SQLException {
        java.math.BigDecimal total = com.eventsuite.finance.Money.ZERO;
        for (Event event : events.findAll()) {
            total = com.eventsuite.finance.Money.sum(total,
                    finance.sumSettledInflow(event.getId()));
        }
        return total;
    }

    /** Total money spent across every event. */
    public java.math.BigDecimal totalSpendAcrossAllEvents() throws SQLException {
        java.math.BigDecimal total = com.eventsuite.finance.Money.ZERO;
        for (Event event : events.findAll()) {
            total = com.eventsuite.finance.Money.sum(total,
                    finance.sumSettledOutflow(event.getId()));
        }
        return total;
    }

    /** Total people admitted across every event. */
    public int totalAttendanceAcrossAllEvents() throws SQLException {
        int total = 0;
        for (Event event : events.findAll()) {
            total += ops.countDistinctAdmitted(event.getId());
        }
        return total;
    }

    /** Total tickets sold across every event. */
    public int totalTicketsSoldAcrossAllEvents() throws SQLException {
        int total = 0;
        for (Event event : events.findAll()) {
            total += tickets.countByStatus(event.getId(), TicketStatus.SOLD)
                    + tickets.countByStatus(event.getId(), TicketStatus.CHECKED_IN);
        }
        return total;
    }

    /** Total outstanding money across every event. */
    public java.math.BigDecimal totalOutstandingAcrossAllEvents() throws SQLException {
        java.math.BigDecimal total = com.eventsuite.finance.Money.ZERO;
        for (Event event : events.findAll()) {
            total = com.eventsuite.finance.Money.sum(total,
                    finance.sumOutstandingInflow(event.getId())
                            .add(finance.sumInvoiceOutstanding(event.getId())));
        }
        return total;
    }

    /** Budget headings that have gone over, across every event. */
    public int countOverspentHeadingsAcrossAllEvents() throws SQLException {
        int total = 0;
        for (Event event : events.findAll()) {
            total += finance.countOverspentHeadings(event.getId());
        }
        return total;
    }

    /**
     * Events in a date window, for the calendar strip.
     *
     * <p>Overlapping rather than starting, so a conference in progress appears on
     * every day it is running rather than only on its first.
     */
    public java.util.List<Event> eventsBetween(LocalDate from, LocalDate to) throws SQLException {
        return events.findOverlapping(from, to);
    }

    /**
     * Events that need attention: over budget, sold out, running, or about to.
     *
     * <p>The list on the front screen. Each entry is something that changes what has to
     * be done today, which is what makes it worth showing rather than the full list.
     */
    public java.util.List<String> attentionItems(String eventId, LocalDate today) throws SQLException {
        EventMetrics metrics = forEvent(eventId);
        java.util.List<String> items = new java.util.ArrayList<>();
        if (metrics.isOverBudget()) {
            items.add("Budget: " + metrics.getBudgetHeadingsOverspent()
                    + " heading(s) over their allocation.");
        }
        if (metrics.getTicketsRemaining() == 0) {
            items.add("Sold out. Check the door plan for a full house.");
        }
        if (metrics.getSpeakersUnconfirmed() > 0) {
            items.add("Speakers: " + metrics.getSpeakersUnconfirmed() + " not yet confirmed.");
        }
        if (metrics.getRegistrationsWaitlisted() > 0) {
            items.add("Waiting list: " + metrics.getRegistrationsWaitlisted()
                    + " people could be offered a place.");
        }
        if (metrics.getRejectedEntries() > 0) {
            items.add("Door: " + metrics.getRejectedEntries() + " arrival(s) had a problem.");
        }
        if (metrics.hasPoorFeedback()) {
            items.add(String.format(java.util.Locale.UK, "Feedback averages %.1f out of 5.",
                    metrics.getAverageFeedback()));
        }
        if (metrics.getBudgetRemaining().signum() == 0
                && metrics.getBudgetPlanned().signum() > 0) {
            items.add("Budget: fully committed with nothing left to spend.");
        }
        Event event = events.findById(eventId);
        if (event != null) {
            long days = event.getDaysUntil(today);
            if (days >= 0 && days <= 14 && metrics.getTicketsSold() == 0) {
                items.add(days == 0
                        ? "The event is today and nothing has sold."
                        : days + " day(s) away and nothing has sold.");
            }
        }
        return items;
    }

    /**
     * The budget headings closest to their limit, for the pre-event warnings.
     *
     * <p>Ordered by how little is left rather than by heading, so the one about to run
     * out is at the top. Headings that are already over sort first, because they are
     * the ones that need a decision.
     */
    public java.util.List<BudgetLine> budgetWarnings(String eventId) throws SQLException {
        java.util.List<BudgetLine> warnings = new java.util.ArrayList<>();
        for (BudgetLine line : finance.findBudgetLines(eventId)) {
            if (line.isOverspent() || line.isNearLimit()) {
                warnings.add(line);
            }
        }
        warnings.sort((left, right) -> {
            BigDecimalCompare byOverspend = new BigDecimalCompare(
                    left.isOverspent() ? 0 : 1, right.isOverspent() ? 0 : 1);
            if (byOverspend.compare() != 0) {
                return byOverspend.compare();
            }
            return left.getRemaining().compareTo(right.getRemaining());
        });
        return warnings;
    }

    /** A tiny two-field comparison, so the sort above reads in words. */
    private static final class BigDecimalCompare {
        private final int left;
        private final int right;

        BigDecimalCompare(int left, int right) {
            this.left = left;
            this.right = right;
        }

        int compare() {
            return Integer.compare(left, right);
        }
    }
}
