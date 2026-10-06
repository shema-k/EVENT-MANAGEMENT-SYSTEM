package com.eventsuite.ui;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.analytics.Feedback;
import com.eventsuite.analytics.FeedbackTopic;
import com.eventsuite.analytics.ReconciliationReport;
import com.eventsuite.analytics.RoiCalculator;
import com.eventsuite.core.Event;
import com.eventsuite.finance.Invoice;
import com.eventsuite.finance.InvoiceStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.finance.Payment;
import com.eventsuite.ops.EngagementRecord;
import com.eventsuite.ops.EngagementType;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.TicketStatus;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * After the event: what people thought, what it cost, and whether it worked.
 *
 * <p>The three questions an organiser asks once the chairs are stacked, and they are
 * answered together because they inform each other. A high satisfaction score on an
 * event that lost money is a different proposition from the same score on one that
 * paid for itself, and neither figure alone says which happened.
 *
 * <p>Feedback is broken out by topic rather than shown as one average, because an
 * average of four tells you there was a problem without telling you where. Content
 * rated four and parking rated one average to two and a half, which looks like general
 * displeasure and is actually one fixable thing.
 */
public final class AnalyticsPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private final Charts.Bars topicRatings = new Charts.Bars();
    private final Charts.Donut ratingSpread = new Charts.Donut();
    private final Charts.Gauge roiGauge = new Charts.Gauge();
    private final Charts.Bars revenueByChannel = new Charts.Bars();

    private JPanel tileArea;
    private JPanel commentArea;
    private JPanel reconciliationArea;
    private JTable invoiceTable;
    private TableData invoiceData;
    private JTable complaintTable;
    private TableData complaintData;

    public AnalyticsPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Analytics", "Choose an event in the header to see how it went.",
                    Ui.emptyState("No event selected.",
                            "Feedback, return on spend and the reconciliation all belong"
                                    + " to one event."));
            return;
        }

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);

        commentArea = new JPanel();
        commentArea.setOpaque(false);
        commentArea.setLayout(new BoxLayout(commentArea, BoxLayout.Y_AXIS));

        reconciliationArea = new JPanel();
        reconciliationArea.setOpaque(false);
        reconciliationArea.setLayout(new BoxLayout(reconciliationArea, BoxLayout.Y_AXIS));

        invoiceData = TableData.with("Invoice", "Billed to", "Total", "Paid", "Due", "Status")
                .withMoney(2, 3).withStatus(5);
        invoiceTable = tableOf(invoiceData);

        complaintData = TableData.with("Topic", "Rating", "Who", "What they said");
        complaintTable = tableOf(complaintData);

        JPanel money = new JPanel(new GridLayout(0, 2, 14, 0));
        money.setOpaque(false);
        money.add(Ui.describedCard("Return on spend",
                "Ticket money per pound of everything it cost. One is break-even.",
                roiGauge));
        money.add(Ui.describedCard("Where the money came in by",
                "Income split by how it was taken.", revenueByChannel));

        JPanel feedback = new JPanel(new GridLayout(0, 2, 14, 0));
        feedback.setOpaque(false);
        feedback.add(Ui.describedCard("Satisfaction by topic",
                "What people rated, out of five.", topicRatings));
        feedback.add(Ui.describedCard("Overall ratings",
                "How the overall score was distributed.", ratingSpread));

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Write the event report", this::writeReport),
                        Ui.button("Refresh", this::reload),
                        Ui.muted("The report is a text file of attendance, money,"
                                + " feedback and the reconciliation.")),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                money,
                Ui.gap(14),
                feedback,
                Ui.gap(14),
                Ui.cardGrid(2,
                        Ui.describedCard("What people complained about",
                                "Lowest rated responses first, with the comments.",
                                Ui.scroll(complaintTable)),
                        Ui.describedCard("Reconciliation",
                                "Whether the books balance, and what is still owed.",
                                reconciliationArea)),
                Ui.gap(14),
                Ui.describedCard("Invoices", "What was billed, what has been paid.",
                        Ui.scroll(invoiceTable)),
                Ui.gap(14),
                Ui.describedCard("All feedback", "Every comment that was left.",
                        commentArea));
        screen("Analytics", event.getName() + "  \u00b7  " + event.getDateRange(), body);
        reload();
    }

    @Override
    protected void reload() {
        Event event = currentEvent();
        if (event == null || tileArea == null) {
            return;
        }
        try {
                            fillTiles(event);
                            fillFeedback(event);
                            fillComplaints(event);
                            fillInvoices(event);
                            fillReconciliation(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the analytics", failure.getMessage());
        }
    }

    private void fillTiles(Event event) throws SQLException {
        EventMetrics metrics = suite().metrics().forEvent(event.getId());
        RoiCalculator roi = metrics.getRoi();
        DashboardPanel.clear(tileArea);

        tileArea.add(Ui.tile("Attendance",
                metrics.getUniqueAttendees() + " / " + metrics.getTicketsSold(),
                metrics.getTicketsCheckedIn() == 0 ? "Nobody checked in"
                        : Math.round(metrics.getAttendanceRate().doubleValue())
                                + "% of ticket holders came"));
        tileArea.add(Ui.tile("Would recommend",
                metrics.getFeedbackResponses() == 0 ? "\u2014"
                        : Math.round(metrics.getRecommendationRate() * 100) + "%",
                metrics.getPromoterScore() >= 0 ? "Promoter score " + metrics.getPromoterScore()
                        : "Promoter score " + metrics.getPromoterScore(),
                healthColour(metrics.hasPoorFeedback())));
        tileArea.add(Ui.tile("Profit",
                Money.compact(roi.getProfit()),
                roi.getReturnVerdict() + " \u00b7 break-even at "
                        + roi.getBreakEvenAttendees() + " people",
                roi.isProfitable() ? Theme.current().success() : Theme.current().danger()));
        tileArea.add(Ui.tile("Cost per head",
                Money.format(roi.getCostPerAttendee()),
                roi.getRevenuePerAttendee().signum() > 0
                        ? "brought in " + Money.format(roi.getRevenuePerAttendee())
                        : "nobody came"));
    }

    /**
     * Feedback, broken out by topic.
     *
     * <p>A bar per topic rather than one overall figure, because the whole point of
     * asking several questions is that they disagree.
     */
    private void fillFeedback(Event event) throws SQLException {
        Map<String, Double> byTopic = new LinkedHashMap<>();
        for (FeedbackTopic topic : FeedbackTopic.values()) {
            int answered = suite().ops().countFeedbackByTopic(event.getId(), topic);
            if (answered > 0) {
                byTopic.put(topic.getLabel(),
                        round(suite().ops().averageRating(event.getId(), topic)));
            }
        }
        topicRatings.with(byTopic).measuredIn("of 5")
                .monochrome(Theme.current().accent());

        Map<String, Double> spread = new LinkedHashMap<>();
        for (int rating = 5; rating >= 1; rating--) {
            int count = 0;
            for (Feedback response : suite().ops()
                    .findFeedbackByTopic(event.getId(), FeedbackTopic.OVERALL)) {
                if (response.getRating() == rating) {
                    count++;
                }
            }
            if (count > 0) {
                spread.put(rating + " out of 5", (double) count);
            }
        }
        ratingSpread.with(spread)
                .centredOn(byTopic.isEmpty() ? "" : String.valueOf(
                        suite().ops().countFeedback(event.getId())));

        DashboardPanel.clear(commentArea);
        List<Feedback> withComments = suite().ops().findWithComments(event.getId());
        if (withComments.isEmpty()) {
            commentArea.add(Ui.emptyState("No written comments.",
                    "Ratings have come back but nobody has written anything."));
        } else {
            for (Feedback response : withComments) {
                commentArea.add(commentBlock(response));
                commentArea.add(Box.createVerticalStrut(10));
            }
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** One comment, with its rating and who left it. */
    private JComponent commentBlock(Feedback response) {
        JPanel panel = new JPanel(new BorderLayout(10, 6));
        panel.setBackground(Theme.current().field());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 200));

        JTextArea text = Ui.textArea(2);
        text.setText(response.getComment());
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder());
        panel.add(text, BorderLayout.CENTER);

        JPanel byline = new JPanel();
        byline.setOpaque(false);
        byline.setLayout(new BoxLayout(byline, BoxLayout.Y_AXIS));
        JLabel rating = Ui.status(response.getRating() + "/5 " + response.getRatingLabel());
        rating.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        byline.add(rating);
        byline.add(Ui.muted(response.getTopic().getLabel()));
        byline.add(Ui.muted(response.getAttendeeName().isEmpty() ? "Anonymous"
                : response.getAttendeeName()));
        panel.add(byline, BorderLayout.EAST);
        return panel;
    }

    /** The worst responses, which is what somebody reads first. */
    private void fillComplaints(Event event) throws SQLException {
        complaintData.rowsAsList().clear();
        for (Feedback response : suite().ops().findComplaints(event.getId(), 25)) {
            complaintData.add(response.getTopic().getLabel(),
                    response.getRating() + "/5",
                    response.getAttendeeName().isEmpty() ? "Anonymous"
                            : response.getAttendeeName(),
                    Ui.ellipsise(response.getComment(), 90));
        }
        complaintTable.repaint();
    }

    private void fillInvoices(Event event) throws SQLException {
        invoiceData.rowsAsList().clear();
        for (Invoice invoice : suite().finance().findInvoices(event.getId())) {
            invoiceData.add(invoice.getNumber(), invoice.getBilledTo(),
                    Money.format(invoice.getTotal()),
                    Money.format(invoice.getPaidAmount()),
                    invoice.getDueOn().toString(),
                    invoice.getStatus().getLabel());
        }
        invoiceTable.repaint();
    }

    /**
     * The reconciliation.
     *
     * <p>The status line first, then the discrepancies with what to do about each, then
     * what is still owed. A reconciliation that only says "balanced" or "out by
     * £412" without saying which invoice is not much help to whoever has to chase it.
     */
    private void fillReconciliation(Event event) throws SQLException {
        ReconciliationReport report = suite().reconcile(event);
        DashboardPanel.clear(reconciliationArea);

        JLabel status = new JLabel(report.getStatus().getLabel());
        status.setFont(Theme.Type.metricSmall());
        status.setForeground(report.getStatus().isAcceptable() ? Theme.current().success()
                : Theme.current().danger());
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        reconciliationArea.add(status);
        reconciliationArea.add(Box.createVerticalStrut(6));
        reconciliationArea.add(Ui.muted(report.getSummaryLine()));
        reconciliationArea.add(Box.createVerticalStrut(12));

        if (report.getDiscrepancies().isEmpty()) {
            reconciliationArea.add(Ui.muted("No discrepancies. Every payment matches what"
                    + " the tickets and invoices say should have been taken."));
        } else {
            for (ReconciliationReport.Discrepancy discrepancy : report.getDiscrepancies()) {
                JPanel block = new JPanel();
                block.setOpaque(false);
                block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
                block.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel amount = Ui.signedMoney(discrepancy.getVariance());
                amount.setAlignmentX(Component.LEFT_ALIGNMENT);
                block.add(amount);
                JLabel what = Ui.muted(discrepancy.getDisplayLine());
                what.setAlignmentX(Component.LEFT_ALIGNMENT);
                block.add(what);
                JLabel action = Ui.muted("\u2192 " + discrepancy.getSuggestedAction());
                action.setForeground(Theme.current().warning());
                action.setAlignmentX(Component.LEFT_ALIGNMENT);
                block.add(action);
                reconciliationArea.add(block);
                reconciliationArea.add(Box.createVerticalStrut(10));
            }
        }

        reconciliationArea.add(Box.createVerticalStrut(6));
        reconciliationArea.add(Ui.bar("Invoices settled",
                report.getInvoiceSettlementRate().intValue(), Theme.current().success()));
        reconciliationArea.add(Box.createVerticalStrut(6));
        reconciliationArea.add(Ui.bar("Money still owed",
                report.getMoneyOutstanding().signum() == 0 ? 100 : 0,
                report.getMoneyOutstanding().signum() == 0 ? Theme.current().success()
                        : Theme.current().warning()));
    }

    /** Writes the end-of-event report and says where it went. */
    public void writeReport() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        try {
            String path = suite().writeEventReport(event);
            setSubtitle("Report written to " + path);
            Ui.dialog(this, "Report written", "The event report is at:\n\n" + path
                    + "\n\nIt covers attendance, money, feedback, sustainability and the"
                    + " reconciliation.");
        } catch (java.io.IOException | SQLException failure) {
            Ui.dialog(this, "Could not write the report", failure.getMessage());
        }
    }
}
