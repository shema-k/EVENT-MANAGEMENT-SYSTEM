package com.eventsuite.workflow;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * What happened when one rule was evaluated once.
 *
 * <p>Automation that does not explain itself is worse than none, because it is
 * trusted and then found to have been quietly wrong. Every evaluation is written
 * down, whether or not it fired, with the two numbers that decided it. When
 * somebody asks why an event was marked sold out early, this is the answer.
 */
public final class AutomationLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM HH:mm:ss", Locale.UK);

    /** The outcome of evaluating a rule. */
    public enum Outcome {
        /** The condition was met and the action ran. */
        FIRED("Fired"),
        /** The condition was not met. The normal case. */
        SKIPPED("Skipped"),
        /** The rule was switched off. */
        DISABLED("Disabled"),
        /** The action needed confirmation nobody gave. */
        NEEDED_CONFIRMATION("Needs confirmation"),
        /** The rule cannot work, as configured. */
        INCOHERENT("Misconfigured"),
        /** The rule has already fired and cannot fire again. */
        ALREADY_FIRED("Already fired");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean isActioned() {
            return this == FIRED;
        }

        /** The outcome with this name, or SKIPPED when it is not one of ours. */
        public static Outcome fromLabel(String text) {
            if (text != null) {
                String needle = text.trim().replace(" ", "").replace("_", "");
                for (Outcome candidate : values()) {
                    if (candidate.name().replace("_", "").equalsIgnoreCase(needle)) {
                        return candidate;
                    }
                    if (candidate.label.replace(" ", "").equalsIgnoreCase(needle)) {
                        return candidate;
                    }
                }
            }
            return SKIPPED;
        }
    }

    private final String id;
    private final String ruleId;
    private final String ruleName;
    private final String eventId;
    private final WorkflowAction action;
    private final Outcome outcome;
    private final ConditionField field;
    private final BigDecimal actualValue;
    private final BigDecimal threshold;
    private final Instant at;
    private final String detail;

    public AutomationLog(String id,
                         String ruleId,
                         String ruleName,
                         String eventId,
                         WorkflowAction action,
                         Outcome outcome,
                         ConditionField field,
                         BigDecimal actualValue,
                         BigDecimal threshold,
                         Instant at,
                         String detail) {
        this.id = Objects.requireNonNull(id, "id");
        this.ruleId = ruleId == null ? "" : ruleId;
        this.ruleName = ruleName == null ? "" : ruleName;
        this.eventId = eventId == null ? "" : eventId;
        this.action = action == null ? WorkflowAction.LOG_ONLY : action;
        this.outcome = outcome == null ? Outcome.SKIPPED : outcome;
        this.field = field;
        this.actualValue = com.eventsuite.finance.Money.of(actualValue);
        this.threshold = com.eventsuite.finance.Money.of(threshold);
        this.at = Objects.requireNonNull(at, "at");
        this.detail = detail == null ? "" : detail;
    }

    /** The common case: a rule was evaluated and did nothing. */
    public static AutomationLog skipped(String id, WorkflowRule rule, WorkflowContext context,
                                        BigDecimal actual, Outcome outcome) {
        return new AutomationLog(id, rule.getId(), rule.getName(), context.getEventId(),
                rule.getAction(), outcome, rule.getField(), actual, rule.getThreshold(),
                context.getEvaluatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                outcome == Outcome.INCOHERENT ? rule.getCoherenceProblem() : "");
    }

    /** The case where a rule fired. */
    public static AutomationLog fired(String id, WorkflowRule rule, WorkflowContext context,
                                      BigDecimal actual) {
        String message = rule.getAction().getDefaultMessage(rule.getParameter());
        return new AutomationLog(id, rule.getId(), rule.getName(), context.getEventId(),
                rule.getAction(), Outcome.FIRED, rule.getField(), actual, rule.getThreshold(),
                context.getEvaluatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                message);
    }

    public String getId() {
        return id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getEventId() {
        return eventId;
    }

    public WorkflowAction getAction() {
        return action;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public ConditionField getField() {
        return field;
    }

    /** The number the rule saw. */
    public BigDecimal getActualValue() {
        return actualValue;
    }

    /** The number it was compared against. */
    public BigDecimal getThreshold() {
        return threshold;
    }

    public Instant getAt() {
        return at;
    }

    /** The message sent, or why the rule could not work. */
    public String getDetail() {
        return detail;
    }

    /** Whether anything actually happened. */
    public boolean isActioned() {
        return outcome.isActioned();
    }

    /** Whether this evaluation is worth showing an organiser. */
    public boolean isInteresting() {
        return outcome == Outcome.FIRED || outcome == Outcome.NEEDED_CONFIRMATION
                || outcome == Outcome.INCOHERENT;
    }

    /** The comparison, written out. */
    public String getConditionLabel() {
        if (field == null) {
            return "";
        }
        String actual = actualValue == null ? "" : actualValue.stripTrailingZeros().toPlainString();
        String limit = threshold == null ? "" : threshold.stripTrailingZeros().toPlainString();
        return field.getLabel() + " " + actual + " " + getOperatorSymbol() + " " + limit;
    }

    private String getOperatorSymbol() {
        return switch (outcome) {
            case FIRED -> "met";
            case SKIPPED -> "not met";
            default -> "vs";
        };
    }

    /** A line for the automation log screen. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(WHEN.format(at.atZone(ZoneId.systemDefault())));
        line.append("  ").append(ruleName);
        line.append("  ").append(action.getLabel());
        if (field != null) {
            line.append("  (").append(getConditionLabel()).append(")");
        }
        line.append("  ").append(outcome.getLabel());
        return line.toString();
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
