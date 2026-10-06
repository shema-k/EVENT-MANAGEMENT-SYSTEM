package com.eventsuite.ui;

import com.eventsuite.core.Event;
import com.eventsuite.service.EventSuite;
import com.eventsuite.workflow.AutomationLog;
import com.eventsuite.workflow.WorkflowAction;
import com.eventsuite.workflow.WorkflowRule;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * The automation screen: what is set up, what has fired, and what needs approving.
 *
 * <p>Automation that changes money or capacity is held back until a person approves
 * it, and the held-back actions are shown at the top of this screen rather than
 * buried in a log. That is the whole safety model in one place: the rules decide, and
 * a human does the two things that are awkward to undo.
 *
 * <p>The log keeps every evaluation, not just the ones that fired. A rule that quietly
 * decided not to act looks deleted otherwise, and somebody will go looking for it.
 */
public final class WorkflowsPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private JPanel tileArea;
    private JPanel ruleArea;
    private JPanel pendingArea;
    private JTable logTable;
    private TableData logData;

    public WorkflowsPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);
        ruleArea = new JPanel();
        ruleArea.setOpaque(false);
        ruleArea.setLayout(new BoxLayout(ruleArea, BoxLayout.Y_AXIS));
        pendingArea = new JPanel();
        pendingArea.setOpaque(false);
        pendingArea.setLayout(new BoxLayout(pendingArea, BoxLayout.Y_AXIS));
        logData = TableData.with("When", "Rule", "Action", "Condition", "Outcome")
                .withStatus(4);
        logTable = tableOf(logData);

        String scope = event == null ? "across every event" : "for " + event.getName();

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Run the rules now", this::runRules),
                        Ui.button("Add the recommended rules", this::addStarterRules),
                        Ui.button("Refresh", this::reload),
                        Ui.muted("Rules are checked " + scope + ".")),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                Ui.describedCard("Waiting for your approval",
                        "Actions that change money or capacity, held back until you say so.",
                        pendingArea),
                Ui.gap(14),
                Ui.describedCard("Rules",
                        "Each one as a single sentence, so it can be checked at a glance.",
                        ruleArea),
                Ui.gap(14),
                Ui.describedCard("Log",
                        "Every evaluation, including the ones that decided not to act.",
                        Ui.scroll(logTable)));
        screen("Automation", "Workflow rules and what they have done.", body);
        reload();
    }

    @Override
    protected void reload() {
        if (tileArea == null) {
            return;
        }
        try {
                            fillTiles();
                            fillRules();
                            fillPending();
                            fillLog();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the automation", failure.getMessage());
        }
    }

    private void fillTiles() throws SQLException {
        List<WorkflowRule> rules = suite().workflows().findAllRules();
        DashboardPanel.clear(tileArea);
        long enabled = rules.stream().filter(WorkflowRule::isEnabled).count();
        List<WorkflowRule> broken = suite().workflows().findAllRules().stream()
                .filter(rule -> !rule.getCoherenceProblem().isEmpty()).toList();
        List<AutomationLog> fired = suite().workflows()
                .findActionedLog(getEventId(), 200);

        tileArea.add(Ui.tile("Rules",
                String.valueOf(rules.size()),
                (int) enabled + " switched on"));
        tileArea.add(Ui.tile("Misconfigured",
                String.valueOf(broken.size()),
                broken.isEmpty() ? "All of them can fire" : "Cannot work as written",
                healthColour(!broken.isEmpty())));
        tileArea.add(Ui.tile("Has fired",
                String.valueOf(fired.size()),
                fired.isEmpty() ? "Nothing has acted yet" : "in the recent log"));
        tileArea.add(Ui.tile("Waiting for you",
                String.valueOf(countPending()),
                countPending() == 0 ? "Nothing held back" : "approve these to let them run",
                healthColour(countPending() > 0)));
    }

    private int countPending() {
        try {
                    return (int) suite().workflows().findLog(getEventId(), 30).stream()
                        .filter(entry -> entry.getOutcome()
                                == AutomationLog.Outcome.NEEDED_CONFIRMATION)
                        .count();
                    } catch (SQLException unreadable) {
                    return 0;
                    }
                }


                /**
                 * One rule, as a sentence.
                 *
                 * <p>Written out in words rather than shown as columns, because the whole value of
                 * a rule being checkable is that somebody can read it and agree or disagree. A
                 * misconfigured rule says so on its own row, with the reason.
                 */
                private void fillRules() throws SQLException {
                    DashboardPanel.clear(ruleArea);
                    List<WorkflowRule> rules = suite().workflows()
                        .findRulesForEvent(getEventId());
                    if (rules.isEmpty()) {
                    ruleArea.add(Ui.emptyState("No rules yet.",
                            "Use Add the recommended rules to start with the thresholds that"
                                    + " usually matter."));
                    return;
                    }
                    for (WorkflowRule rule : rules) {
                    ruleArea.add(ruleRow(rule));
                    ruleArea.add(Box.createVerticalStrut(8));
                    }
                }

                private JComponent ruleRow(WorkflowRule rule) {
                    JPanel row = new JPanel(new BorderLayout(12, 5));
                    row.setBackground(Theme.current().field());
                    row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                rule.isIncoherent() ? Theme.current().danger()
                                        : Theme.current().border()),
                        BorderFactory.createEmptyBorder(10, 12, 10, 12)));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 120));

                    JPanel facts = new JPanel();
                    facts.setOpaque(false);
                    facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
                    JLabel name = Ui.bodyBold(rule.getName());
                    name.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(name);
                    JLabel sentence = Ui.muted(capitalise(rule.describe()));
                    sentence.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(sentence);

                    String problem = rule.getCoherenceProblem();
                    if (!problem.isEmpty()) {
                    JLabel warning = Ui.health(problem, true);
                    warning.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(Box.createVerticalStrut(4));
                    facts.add(warning);
                    } else if (rule.getAction().needsConfirmation()) {
                    JLabel note = Ui.muted("This one changes money or capacity, so it waits for you.");
                    note.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(Box.createVerticalStrut(4));
                    facts.add(note);
                    }
                    row.add(facts, BorderLayout.CENTER);

                    JPanel actions = new JPanel();
                    actions.setOpaque(false);
                    actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
                    JPanel buttons = Ui.row(
                        Ui.smallButton(rule.isEnabled() ? "Switch off" : "Switch on", () -> {
                            try {
                                                            suite().setRuleEnabled(rule, !rule.isEnabled());
                                                            reload();
                            } catch (SQLException failure) {
                                Ui.dialog(this, "Could not save", failure.getMessage());
                            }
                        }),
                        Ui.smallButton("Remove", () -> {
                            try {
                                                            suite().deleteRule(rule.getId());
                                                            reload();
                            } catch (SQLException failure) {
                                Ui.dialog(this, "Could not remove", failure.getMessage());
                            }
                        }));
                    buttons.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    actions.add(buttons);
                    actions.add(Ui.muted(rule.getTimesRun() == 0 ? "never fired"
                        : "fired " + rule.getTimesRun() + "\u00d7, last "
                                + rule.getLastRunLabel()));
                    actions.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    row.add(actions, BorderLayout.EAST);
                    return row;
                }

                private String capitalise(String text) {
                    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
                }

                /** The actions held back, with a button that lets them through. */
                private void fillPending() throws SQLException {
                    DashboardPanel.clear(pendingArea);
                    List<AutomationLog> waiting = suite().workflows().findLog(getEventId(), 30).stream()
                        .filter(entry -> entry.getOutcome()
                                == AutomationLog.Outcome.NEEDED_CONFIRMATION)
                        .toList();
                    if (waiting.isEmpty()) {
                    pendingArea.add(Ui.emptyState("Nothing is waiting.",
                            "The only actions that stop for approval are the ones that change money"
                                    + " or capacity."));
                    return;
                    }
                    for (AutomationLog entry : waiting) {
                    JPanel row = new JPanel(new BorderLayout(12, 5));
                    row.setBackground(Theme.current().accentSoft());
                    row.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Theme.current().warning()),
                            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 100));

                    JPanel facts = new JPanel();
                    facts.setOpaque(false);
                    facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
                    JLabel what = Ui.bodyBold(entry.getAction().getLabel());
                    what.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(what);
                    JLabel why = Ui.muted(entry.getRuleName() + "  \u00b7  "
                            + (entry.getField() == null ? "" : entry.getField().getLabel() + " "
                            + entry.getActualValue().stripTrailingZeros().toPlainString()));
                    why.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(why);
                    row.add(facts, BorderLayout.CENTER);
                    row.add(Ui.smallButton("Let it run", this::confirmPending), BorderLayout.EAST);
                    pendingArea.add(row);
                    pendingArea.add(Box.createVerticalStrut(8));
                    }
                }

                private void confirmPending() {
                    try {
                                            List<AutomationLog> confirmed = suite().confirmAutomation(getEventId());
                                            setSubtitle(confirmed.size() + " action(s) approved.");
                                            reload();
                    } catch (SQLException failure) {
                        Ui.dialog(this, "Could not approve", failure.getMessage());
                    }
    }

    private void fillLog() throws SQLException {
        logData.rowsAsList().clear();
        for (AutomationLog entry : suite().workflows().findLog(getEventId(), 120)) {
            logData.add(entry.getAt().atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime().toString(),
                    entry.getRuleName(), entry.getAction().getLabel(),
                    entry.getField() == null ? "" : entry.getConditionLabel(),
                    entry.getOutcome().getLabel());
        }
        logTable.repaint();
    }

    /** Runs every applicable rule once, on the spot. */
    public void runRules() {
        try {
                            List<AutomationLog> entries = suite().runAutomation(getEventId(), false);
                            long fired = entries.stream().filter(AutomationLog::isActioned).count();
                            setSubtitle(fired == 0 ? "Nothing needed doing."
                                    : fired + " rule(s) acted.");
                            reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not run the rules", failure.getMessage());
        }
    }

    private void addStarterRules() {
        try {
                            int before = suite().workflows().findRulesForEvent(getEventId()).size();
                            Event event = currentEvent();
                            for (WorkflowRule rule : com.eventsuite.workflow.WorkflowEngine.starterRules(
                                    getEventId(), getEventId().isEmpty() ? "all" : getEventId())) {
                                if (!suite().workflows().hasEquivalentRule(rule)) {
                                    suite().addRule(rule);
                                }
                            }
                            int after = suite().workflows().findRulesForEvent(getEventId()).size();
                            setSubtitle(after == before ? "Those rules are already set up."
                                    : (after - before) + " rule(s) added.");
                            reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not add the rules", failure.getMessage());
        }
    }
}
