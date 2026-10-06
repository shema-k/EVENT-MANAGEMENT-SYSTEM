package com.eventsuite.workflow;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One automated rule: when this happens, if this is true, do that.
 *
 * <p>The whole of the automation system is this sentence. Keeping it to one line is
 * what makes it trustworthy, because a rule that cannot be read in one line cannot
 * be checked by the person who has to answer for it at midnight before a festival.
 *
 * <p>A rule can be switched off without being deleted, and it remembers when it last
 * ran and how often. Both matter: a rule that fires once only must not fire again on
 * the next poll, and a rule that has quietly done nothing for a month should be
 * visible as a problem rather than assumed to be fine.
 */
public final class WorkflowRule implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.UK);

    private final String id;
    private final String eventId;
    private final String name;
    private final WorkflowTrigger trigger;
    private final ConditionField field;
    private final ConditionOperator operator;
    private final BigDecimal threshold;
    private final WorkflowAction action;
    private final String parameter;
    private final boolean enabled;

    private int timesRun;
    private Instant lastRunAt;
    private boolean confirmedForThisRun;

    public WorkflowRule(String id,
                        String eventId,
                        String name,
                        WorkflowTrigger trigger,
                        ConditionField field,
                        ConditionOperator operator,
                        BigDecimal threshold,
                        WorkflowAction action,
                        String parameter,
                        boolean enabled,
                        int timesRun,
                        Instant lastRunAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = eventId == null ? "" : eventId;
        this.name = Objects.requireNonNull(name, "name");
        this.trigger = trigger == null ? WorkflowTrigger.MANUAL : trigger;
        this.field = field == null ? ConditionField.DAYS_UNTIL_EVENT : field;
        this.operator = operator == null ? ConditionOperator.AT_LEAST : operator;
        this.threshold = com.eventsuite.finance.Money.of(threshold);
        this.action = action == null ? WorkflowAction.NOTIFY_ORGANISER : action;
        this.parameter = parameter == null ? "" : parameter.trim();
        this.enabled = enabled;
        this.timesRun = timesRun;
        this.lastRunAt = lastRunAt;
    }

    /** A rule that is switched on and has never run. */
    public static WorkflowRule of(String id, String eventId, String name, WorkflowTrigger trigger,
                                  ConditionField field, ConditionOperator operator,
                                  BigDecimal threshold, WorkflowAction action, String parameter) {
        return new WorkflowRule(id, eventId, name, trigger, field, operator, threshold, action,
                parameter, true, 0, null);
    }

    /**
     * A rule for a threshold trigger, such as seven days before the event.
     *
     * <p>Built here rather than assembled by the caller so the days field is the one
     * that trigger actually reads. A "days before" rule testing ticket revenue would
     * never fire, and that mistake is easy to make by hand.
     */
    public static WorkflowRule daysBefore(String id, String eventId, String name, int days,
                                          WorkflowAction action, String parameter) {
        return of(id, eventId, name, WorkflowTrigger.DAYS_BEFORE_EVENT,
                ConditionField.DAYS_UNTIL_EVENT, ConditionOperator.AT_MOST,
                BigDecimal.valueOf(days), action, parameter);
    }

    /** A rule for a percentage-of-capacity trigger. */
    public static WorkflowRule atPercentSold(String id, String eventId, String name,
                                             BigDecimal percent, WorkflowAction action,
                                             String parameter) {
        return of(id, eventId, name, WorkflowTrigger.ALMOST_SOLD_OUT,
                ConditionField.PERCENT_SOLD, ConditionOperator.AT_LEAST, percent, action, parameter);
    }

    public String getId() {
        return id;
    }

    /** Which event this rule belongs to, or empty for one that covers all events. */
    public String getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public WorkflowTrigger getTrigger() {
        return trigger;
    }

    public ConditionField getField() {
        return field;
    }

    public ConditionOperator getOperator() {
        return operator;
    }

    /** The number the field is compared against. */
    public BigDecimal getThreshold() {
        return threshold;
    }

    public WorkflowAction getAction() {
        return action;
    }

    /** The message or detail the action uses. */
    public String getParameter() {
        return parameter;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** How many times this rule has fired. */
    public int getTimesRun() {
        return timesRun;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    /** Whether a person has approved this run, needed by the guarded actions. */
    public boolean isConfirmedForThisRun() {
        return confirmedForThisRun;
    }

    /** Whether this rule applies to a given event. */
    public boolean appliesTo(String candidateEventId) {
        return eventId.isEmpty() || eventId.equals(candidateEventId);
    }

    /** Whether the rule can run again, given whether it has already fired. */
    public boolean canRunAgain(Instant now) {
        if (!enabled) {
            return false;
        }
        if (trigger.canRepeat()) {
            return true;
        }
        // A one-shot rule never runs twice. Without this, "tickets sold out" would
        // fire on every poll once the event was full.
        return lastRunAt == null;
    }

    /** Whether the rule has never fired. */
    public boolean hasNeverRun() {
        return lastRunAt == null;
    }

    /** Whether this rule has been switched on but has not worked for a long time. */
    public boolean looksIdle(Instant now) {
        if (!enabled || timesRun == 0) {
            return enabled;
        }
        return lastRunAt != null
                && now.isAfter(lastRunAt.plusSeconds(60L * 60 * 24 * 30));
    }

    /**
     * Whether the condition is satisfied by the given facts.
     *
     * <p>The field is read from the context and compared numerically, so the operator
     * never sees the strings. A missing field reads as zero rather than as a failure,
     * because a rule written against a screen that is not open should quietly not
     * fire rather than stop the whole run.
     */
    public boolean isSatisfiedBy(WorkflowContext context) {
        if (context == null) {
            return false;
        }
        BigDecimal actual = context.get(field);
        if (actual == null) {
            actual = BigDecimal.ZERO;
        }
        return operator.test(actual.compareTo(threshold));
    }

    /**
     * Whether the action may run unattended.
     *
     * <p>False for actions that change money or capacity until somebody has
     * confirmed them for this run.
     */
    public boolean mayRunWithoutConfirmation() {
        return !action.needsConfirmation() || confirmedForThisRun;
    }

    /** Records that the rule fired. */
    public void recordRun(Instant when) {
        timesRun++;
        lastRunAt = when;
        // The confirmation is for one run only. Leaving it set would let a guarded
        // action fire unattended forever after somebody once clicked yes.
        confirmedForThisRun = false;
    }

    /** Grants confirmation for the next run of a guarded action. */
    public void confirmNextRun() {
        this.confirmedForThisRun = true;
    }

    /** A copy that is switched off, or on. */
    public WorkflowRule withEnabled(boolean newEnabled) {
        return new WorkflowRule(id, eventId, name, trigger, field, operator, threshold, action,
                parameter, newEnabled, timesRun, lastRunAt);
    }

    /** A copy with a different action. */
    public WorkflowRule withAction(WorkflowAction newAction, String newParameter) {
        return new WorkflowRule(id, eventId, name, trigger, field, operator, threshold, newAction,
                newParameter, enabled, timesRun, lastRunAt);
    }

    /** A copy with a different condition. */
    public WorkflowRule withCondition(ConditionField newField, ConditionOperator newOperator,
                                      BigDecimal newThreshold) {
        return new WorkflowRule(id, eventId, name, trigger, newField, newOperator, newThreshold,
                action, parameter, enabled, timesRun, lastRunAt);
    }

    /** The rule as a single readable sentence. */
    public String describe() {
        StringBuilder sentence = new StringBuilder("When ");
        sentence.append(trigger.getLabel().toLowerCase(Locale.ROOT));
        sentence.append(", if ").append(field.getLabel().toLowerCase(Locale.ROOT));
        sentence.append(" ").append(operator.getLabel()).append(" ");
        sentence.append(field.isWholeNumber()
                ? threshold.stripTrailingZeros().toPlainString()
                : threshold.stripTrailingZeros().toPlainString());
        sentence.append(", then ").append(action.getLabel().toLowerCase(Locale.ROOT));
        if (!parameter.isEmpty() && action.needsParameter()) {
            sentence.append(" (\"").append(parameter).append("\")");
        }
        return sentence.toString();
    }

    /** A one-line summary for the rules list. */
    public String getDisplayLine() {
        return (enabled ? "" : "[off]  ") + name + "  -  " + describe();
    }

    /**
     * Whether this rule is asking for something that cannot happen.
     *
     * <p>Caught when a rule is built, so the mistake is reported where it was made
     * rather than left sitting inert: a post-event condition attached to a trigger
     * that fires before the event, or a threshold trigger with no threshold.
     */
    public boolean isIncoherent() {
        return !getCoherenceProblem().isEmpty();
    }

    /** Why the rule cannot work, or an empty string when it is fine. */
    public String getCoherenceProblem() {
        if (trigger.needsThreshold() && threshold.signum() == 0) {
            return "This trigger needs a number of days or hours to act on.";
        }
        if (!trigger.isAfterTheEvent() && field.isPostEvent()) {
            return "\"" + field.getLabel() + "\" only exists after the event, so it is always"
                    + " zero when \"" + trigger.getLabel() + "\" fires.";
        }
        if (trigger.isAfterTheEvent() && field == ConditionField.DAYS_UNTIL_EVENT) {
            return "Days until the event is meaningless once the event is over.";
        }
        return "";
    }

    /** The last run, formatted for the rules list. */
    public String getLastRunLabel() {
        if (lastRunAt == null) {
            return "never";
        }
        return WHEN.format(lastRunAt.atZone(ZoneId.systemDefault()));
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
