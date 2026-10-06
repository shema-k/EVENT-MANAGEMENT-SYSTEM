package com.eventsuite.ui;

import com.eventsuite.core.Event;
import com.eventsuite.ops.EngagementRecord;
import com.eventsuite.ops.EngagementType;
import com.eventsuite.analytics.SustainabilityMetric;
import com.eventsuite.service.EventSuite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * What people actually did, and what the event did to the planet.
 *
 * <p>Two things that share a screen because both are measured rather than felt, and
 * both are the numbers an organisation is asked for afterwards. Engagement says whether
 * anybody took part; sustainability says what it cost environmentally. Neither is a
 * feeling and both are easy to claim without evidence, which is why each is recorded
 * as an actual figure here.
 *
 * <p>Sustainability carries the conversion factors rather than a single headline
 * number, so the total is computable and the assumptions are visible. A footprint
 * quoted without its factors is an assertion rather than a measurement.
 */
public final class EngagementPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private final Charts.Bars byType = new Charts.Bars();
    private final Charts.Bars carbonBySource = new Charts.Bars();

    private JPanel tileArea;
    private JPanel typeArea;
    private JPanel sustainabilityArea;
    private JTable recordTable;
    private TableData recordData;

    public EngagementPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Engagement and impact",
                    "Choose an event in the header to see what people took part in.",
                    Ui.emptyState("No event selected.",
                            "Engagement and sustainability belong to an event."));
            return;
        }

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);
        typeArea = new JPanel();
        typeArea.setOpaque(false);
        typeArea.setLayout(new BoxLayout(typeArea, BoxLayout.Y_AXIS));
        sustainabilityArea = new JPanel();
        sustainabilityArea.setOpaque(false);
        sustainabilityArea.setLayout(new BoxLayout(sustainabilityArea, BoxLayout.Y_AXIS));
        recordData = TableData.with("When", "Kind", "About", "Detail", "Score");
        recordTable = tableOf(recordData);

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Record something an attendee did", this::showRecordForm),
                        Ui.button("Add a feedback response", this::showFeedbackForm),
                        Ui.button("Record an environmental measure", this::showMeasureForm),
                        Ui.button("Refresh", this::reload)),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                Ui.cardGrid(2,
                        Ui.describedCard("Engagement by kind",
                                "What people actually did, counted.", byType),
                        Ui.describedCard("Footprint by measure",
                                "Where the carbon came from.", carbonBySource)),
                Ui.gap(14),
                Ui.describedCard("Sustainability",
                        "The figures behind the footprint, and where they came from.",
                        sustainabilityArea),
                Ui.gap(14),
                Ui.describedCard("Records", "Everything logged, newest first.",
                        Ui.scroll(recordTable)));
        screen("Engagement and impact", event.getName() + "  \u00b7  " + event.getDateRange(),
                body);
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
                            fillTypes(event);
                            fillSustainability(event);
                            fillRecords(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the figures", failure.getMessage());
        }
    }

    private void fillTiles(Event event) throws SQLException {
        com.eventsuite.analytics.EventMetrics metrics = suite().metrics()
                .forEvent(event.getId());
        DashboardPanel.clear(tileArea);

        int total = 0;
        for (EngagementType type : EngagementType.values()) {
            total += suite().ops().countEngagementByType(event.getId(), type);
        }
        int engaged = suite().ops().countEngagedAttendees(event.getId());
        double score = suite().ops().averageEngagementScore(event.getId());

        tileArea.add(Ui.tile("Records",
                String.valueOf(total),
                engaged == 0 ? "nothing attributed to a person yet"
                        : "from " + engaged + " attendees"));
        tileArea.add(Ui.tileWithBar("Took part",
                metrics.getUniqueAttendees() == 0 ? "\u2014"
                        : Math.round(metrics.getEngagementRate().doubleValue()) + "%",
                engaged + " of " + metrics.getUniqueAttendees() + " people",
                metrics.getEngagementRate().intValue()));
        tileArea.add(Ui.tile("Average score",
                score == 0 ? "\u2014" : String.format(java.util.Locale.UK, "%.1f", score),
                "out of 10, from the scored records",
                score > 0 && score < 6 ? Theme.current().warning() : Theme.current().text()));
        tileArea.add(Ui.tile("Footprint",
                metrics.getCarbonLabel(),
                metrics.getRecyclingRate().signum() > 0
                        ? metrics.getRecyclingRate() + "% of waste recycled"
                        : "nothing recorded yet",
                suite().ops().hasSustainabilityData(event.getId())
                        ? Theme.current().text() : Theme.current().warning()));
    }

    private void fillTypes(Event event) throws SQLException {
        DashboardPanel.clear(typeArea);
        Map<String, Double> counts = new LinkedHashMap<>();
        boolean any = false;
        for (EngagementType type : EngagementType.values()) {
            int count = suite().ops().countEngagementByType(event.getId(), type);
            if (count > 0) {
                counts.put(type.getLabel(), (double) count);
                any = true;
            }
        }
        byType.with(counts).measuredIn("records");
        if (!any) {
            typeArea.add(Ui.emptyState("Nothing recorded yet.",
                    "Poll answers, session ratings, questions and connections all count"
                            + " towards this."));
        }
    }

    /**
     * The sustainability table.
     *
     * <p>Each measure with its quantity, whether it was measured or estimated, and what
     * it contributes. An estimated figure says so on the row, because a number
     * presented as measured when it was guessed is the thing that makes a whole report
     * untrustworthy.
     */
    private void fillSustainability(Event event) throws SQLException {
        DashboardPanel.clear(sustainabilityArea);
        List<SustainabilityMetric> metrics = suite().ops().findSustainability(event.getId());
        if (metrics.isEmpty()) {
            sustainabilityArea.add(Ui.emptyState("No environmental figures recorded.",
                    "Ask the venue for its meter readings and the caterers for what they"
                            + " binned. Record them here so the footprint is a measurement."));
            carbonBySource.with(new LinkedHashMap<>());
            return;
        }
        Map<String, Double> carbon = new LinkedHashMap<>();
        for (SustainabilityMetric metric : metrics) {
            sustainabilityArea.add(metricRow(metric));
            sustainabilityArea.add(Box.createVerticalStrut(7));
            if (metric.getCarbonKg().signum() > 0) {
                carbon.put(metric.getMeasure().getLabel(), metric.getCarbonKg().doubleValue());
            }
        }
        carbonBySource.with(carbon).measuredIn("kg CO2e")
                .monochrome(Theme.current().warning());
    }

    private JComponent metricRow(SustainabilityMetric metric) {
        JPanel row = new JPanel(new BorderLayout(12, 4));
        row.setBackground(Theme.current().field());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 80));

        JPanel facts = new JPanel();
        facts.setOpaque(false);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
        JLabel name = Ui.bodyBold(metric.getMeasure().getLabel() + "  "
                + metric.getQuantityLabel());
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(name);
        String source = metric.getSource().isEmpty() ? "no source recorded"
                : "from " + metric.getSource();
        JLabel detail = Ui.muted(source
                + (metric.getMeasure().carbonFactor().signum() > 0
                        ? "  \u00b7  " + metric.getCarbonKg().stripTrailingZeros()
                                .toPlainString() + " kg CO2e"
                        : "  \u00b7  no carbon factor for this measure"));
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(detail);
        row.add(facts, BorderLayout.CENTER);

        row.add(Ui.status(metric.isEstimated() ? "Estimated" : "Measured"), BorderLayout.EAST);
        return row;
    }

    private void fillRecords(Event event) throws SQLException {
        recordData.rowsAsList().clear();
        for (EngagementRecord record : suite().ops().findEngagement(event.getId())) {
            recordData.add(record.getLocalTime().toLocalTime().toString(),
                    record.getType().getLabel(), record.getSubject(),
                    Ui.ellipsise(record.getDetail(), 70),
                    record.hasScore() ? record.getScore() + "/10" : "");
        }
        recordTable.repaint();
        setSubtitle(recordData.getRowCount() + " engagement record(s)");
    }

    // ---------------------------------------------------------------- forms

    private void showRecordForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JComboBox<EngagementType> type =
                Ui.enumCombo(EngagementType.class, EngagementType.SESSION_RATING);
        JTextField who = Ui.field("");
        JTextField subject = Ui.field("");
        JTextField detail = Ui.field("");
        JTextField score = Ui.field("8");
        JLabel scoreNote = Ui.muted("");

        type.addActionListener(change -> {
            EngagementType chosen = (EngagementType) type.getSelectedItem();
            scoreNote.setText(chosen != null && chosen.isScored()
                    ? "This kind carries a score out of 10."
                    : "This kind carries no score, so the score is ignored.");
            score.setEnabled(chosen != null && chosen.isScored());
        });
        scoreNote.setText("This kind carries a score out of 10.");

        JPanel form = Ui.column(
                Ui.titledGroup("What they did", type),
                Ui.gap(4),
                scoreNote,
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Who", who), Ui.titledGroup("Score", score)),
                Ui.gap(8),
                Ui.titledGroup("What it was about", subject),
                Ui.gap(8),
                Ui.titledGroup("In their words", detail));
        if (Ui.formDialog(this, "Record engagement", form, "Record it", "Cancel") == null) {
            return;
        }
        try {
                            EngagementType chosen = (EngagementType) type.getSelectedItem();
                            int value = chosen != null && chosen.isScored()
                                    ? Integer.parseInt(score.getText().trim()) : 0;
                            suite().recordEngagement(event.getId(), "", who.getText().trim(),
                                    chosen, subject.getText().trim(), detail.getText().trim(), value, "", "desk");
                            setSubtitle("Recorded.");
                            reload();
                            } catch (NumberFormatException notANumber) {
                            Ui.dialog(this, "Score not understood", "Put a whole number between 0 and 10.");
                            } catch (IllegalArgumentException outOfRange) {
                            Ui.dialog(this, "Score out of range", outOfRange.getMessage());
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not record it", failure.getMessage());
        }
    }

    private void showFeedbackForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JComboBox<com.eventsuite.analytics.FeedbackTopic> topic =
                Ui.enumCombo(com.eventsuite.analytics.FeedbackTopic.class,
                        com.eventsuite.analytics.FeedbackTopic.OVERALL);
        JTextField who = Ui.field("");
        JTextField rating = Ui.field("4");
        JTextArea comment = Ui.textArea(3);

        JPanel form = Ui.column(
                Ui.titledGroup("What are they answering", topic),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Who", who),
                        Ui.titledGroup("Rating out of 5", rating)),
                Ui.gap(8),
                Ui.titledGroup("In their words", Ui.scroll(comment)));
        if (Ui.formDialog(this, "Add a feedback response", form, "Add it", "Cancel") == null) {
            return;
        }
        try {
                            suite().recordFeedback(event.getId(), "", who.getText().trim(),
                                    (com.eventsuite.analytics.FeedbackTopic) topic.getSelectedItem(),
                                    Integer.parseInt(rating.getText().trim()), comment.getText(),
                                    "desk");
                            setSubtitle("Feedback added.");
                            reload();
                            } catch (NumberFormatException notANumber) {
                            Ui.dialog(this, "Rating not understood", "Put a whole number from 1 to 5.");
                            } catch (IllegalArgumentException outOfRange) {
                            Ui.dialog(this, "Rating out of range", outOfRange.getMessage());
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not record it", failure.getMessage());
        }
    }

    private void showMeasureForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JComboBox<SustainabilityMetric.Measure> measure =
                Ui.enumCombo(SustainabilityMetric.Measure.class,
                        SustainabilityMetric.Measure.ELECTRICITY_KWH);
        JTextField quantity = Ui.field("0");
        JTextField source = Ui.field("");
        JTextField note = Ui.field("");
        JCheckBoxHolder estimated = new JCheckBoxHolder();

        JLabel factor = Ui.muted("");
        measure.addActionListener(change -> {
            SustainabilityMetric.Measure chosen =
                    (SustainabilityMetric.Measure) measure.getSelectedItem();
            factor.setText(chosen != null && chosen.carbonFactor().signum() > 0
                    ? "Counts as " + chosen.carbonFactor().stripTrailingZeros().toPlainString()
                            + " kg CO2e per " + chosen.getUnit()
                    : "No carbon factor for this measure, so it affects the waste figures"
                            + " rather than the footprint.");
        });

        JPanel form = Ui.column(
                Ui.titledGroup("What are you measuring", measure),
                Ui.gap(4),
                factor,
                Ui.gap(8),
                Ui.row(Ui.titledGroup("How much", quantity),
                        Ui.titledGroup("Where the figure came from", source)),
                Ui.gap(8),
                Ui.titledGroup("Anything to note", note),
                Ui.gap(8),
                Ui.titledGroup("This is an estimate, not a reading", estimated.box));
        if (Ui.formDialog(this, "Record an environmental measure", form, "Record it", "Cancel")
                == null) {
            return;
        }
        try {
                            SustainabilityMetric.Measure chosen =
                                    (SustainabilityMetric.Measure) measure.getSelectedItem();
                            suite().recordSustainability(event.getId(), chosen,
                                    com.eventsuite.finance.Money.of(quantity.getText()),
                                    source.getText().trim(), note.getText().trim(), estimated.isSelected());
                            setSubtitle(chosen.getLabel() + " recorded.");
                            reload();
                            } catch (IllegalArgumentException badInput) {
                            Ui.dialog(this, "Figure not understood",
                                    "Quantity must be a number that is not negative.");
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not record it", failure.getMessage());
        }
    }

    /** Carries a checkbox's value in and out of a form. */
    private static final class JCheckBoxHolder {
        private final javax.swing.JCheckBox box = Ui.check("", false);

        boolean isSelected() {
            return box.isSelected();
        }
    }
}
