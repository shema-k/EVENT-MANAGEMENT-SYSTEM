package com.eventsuite.workflow;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Evaluates automation rules against an event's facts, and records what happened.
 *
 * <p>The engine does not know what any of the numbers mean. It is handed a
 * {@link WorkflowContext}, runs every enabled rule against it, and returns what
 * fired. Nothing about it reads the database or sends an email, which keeps it
 * testable and keeps the side effects in one place where they can be listed and
 * confirmed rather than scattered through the rule logic.
 *
 * <p>Two safeguards matter here. A rule that cannot work is reported rather than
 * quietly ignored, because a misconfigured rule is invisible otherwise. And an action
 * that changes money or capacity is held back until somebody confirms it, so a
 * threshold typed in a hurry cannot refund a sponsor's invoice at three in the
 * morning.
 */
public final class WorkflowEngine implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Declared as {@link ArrayList} rather than {@code List} so these are concretely
     * serialisable: the engine is saved alongside the rules it holds.
     */
    private final ArrayList<WorkflowRule> rules = new ArrayList<>();
    private final ArrayList<AutomationLog> log = new ArrayList<>();
    private final ArrayList<AutomationLog> pendingConfirmations = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger();

    public WorkflowEngine() {
    }

    public WorkflowEngine(List<WorkflowRule> startingRules) {
        if (startingRules != null) {
            rules.addAll(startingRules);
        }
    }

    /** The rules this engine holds. The list is live, so rules can be added. */
    public List<WorkflowRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /** Adds a rule. */
    public void addRule(WorkflowRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    /** Removes a rule by id. Returns true when one was found and removed. */
    public boolean removeRule(String ruleId) {
        return rules.removeIf(rule -> rule.getId().equals(ruleId));
    }

    /** The rules that apply to a given event, including the event-wide ones. */
    public List<WorkflowRule> rulesFor(String eventId) {
        List<WorkflowRule> applicable = new ArrayList<>();
        for (WorkflowRule rule : rules) {
            if (rule.appliesTo(eventId)) {
                applicable.add(rule);
            }
        }
        return applicable;
    }

    /** How many rules are switched on. */
    public int getEnabledCount() {
        int count = 0;
        for (WorkflowRule rule : rules) {
            if (rule.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Runs every applicable rule against the context.
     *
     * @param allowConfirmed whether guarded actions may run because a person has
     *                       already approved them for this run
     * @return the log entries from this run, fired entries first
     */
    public List<AutomationLog> run(WorkflowContext context, boolean allowConfirmed,
                                      String idPrefix) {
        List<AutomationLog> produced = new ArrayList<>();
        if (context == null) {
            return produced;
        }
        for (WorkflowRule rule : rulesFor(context.getEventId())) {
            AutomationLog entry = evaluate(rule, context, allowConfirmed, idPrefix);
            produced.add(entry);
        }
        // Fired entries first: the interesting ones should not be buried under a
        // screenful of rules that decided not to act.
        produced.sort((left, right) -> {
            int byInterest = Boolean.compare(right.isInteresting(), left.isInteresting());
            return byInterest != 0 ? byInterest : right.getAt().compareTo(left.getAt());
        });
        log.addAll(produced);
        return produced;
    }

    /**
     * Runs every rule, allowing only actions that need no confirmation.
     *
     * <p>Prefixes the log ids with this engine's own identity, which is unique per
     * instance. A caller persisting the log can use the store's sequence instead.
     */
    public List<AutomationLog> run(WorkflowContext context) {
        return run(context, false, "auto-" + System.identityHashCode(this));
    }

    /** Evaluates one rule and returns what happened. */
    public AutomationLog evaluate(WorkflowRule rule, WorkflowContext context,
                                  boolean allowConfirmed, String idPrefix) {
        if (!rule.isEnabled()) {
            return AutomationLog.skipped(nextId(idPrefix), rule, context, context.get(rule.getField()),
                    AutomationLog.Outcome.DISABLED);
        }
        String problem = rule.getCoherenceProblem();
        if (!problem.isEmpty()) {
            return AutomationLog.skipped(nextId(idPrefix), rule, context, context.get(rule.getField()),
                    AutomationLog.Outcome.INCOHERENT);
        }
        if (!rule.canRunAgain(context.getEvaluatedAt().atZone(java.time.ZoneId.systemDefault())
                .toInstant())) {
            AutomationLog.Outcome outcome = rule.hasNeverRun()
                    ? AutomationLog.Outcome.SKIPPED : AutomationLog.Outcome.ALREADY_FIRED;
            return AutomationLog.skipped(nextId(idPrefix), rule, context, context.get(rule.getField()),
                    outcome);
        }
        BigDecimal actual = context.get(rule.getField());
        if (!rule.isSatisfiedBy(context)) {
            return AutomationLog.skipped(nextId(idPrefix), rule, context, actual,
                    AutomationLog.Outcome.SKIPPED);
        }
        if (rule.getAction().needsConfirmation() && !allowConfirmed
                && !rule.isConfirmedForThisRun()) {
            AutomationLog entry = AutomationLog.fired(nextId(idPrefix), rule, context, actual);
            pendingConfirmations.add(entry);
            return new AutomationLog(entry.getId(), rule.getId(), rule.getName(),
                    context.getEventId(), rule.getAction(),
                    AutomationLog.Outcome.NEEDED_CONFIRMATION, rule.getField(), actual,
                    rule.getThreshold(), entry.getAt(),
                    rule.getAction().getLabel()
                            + " needs approval before it can change money or capacity.");
        }
        rule.recordRun(context.getEvaluatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
        return AutomationLog.fired(nextId(idPrefix), rule, context, actual);
    }

    /**
     * Approves the actions that were held back, and runs them.
     *
     * <p>Called when somebody clicks the confirmation. Everything held back is
     * approved at once, because the screen shows them together and asking twice for
     * the same list is how somebody approves the wrong one.
     */
    public List<AutomationLog> confirmPending(WorkflowContext context, String idPrefix) {
        List<AutomationLog> confirmed = new ArrayList<>();
        for (AutomationLog waiting : new ArrayList<>(pendingConfirmations)) {
            for (WorkflowRule rule : rules) {
                if (rule.getId().equals(waiting.getRuleId())
                        && rule.isSatisfiedBy(context)) {
                    rule.recordRun(context.getEvaluatedAt()
                            .atZone(java.time.ZoneId.systemDefault()).toInstant());
                    confirmed.add(AutomationLog.fired(nextId(idPrefix), rule, context,
                            context.get(rule.getField())));
                    break;
                }
            }
        }
        pendingConfirmations.clear();
        log.addAll(confirmed);
        return confirmed;
    }

    /** The actions waiting for somebody to approve them. */
    public List<AutomationLog> getPendingConfirmations() {
        return Collections.unmodifiableList(pendingConfirmations);
    }

    /** How many actions are waiting for approval. */
    public int getPendingCount() {
        return pendingConfirmations.size();
    }

    /** Discards everything held back, because the condition no longer holds. */
    public void clearPending() {
        pendingConfirmations.clear();
    }

    /** Everything this engine has recorded, oldest first. */
    public List<AutomationLog> getLog() {
        return Collections.unmodifiableList(log);
    }

    /** The log entries worth showing an organiser. */
    public List<AutomationLog> getInterestingLog() {
        List<AutomationLog> interesting = new ArrayList<>();
        for (AutomationLog entry : log) {
            if (entry.isInteresting()) {
                interesting.add(entry);
            }
        }
        return interesting;
    }

    /** Rules that cannot work as configured. */
    public List<WorkflowRule> getMisconfiguredRules() {
        List<WorkflowRule> broken = new ArrayList<>();
        for (WorkflowRule rule : rules) {
            if (!rule.getCoherenceProblem().isEmpty()) {
                broken.add(rule);
            }
        }
        return broken;
    }

    /**
     * A set of sensible rules for a new event.
     *
     * <p>Shipped with the system rather than left for the user to invent, because
     * the value of automation is in having it already thought of the obvious
     * thresholds, and these are the ones everybody ends up writing.
     */
    public static List<WorkflowRule> starterRules(String eventId, String prefix) {
        List<WorkflowRule> starters = new ArrayList<>();
        starters.add(WorkflowRule.daysBefore(prefix + "-1", eventId,
                "Confirm unconfirmed speakers one week out", 7, WorkflowAction.CREATE_TASK,
                "Chase the speakers who have not confirmed. After seven days a gap is a hole in the programme."));
        starters.add(WorkflowRule.of(prefix + "-2", eventId,
                "Warn when tickets are nearly gone", WorkflowTrigger.ALMOST_SOLD_OUT,
                ConditionField.PERCENT_SOLD, ConditionOperator.AT_LEAST,
                BigDecimal.valueOf(90), WorkflowAction.NOTIFY_ORGANISER,
                "90% sold. Check the room layout and plan for a full house."));
        starters.add(WorkflowRule.of(prefix + "-3", eventId,
                "Stop selling when the event is full", WorkflowTrigger.TICKET_SOLD_OUT,
                ConditionField.TICKETS_REMAINING, ConditionOperator.AT_MOST,
                BigDecimal.ZERO, WorkflowAction.MARK_SOLD_OUT, ""));
        starters.add(WorkflowRule.of(prefix + "-4", eventId,
                "Chase unpaid invoices", WorkflowTrigger.INVOICE_OVERDUE,
                ConditionField.INVOICES_OVERDUE, ConditionOperator.MORE_THAN,
                BigDecimal.ZERO, WorkflowAction.CREATE_TASK,
                "Send a reminder to everyone whose invoice has passed its due date."));
        starters.add(WorkflowRule.of(prefix + "-5", eventId,
                "Warn about budget headings at their limit", WorkflowTrigger.BUDGET_NEARLY_SPENT,
                ConditionField.BUDGET_SPENT, ConditionOperator.AT_LEAST,
                BigDecimal.valueOf(85), WorkflowAction.ALERT_BUDGET,
                "A budget heading is at 85% of its allocation."));
        starters.add(WorkflowRule.daysBefore(prefix + "-6", eventId,
                "Send joining details the day before", 1, WorkflowAction.SEND_EMAIL,
                "Your event is tomorrow. Here is where to go, what time to arrive, and what to bring."));
        starters.add(WorkflowRule.of(prefix + "-7", eventId,
                "Ask for feedback once it is over", WorkflowTrigger.EVENT_COMPLETED,
                ConditionField.POOR_FEEDBACK_COUNT, ConditionOperator.MORE_THAN,
                BigDecimal.ZERO, WorkflowAction.CREATE_TASK,
                "Follow up on the feedback that came back poor."));
        starters.add(WorkflowRule.of(prefix + "-8", eventId,
                "Remind about the waste report", WorkflowTrigger.WASTE_REPORT_DUE,
                ConditionField.CARBON_KG, ConditionOperator.EXACTLY,
                BigDecimal.ZERO, WorkflowAction.CREATE_TASK,
                "No sustainability figures have been entered. Ask the venue and the suppliers."));
        return starters;
    }

    /**
     * The headline number for the automation screen.
     *
     * <p>The fired-to-evaluated ratio, as a percentage. A run where almost nothing
     * fires is a healthy run, which is worth saying: automation that is always busy
     * has been configured to cry wolf.
     */
    public BigDecimal getActionRate() {
        if (log.isEmpty()) {
            return Money.ZERO;
        }
        int fired = 0;
        for (AutomationLog entry : log) {
            if (entry.isActioned()) {
                fired++;
            }
        }
        return Money.of(BigDecimal.valueOf(100.0 * fired / log.size()).setScale(1,
                java.math.RoundingMode.HALF_UP));
    }

    /**
     * A unique id for a log entry or a new rule.
     *
     * <p>The prefix is supplied by the caller rather than hard-coded. An engine is
     * built fresh on every automation run, so a prefix fixed inside it would restart
     * from one each time and two runs would mint the same ids — which the store would
     * then reject as duplicate keys.
     */
    public String nextId(String prefix) {
        return prefix + "-" + (long) idCounter.incrementAndGet();
    }

    /** How many rules this engine holds. */
    public int getRuleCount() {
        return rules.size();
    }
}
