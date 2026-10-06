package com.eventsuite.workflow;

import java.util.Locale;

/**
 * What a rule does when its condition is met.
 *
 * <p>Actions that change money or capacity carry a guard. An automation that marks
 * an event sold out or issues a refund is acting on the whole event, and getting
 * the condition slightly wrong is expensive. Those actions say so here, and the
 * engine refuses to run them without an explicit confirmation, rather than trusting
 * a threshold that somebody typed in a hurry.
 *
 * <p>The first few actions are the ones worth having. An event organiser spends most
 * of their time chasing confirmations and re-sending information, so the automation
 * earns its keep by removing the chasing.
 */
public enum WorkflowAction {

    SEND_EMAIL("Send an email to attendees", false, false, false),
    NOTIFY_ORGANISER("Show a notice in the interface", true, false, false),
    CREATE_TASK("Add a task to the checklist", true, false, false),
    LOG_ONLY("Record it and do nothing else", false, false, false),
    EXPORT_REPORT("Write a report file", false, false, false),
    ALERT_BUDGET("Warn that a budget heading is at risk", true, false, false),
    MARK_SOLD_OUT("Mark the event sold out", false, true, true),
    HOLD_REMAINING_TICKETS("Stop further sales", false, true, true),
    PROMOTE_WAITLIST("Move people from the waiting list", false, true, true),
    ISSUE_REFUND("Refund the outstanding payments", false, true, true),
    RAISE_INVOICE("Raise an invoice for what is owed", false, true, false),
    RECORD_SPONSOR_CONTACT("Record a sponsorship lead", true, false, false);

    private final String label;
    private final boolean visibleToOrganiser;
    private final boolean changesMoneyOrCapacity;
    private final boolean needsConfirmation;

    WorkflowAction(String label, boolean visibleToOrganiser, boolean changesMoneyOrCapacity,
                    boolean needsConfirmation) {
        this.label = label;
        this.visibleToOrganiser = visibleToOrganiser;
        this.changesMoneyOrCapacity = changesMoneyOrCapacity;
        this.needsConfirmation = needsConfirmation;
    }

    public String getLabel() {
        return label;
    }

    /** Whether this shows up as something the organiser will notice. */
    public boolean isVisibleToOrganiser() {
        return visibleToOrganiser;
    }

    /** Whether running this affects money or capacity. */
    public boolean changesMoneyOrCapacity() {
        return changesMoneyOrCapacity;
    }

    /**
     * Whether the engine will refuse to run this without a person saying yes.
     *
     * <p>Set on the three actions where a mistake cannot be undone from the
     * interface: marking an event sold out, stopping sales and issuing refunds.
     */
    public boolean needsConfirmation() {
        return needsConfirmation;
    }

    /** Whether this action is safe to run unattended on a timer. */
    public boolean isSafeToAutomate() {
        return !needsConfirmation();
    }

    /** The default message an action sends, given its parameter. */
    public String getDefaultMessage(String parameter) {
        String text = parameter == null || parameter.isBlank() ? "" : parameter.trim();
        return switch (this) {
            case SEND_EMAIL -> text.isEmpty()
                    ? "There is an update to your event." : text;
            case CREATE_TASK -> text.isEmpty() ? "Follow up on the automation rule." : text;
            case RECORD_SPONSOR_CONTACT -> text.isEmpty()
                    ? "A sponsor has enquired about this event." : text;
            case ALERT_BUDGET -> text.isEmpty()
                    ? "A budget heading needs attention." : text;
            default -> text;
        };
    }

    /** Whether this action needs a message or parameter to mean anything. */
    public boolean needsParameter() {
        return this == SEND_EMAIL || this == CREATE_TASK || this == RECORD_SPONSOR_CONTACT;
    }

    public static WorkflowAction fromLabel(String text) {
        if (text != null) {
            String needle = text.trim().replace(" ", "").replace("_", "");
            for (WorkflowAction action : values()) {
                if (action.name().replace("_", "").equalsIgnoreCase(needle)) {
                    return action;
                }
                if (action.label.replace(" ", "").equalsIgnoreCase(needle)) {
                    return action;
                }
            }
        }
        // No recognisable match falls back to a visible notice, which is the
        // safest action to guess at: it tells somebody rather than acting for them.
        return NOTIFY_ORGANISER;
    }

    @Override
    public String toString() {
        return label;
    }
}
