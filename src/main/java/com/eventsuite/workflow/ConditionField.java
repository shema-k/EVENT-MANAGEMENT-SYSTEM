package com.eventsuite.workflow;

/**
 * The numbers a rule can be tested against.
 *
 * <p>Automation is only worth having if the conditions read as plainly as the
 * actions. Rather than letting a rule hold a fragment of a formula that nobody can
 * check, conditions name a field from this list and an operator from its
 * counterpart. Every combination is evaluated the same way, so a rule cannot be
 * clever in a way its author did not intend.
 *
 * <p>Fields are resolved against the {@link WorkflowContext} carrying the facts
 * about one event at one moment. A field that context does not carry evaluates to
 * zero rather than failing, which stops a rule written for one screen from breaking
 * the automation run of another.
 */
public enum ConditionField {

    DAYS_UNTIL_EVENT("Days until the event", true),
    HOURS_UNTIL_EVENT("Hours until the event", true),
    PERCENT_SOLD("Percentage of tickets sold", false),
    TICKETS_SOLD("Tickets sold", false),
    TICKETS_REMAINING("Tickets remaining", false),
    REVENUE_COLLECTED("Revenue collected", false),
    BUDGET_SPENT("Budget spent", false),
    BUDGET_REMAINING("Budget remaining", false),
    BUDGET_OVERSHOULD("Budget headings overspent", false),
    INVOICES_OVERDUE("Invoices overdue", false),
    MONEY_OUTSTANDING("Money still owed", false),
    CONFIRMED_REGISTRATIONS("Confirmed registrations", false),
    WAITLISTED_REGISTRATIONS("People on the waiting list", false),
    SPEAKERS_UNCONFIRMED("Speakers not yet confirmed", false),
    SPEAKERS_WITHDRAWN("Speakers who have withdrawn", false),
    CHECKED_IN("People checked in", false),
    CHECKINS_LAST_HOUR("Check-ins in the last hour", false),
    FEEDBACK_SCORE("Average feedback score", false),
    POOR_FEEDBACK_COUNT("Feedback scored bad or worse", false),
    CARBON_KG("Carbon footprint in kilograms", false),
    RECYCLING_RATE("Recycling rate", false),
    MARKETING_SPEND("Marketing spend", false),
    WAITLIST_LENGTH("Waiting list length", false);

    private final String label;
    private final boolean wholeNumber;

    ConditionField(String label, boolean wholeNumber) {
        this.label = label;
        this.wholeNumber = wholeNumber;
    }

    public String getLabel() {
        return label;
    }

    /** Whether this field should be compared as a whole number, not a decimal. */
    public boolean isWholeNumber() {
        return wholeNumber;
    }

    /**
     * Whether the field means something before the event happens.
     *
     * <p>Used to warn about rules that can never fire: a condition on feedback score
     * attached to a trigger that runs the day tickets go on sale will sit there
     * silently evaluating zero forever.
     */
    public boolean isPostEvent() {
        return this == FEEDBACK_SCORE || this == POOR_FEEDBACK_COUNT
                || this == CARBON_KG || this == RECYCLING_RATE || this == CHECKED_IN
                || this == CHECKINS_LAST_HOUR;
    }

    public static ConditionField fromLabel(String text) {
        if (text != null) {
            String needle = text.trim().replace(" ", "").replace("_", "");
            for (ConditionField field : values()) {
                if (field.name().replace("_", "").equalsIgnoreCase(needle)) {
                    return field;
                }
                if (field.label.replace(" ", "").equalsIgnoreCase(needle)) {
                    return field;
                }
            }
        }
        return DAYS_UNTIL_EVENT;
    }

    @Override
    public String toString() {
        return label;
    }
}
