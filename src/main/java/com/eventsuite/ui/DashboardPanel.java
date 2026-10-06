package com.eventsuite.ui;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.core.Event;
import com.eventsuite.core.EventCategory;
import com.eventsuite.core.EventStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.service.EventSuite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The front screen: what is happening, right now, across every event.
 *
 * <p>Two levels, because two questions are being asked. With no event selected the
 * screen answers "how is the whole book of events doing" — totals across every
 * registered event, the categories they fall into, and anything that needs attention
 * today. With an event selected it answers "how is this one doing", with the ticket
 * curve, the budget and the return on spend.
 *
 * <p>Both refresh on a timer owned by {@link MainWindow}. The panels are not rebuilt,
 * so the scroll position and the selected row survive, and the reads happen off the
 * event thread so a slow query cannot freeze the window.
 */
public final class DashboardPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private final Charts.Line arrivalCurve = new Charts.Line();
    private final Charts.Line revenueCurve = new Charts.Line();
    private final Charts.Donut categorySplit = new Charts.Donut();
    private final Charts.Donut accessSplit = new Charts.Donut();
    private final Charts.Bars budgetByHeading = new Charts.Bars();
    private final Charts.Gauge returnGauge = new Charts.Gauge();

    private JPanel tileRowOne;
    private JPanel tileRowTwo;
    private JPanel chartPanel;
    private JPanel attentionPanel;
    private JPanel healthPanel;

    public DashboardPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            buildPortfolio();
        } else {
            buildForEvent(event);
        }
    }

    // ---------------------------------------------------------------- portfolio

    /** The whole-book view: every event, grouped by what kind of thing it is. */
    private void buildPortfolio() {
        tileRowOne = new JPanel(new GridLayout(0, 4, 12, 12));
        tileRowTwo = new JPanel(new GridLayout(0, 4, 12, 12));
        for (JPanel row : new JPanel[]{tileRowOne, tileRowTwo}) {
            row.setOpaque(false);
        }

        categorySplit.with(new LinkedHashMap<>()).centredOn("");
        attentionPanel = new JPanel();
        attentionPanel.setOpaque(false);
        attentionPanel.setLayout(new javax.swing.BoxLayout(attentionPanel,
                javax.swing.BoxLayout.Y_AXIS));

        JPanel body = Ui.column(
                tileRowOne,
                tileRowTwo,
                Ui.gap(14),
                Ui.cardGrid(2,
                        Ui.describedCard("Events by category",
                                "How the calendar is spread across the kinds of event you run.",
                                categorySplit),
                        Ui.describedCard("Needs attention today",
                                "Anything that changes what has to be done before tomorrow.",
                                attentionPanel)));

        fixedScreen("Dashboard",
                "Every registered event, and anything that needs a decision today.",
                body);
        setSubtitle("");
        reload();
    }

    // ---------------------------------------------------------------- one event

    private void buildForEvent(Event event) {
        tileRowOne = new JPanel(new GridLayout(0, 4, 12, 12));
        tileRowTwo = new JPanel(new GridLayout(0, 4, 12, 12));
        for (JPanel row : new JPanel[]{tileRowOne, tileRowTwo}) {
            row.setOpaque(false);
        }

        chartPanel = new JPanel(new GridLayout(2, 1, 0, 14));
        chartPanel.setOpaque(false);
        chartPanel.add(Ui.describedCard("Arrivals", "People through the door, by the hour.", arrivalCurve));
        chartPanel.add(Ui.describedCard("Ticket revenue", "Running takings as sales come in.", revenueCurve));

        JPanel moneyPanel = new JPanel(new GridLayout(0, 2, 14, 0));
        moneyPanel.setOpaque(false);
        moneyPanel.add(Ui.describedCard("Return on spend",
                "Ticket money per pound of cost. One is break-even.", returnGauge));
        moneyPanel.add(Ui.describedCard("Budget by heading",
                "Planned against committed and spent.", budgetByHeading));

        healthPanel = new JPanel();
        healthPanel.setOpaque(false);
        healthPanel.setLayout(new javax.swing.BoxLayout(healthPanel,
                javax.swing.BoxLayout.Y_AXIS));

        attentionPanel = new JPanel();
        attentionPanel.setOpaque(false);
        attentionPanel.setLayout(new javax.swing.BoxLayout(attentionPanel,
                javax.swing.BoxLayout.Y_AXIS));

        JPanel body = Ui.column(
                tileRowOne,
                tileRowTwo,
                Ui.gap(14),
                Ui.describedCard("Needs attention", "What to do about this event next.", attentionPanel),
                Ui.gap(14),
                chartPanel,
                Ui.gap(14),
                moneyPanel,
                Ui.gap(14),
                Ui.cardGrid(2,
                        Ui.describedCard("Health", "What the numbers add up to.", healthPanel),
                        Ui.describedCard("Tickets by access level",
                                "Who holds what kind of ticket.", accessSplit)));

        fixedScreen(event.getName(),
                event.getCategory().getLabel() + "  \u00b7  " + event.getDateRange()
                        + "  \u00b7  " + describeVenue(event),
                body);
        reload();
    }

    private String describeVenue(Event event) {
        return event.getVenueName().isBlank() ? "No venue set" : event.getVenueName();
    }

    // ---------------------------------------------------------------- refresh

    @Override
    protected void reload() {
        Event event = currentEvent();
        try {
            if (event == null) {
                reloadPortfolio();
            } else {
                reloadEvent(event);
            }
        } catch (SQLException failure) {
            showProblem(failure);
        }
    }

    /**
     * Fills the portfolio tiles.
     *
     * <p>Every figure here is a count or a total across all events, so a single event
     * having a bad day cannot be read as the whole book having one.
     */
    private void reloadPortfolio() throws SQLException {
        EventSuite suite = suite();
        int eventCount = suite.metrics().countEvents();
        int onSale = suite.metrics().countEventsInState(EventStatus.ON_SALE);
        int planning = suite.metrics().countEventsInState(EventStatus.PLANNING)
                + suite.metrics().countEventsInState(EventStatus.DRAFT);
        int completed = suite.metrics().countEventsInState(EventStatus.COMPLETED);

        BigDecimal revenue = suite.metrics().totalRevenueAcrossAllEvents();
        BigDecimal spend = suite.metrics().totalSpendAcrossAllEvents();
        BigDecimal outstanding = suite.metrics().totalOutstandingAcrossAllEvents();
        int sold = suite.metrics().totalTicketsSoldAcrossAllEvents();
        int attended = suite.metrics().totalAttendanceAcrossAllEvents();
        int overspent = suite.metrics().countOverspentHeadingsAcrossAllEvents();

        BigDecimal profit = Money.subtract(revenue, spend);

        clear(tileRowOne);
        tileRowOne.add(Ui.tile("Registered events",
                String.valueOf(eventCount),
                onSale + " on sale, " + planning + " in planning"));
        tileRowOne.add(Ui.tile("Tickets sold",
                NumberFormat.decimal(sold),
                attended + " people through the door"));
        tileRowOne.add(Ui.tile("Revenue collected",
                Money.compact(revenue),
                Money.compact(spend) + " spent", colourFor(profit.signum())));
        tileRowOne.add(Ui.tile("Money outstanding",
                Money.compact(outstanding),
                outstanding.signum() > 0 ? "Still owed" : "All settled",
                outstanding.signum() > 0 ? Theme.current().warning()
                        : Theme.current().success()));

        clear(tileRowTwo);
        tileRowTwo.add(Ui.tile("Completed events",
                String.valueOf(completed),
                "Ready for reconciliation"));
        tileRowTwo.add(Ui.tile("Budget headings over",
                String.valueOf(overspent),
                overspent == 0 ? "Nothing overspent" : "Needs a decision",
                healthColour(overspent > 0)));
        tileRowTwo.add(Ui.tileWithBar("Attendance rate",
                attendanceLabel(sold, attended),
                "Of everyone who bought a ticket",
                sold == 0 ? 0 : (int) Math.round(100.0 * attended / sold)));
        tileRowTwo.add(Ui.tile("Next event",
                nextEventLabel(),
                "The next thing on the calendar"));

        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (EventCategory category : EventCategory.values()) {
            int count = suite.events().findByCategory(category).size();
            if (count > 0) {
                byCategory.put(category.getLabel(), (double) count);
            }
        }
        categorySplit.with(byCategory).centredOn(String.valueOf(eventCount));

        fillAttention();
    }

    private String attendanceLabel(int sold, int attended) {
        if (sold == 0) {
            return "\u2014";
        }
        return Math.round(100.0 * attended / sold) + "%";
    }

    private String nextEventLabel() {
        try {
            List<Event> upcoming = suite().upcoming();
            if (upcoming.isEmpty()) {
                return "Nothing";
            }
            Event next = upcoming.get(0);
            long days = next.getDaysUntil(LocalDate.now());
            if (days == 0) {
                return "Today";
            }
            if (days == 1) {
                return "Tomorrow";
            }
            return days + " days";
        } catch (SQLException unreadable) {
            return "\u2014";
        }
    }

    /**
     * Lists what needs a decision, across every event.
     *
     * <p>Capped at six. A list of forty items is not a list somebody reads, and the
     * seventh item is never the one that mattered anyway.
     */
    private void fillAttention() throws SQLException {
        clear(attentionPanel);
        List<String> items = new ArrayList<>();
        for (Event event : suite().events().findAll()) {
            if (!event.getStatus().isSettled()) {
                items.addAll(suite().attentionItems(event.getId()));
            }
        }
        if (items.isEmpty()) {
            attentionPanel.add(Ui.emptyState("Nothing needs attention.",
                    "No budget is overspent, no speaker is missing and nothing is overdue."));
            attentionPanel.setBorder(BorderFactory.createEmptyBorder());
            return;
        }
        int shown = Math.min(6, items.size());
        for (int index = 0; index < shown; index++) {
            attentionPanel.add(Ui.bar(items.get(index), 100,
                    Theme.current().warning()));
            attentionPanel.add(javax.swing.Box.createVerticalStrut(8));
        }
        if (items.size() > shown) {
            attentionPanel.add(Ui.muted("and " + (items.size() - shown) + " more"));
        }
    }

    /** Fills the tiles and charts for one event. */
    private void reloadEvent(Event event) throws SQLException {
        EventSuite suite = suite();
        EventMetrics metrics = suite.metrics().forEvent(event.getId());

        clear(tileRowOne);
        tileRowOne.add(Ui.tileWithBar("Sold",
                metrics.getTicketsSold() + " / " + metrics.getSellableCapacity(),
                metrics.getTicketsRemaining() + " left to sell",
                metrics.getSellThroughPercent().intValue()));
        tileRowOne.add(Ui.tileWithBar("Through the door",
                String.valueOf(metrics.getTicketsCheckedIn()),
                metrics.getTicketsCheckedIn() == 0 ? "Doors not open yet"
                        : Math.round(metrics.getAttendanceRate().doubleValue()) + "% of buyers",
                metrics.getAttendanceRate().intValue()));
        tileRowOne.add(Ui.tile("Takings",
                Money.compact(metrics.getNetRevenue()),
                Money.compact(metrics.getProviderFees()) + " in processing fees"));
        tileRowOne.add(Ui.tile("Budget left",
                Money.compact(metrics.getBudgetRemaining()),
                metrics.getBudgetUsedPercent().intValue() + "% committed or spent",
                healthColour(metrics.isOverBudget())));

        clear(tileRowTwo);
        tileRowTwo.add(Ui.tile("Registrations",
                String.valueOf(metrics.getRegistrationsConfirmed()),
                metrics.getRegistrationsWaitlisted() > 0
                        ? metrics.getRegistrationsWaitlisted() + " on the waiting list"
                        : "No waiting list"));
        tileRowTwo.add(Ui.tile("Feedback",
                metrics.getFeedbackResponses() == 0 ? "\u2014"
                        : String.format(java.util.Locale.UK, "%.1f", metrics.getAverageFeedback()),
                metrics.getFeedbackResponses() == 0 ? "None returned yet"
                        : "out of 5, from " + metrics.getFeedbackResponses(),
                healthColour(metrics.hasPoorFeedback())));
        tileRowTwo.add(Ui.tile("Speakers",
                metrics.getSpeakersConfirmed() + " / " + metrics.getSpeakersTotal(),
                metrics.getSpeakersUnconfirmed() == 0
                        ? "Lineup complete"
                        : metrics.getSpeakersUnconfirmed() + " still to confirm",
                healthColour(metrics.getSpeakersUnconfirmed() > 0)));
        tileRowTwo.add(Ui.tile("Carbon",
                metrics.getCarbonLabel(),
                metrics.getRecyclingRate().signum() > 0
                        ? metrics.getRecyclingRate() + "% recycled" : "Not yet recorded"));

        arrivalCurve.with(suite.attendanceTrend(event.getId(), false))
                .captioned("arrivals per hour");
        revenueCurve.with(metrics.getRevenueTrend()).captioned("cumulative takings");

        returnGauge.showing(clampGauge(metrics.getRoi().getTicketReturnMultiple()),
                        2.0).labelled(metrics.getRoi().getReturnVerdict())
                .captioned("ticket money per pound spent");

        Map<String, Double> budget = new LinkedHashMap<>();
        for (com.eventsuite.finance.BudgetLine line
                : suite.finance().findBudgetLines(event.getId())) {
            budget.put(line.getCategory().getLabel(),
                    line.getPlanned().doubleValue());
        }
        budgetByHeading.with(budget).measuredIn("\u00a3 planned");

        Map<String, Double> access = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry
                : suite.tickets().soldCountByTierName(event.getId()).entrySet()) {
            access.put(entry.getKey(), entry.getValue().doubleValue());
        }
        accessSplit.with(access).centredOn(String.valueOf(metrics.getTicketsSold()));

        clear(healthPanel);
        for (String line : suite.attentionItems(event.getId())) {
            healthPanel.add(Ui.bar(line, 100, Theme.current().warning()));
            healthPanel.add(javax.swing.Box.createVerticalStrut(8));
        }
        if (suite.attentionItems(event.getId()).isEmpty()) {
            healthPanel.add(Ui.emptyState("Nothing outstanding.",
                    "Budgets are within their allocations and the lineup is complete."));
        }

        clear(attentionPanel);
        List<String> items = suite.attentionItems(event.getId());
        if (items.isEmpty()) {
            attentionPanel.add(Ui.emptyState("This event is in good order.",
                    "Nothing is overspent, overdue or unconfirmed."));
        } else {
            for (String item : items) {
                attentionPanel.add(Ui.bar(item, 100, Theme.current().warning()));
                attentionPanel.add(javax.swing.Box.createVerticalStrut(7));
            }
        }

        setSubtitle(event.getStatus().getLabel() + "  \u00b7  "
                + metrics.getHealthSummary());
    }

    /**
     * Keeps the gauge inside its scale.
     *
     * <p>A return of eleven times would otherwise be drawn as a full ring, which reads
     * as "at maximum" rather than as the exceptional result it is.
     */
    private double clampGauge(double value) {
        if (Double.isInfinite(value)) {
            return 2.0;
        }
        return Math.max(0, Math.min(2.0, value));
    }

    // ---------------------------------------------------------------- helpers

    /** Empties a container so it can be refilled. */
    static void clear(JPanel panel) {
        if (panel == null) {
            return;
        }
        panel.removeAll();
    }

    private Color colourFor(int sign) {
        if (sign > 0) {
            return Theme.current().success();
        }
        return sign < 0 ? Theme.current().danger() : Theme.current().muted();
    }

    /**
     * Replaces the screen with an explanation when a read fails.
     *
     * <p>Silent failure would leave stale figures on screen looking current, which is
     * worse than an empty screen that says something went wrong.
     */
    private void showProblem(SQLException failure) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.current().page());
        JLabel message = new JLabel(
                "<html><div style='width:420px'>The figures could not be loaded.<br><br>"
                        + escape(failure.getMessage()) + "</div></html>",
                JLabel.CENTER);
        message.setFont(Theme.Type.body());
        message.setForeground(Theme.current().danger());
        panel.add(message, BorderLayout.CENTER);
        removeAll();
        add(panel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private String escape(String text) {
        return text == null ? "The database did not respond."
                : text.replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Thousands separators, so a count of 9000 does not read as 9000. */
    static final class NumberFormat {
        private NumberFormat() {
        }

        static String decimal(long value) {
            return String.format(java.util.Locale.UK, "%,d", value);
        }
    }
}
