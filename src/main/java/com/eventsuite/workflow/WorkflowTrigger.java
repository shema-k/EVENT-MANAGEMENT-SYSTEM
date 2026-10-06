package com.eventsuite.workflow;

import java.util.Locale;

/**
 * The moment something is worth checking the rules against.
 *
 * <p>Triggers are named after events in the lifecycle rather than after screens,
 * because a rule should not care which screen somebody happened to be looking at.
 * "Tickets sold out" happens whether it was noticed by the dashboard refreshing,
 * by the sales screen or by a colleague on the phone, and the automation should
 * behave the same in all three cases.
 *
 * <p>Each trigger carries when it fires. Some are one-offs — an event cannot be
 * created twice — and some recur on every check, which is what lets a "days before"
 * rule keep working as the date approaches rather than only on the exact day.
 */
public enum WorkflowTrigger {

    EVENT_CREATED("An event is created", Occurrence.ONCE),
    EVENT_STATUS_CHANGED("An event changes status", Occurrence.ONCE),
    TICKET_SOLD("A ticket is sold", Occurrence.RECURRING),
    TICKET_SOLD_OUT("Tickets sell out", Occurrence.ONCE),
    ALMOST_SOLD_OUT("Tickets reach 90% sold", Occurrence.ONCE),
    REGISTRATION_OVERDUE_UNPAID("A registration is unpaid on its due date", Occurrence.RECURRING),
    DAYS_BEFORE_EVENT("On a chosen number of days before the event", Occurrence.RECURRING),
    HOURS_BEFORE_EVENT("On a chosen number of hours before the event", Occurrence.RECURRING),
    EVENT_STARTED("The event begins", Occurrence.ONCE),
    CHECKIN_RATE_HIGH("Arrivals run above the expected rate", Occurrence.RECURRING),
    BUDGET_NEARLY_SPENT("A budget heading is nearly spent", Occurrence.RECURRING),
    BUDGET_OVERSUBMITTED("A budget heading is overspent", Occurrence.ONCE),
    SPEAKER_WITHDRAWN("A speaker withdraws", Occurrence.ONCE),
    SPEAKER_DEADLINE_PASSED("A speaker is unconfirmed close to the event", Occurrence.RECURRING),
    INVOICE_OVERDUE("An invoice passes its due date", Occurrence.RECURRING),
    EVENT_COMPLETED("The event is marked complete", Occurrence.ONCE),
    LOW_FEEDBACK_SCORE("Feedback comes back poor", Occurrence.RECURRING),
    WASTE_REPORT_DUE("No sustainability data has been entered", Occurrence.RECURRING),
    MANUAL("Run by hand", Occurrence.RECURRING);

    /** Whether a trigger can usefully happen more than once per event. */
    public enum Occurrence {
        ONCE, RECURRING
    }

    private final String label;
    private final Occurrence occurrence;

    WorkflowTrigger(String label, Occurrence occurrence) {
        this.label = label;
        this.occurrence = occurrence;
    }

    public String getLabel() {
        return label;
    }

    /** Whether this trigger can fire again after it has fired once. */
    public boolean canRepeat() {
        return occurrence == Occurrence.RECURRING;
    }

    /** Whether this trigger happens after the event rather than before it. */
    public boolean isAfterTheEvent() {
        return this == EVENT_COMPLETED || this == LOW_FEEDBACK_SCORE
                || this == WASTE_REPORT_DUE;
    }

    /** Whether this trigger happens while the event is running. */
    public boolean isDuringTheEvent() {
        return this == EVENT_STARTED || this == CHECKIN_RATE_HIGH;
    }

    /** Whether the trigger's threshold must be given, as days or hours. */
    public boolean needsThreshold() {
        return this == DAYS_BEFORE_EVENT || this == HOURS_BEFORE_EVENT;
    }

    /** Whether the trigger's threshold is a percentage of capacity. */
    public boolean thresholdIsPercentage() {
        return this == ALMOST_SOLD_OUT || this == BUDGET_NEARLY_SPENT;
    }

    public static WorkflowTrigger fromLabel(String text) {
        if (text != null) {
            String needle = text.trim().replace(" ", "").replace("_", "");
            for (WorkflowTrigger trigger : values()) {
                if (trigger.name().replace("_", "").equalsIgnoreCase(needle)) {
                    return trigger;
                }
                if (trigger.label.replace(" ", "").equalsIgnoreCase(needle)) {
                    return trigger;
                }
            }
        }
        return MANUAL;
    }

    @Override
    public String toString() {
        return label;
    }
}
