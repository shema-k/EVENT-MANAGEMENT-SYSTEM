package com.eventsuite.workflow;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * The facts about one event at one moment, for rules to be tested against.
 *
 * <p>Every rule in the system is evaluated against a context rather than against
 * live tables. That is what makes the automation run repeatable: the same context
 * always produces the same result, so a rule can be tested without touching the
 * database and a run can be explained afterwards from the numbers it saw.
 *
 * <p>Fields default to zero when not supplied. A context that knows only about
 * tickets still answers questions about marketing, at zero, instead of throwing —
 * so a partially-built context never breaks a run halfway through.
 */
public final class WorkflowContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String eventId;
    private final LocalDateTime evaluatedAt;
    /**
     * The facts set on this context.
     *
     * <p>Declared as {@link EnumMap} rather than {@code Map} because an EnumMap is
     * concretely serialisable, and this object is written to disk with the rules.
     */
    private final EnumMap<ConditionField, BigDecimal> facts;

    public WorkflowContext(String eventId, LocalDateTime evaluatedAt) {
        this.eventId = eventId == null ? "" : eventId;
        this.evaluatedAt = evaluatedAt == null ? LocalDateTime.now() : evaluatedAt;
        this.facts = new EnumMap<>(ConditionField.class);
    }

    /** An empty context for an event, evaluated now. */
    public static WorkflowContext of(String eventId) {
        return new WorkflowContext(eventId, LocalDateTime.now());
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    /**
     * The value of a field, or zero when this context does not know it.
     *
     * <p>Returning zero rather than null is the decision that lets one rule builder
     * serve every trigger: a rule testing a field that is not populated fails its
     * condition and stays quiet, rather than crashing the run.
     */
    public BigDecimal get(ConditionField field) {
        if (field == null) {
            return Money.ZERO;
        }
        return facts.getOrDefault(field, Money.ZERO);
    }

    /** Whether this context carries a real figure for the field. */
    public boolean knows(ConditionField field) {
        return field != null && facts.containsKey(field);
    }

    /** A copy with one more fact set. Immutable, so contexts can be shared. */
    public WorkflowContext with(ConditionField field, BigDecimal value) {
        WorkflowContext copy = new WorkflowContext(eventId, evaluatedAt);
        copy.facts.putAll(facts);
        if (field != null) {
            copy.facts.put(field, Money.of(value));
        }
        return copy;
    }

    /** A copy with a count set. */
    public WorkflowContext with(ConditionField field, long value) {
        return with(field, BigDecimal.valueOf(value));
    }

    /** A copy with days until the event set from a start date. */
    public WorkflowContext withDaysUntil(java.time.LocalDate startDate) {
        if (startDate == null) {
            return with(ConditionField.DAYS_UNTIL_EVENT, 0);
        }
        return with(ConditionField.DAYS_UNTIL_EVENT,
                java.time.temporal.ChronoUnit.DAYS.between(
                        evaluatedAt.toLocalDate(), startDate));
    }

    /** A copy with hours until the event set from a start moment. */
    public WorkflowContext withHoursUntil(LocalDateTime startAt) {
        if (startAt == null) {
            return with(ConditionField.HOURS_UNTIL_EVENT, 0);
        }
        return with(ConditionField.HOURS_UNTIL_EVENT,
                java.time.Duration.between(evaluatedAt, startAt).toHours());
    }

    /** A copy with the percentage of capacity sold set. */
    public WorkflowContext withPercentSold(int sold, int capacity) {
        if (capacity <= 0) {
            return with(ConditionField.PERCENT_SOLD, 0);
        }
        return with(ConditionField.PERCENT_SOLD,
                BigDecimal.valueOf(100.0 * sold / capacity).setScale(1,
                        java.math.RoundingMode.HALF_UP));
    }

    /** How many fields have been set. Useful in tests to check a context is built. */
    public int getFactCount() {
        return facts.size();
    }

    @Override
    public String toString() {
        return "WorkflowContext for " + (eventId.isEmpty() ? "(no event)" : eventId)
                + " at " + evaluatedAt + " with " + facts.size() + " facts";
    }
}
