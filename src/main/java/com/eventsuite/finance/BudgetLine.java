package com.eventsuite.finance;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One heading of an event's budget, and how it is going.
 *
 * <p>Three numbers, not one, because money moves in three steps. {@code planned}
 * is what was set aside, {@code committed} is what has been promised to suppliers
 * and speakers but not yet paid, and {@code actual} is what has left the account.
 * Only the first is under the organiser's control; the second is already spoken
 * for; the third is the fact.
 *
 * <p>Reporting a single "spent" figure is the standard mistake. Two thirds of a
 * production budget committed six weeks out looks exactly like a disaster if it is
 * reported as spend, and looks healthy if it is not reported at all. Keeping all
 * three side by side is what makes the difference visible: committed plus actual
 * is the real exposure, and it is the number the variance is measured against.
 */
public final class BudgetLine implements Serializable {
    private static final long serialVersionUID = 1L;

    /** How close to the limit counts as a warning on the dashboard. */
    public static final BigDecimal WARNING_THRESHOLD =
            BigDecimal.valueOf(85).setScale(2, java.math.RoundingMode.HALF_UP);

    private final String id;
    private final String eventId;
    private final BudgetCategory category;
    private final BigDecimal planned;
    private final String note;

    private BigDecimal committed;
    private BigDecimal actual;

    public BudgetLine(String id,
                      String eventId,
                      BudgetCategory category,
                      BigDecimal planned,
                      BigDecimal committed,
                      BigDecimal actual,
                      String note) {
        this.id = id;
        this.eventId = eventId;
        this.category = category == null ? BudgetCategory.VENUE : category;
        this.planned = Money.of(planned).max(BigDecimal.ZERO);
        this.committed = Money.of(committed);
        this.actual = Money.of(actual);
        this.note = note == null ? "" : note;
    }

    /** A freshly planned heading with nothing committed or spent against it. */
    public static BudgetLine plannedFor(String id, String eventId, BudgetCategory category,
                                        BigDecimal amount, String note) {
        return new BudgetLine(id, eventId, category, amount, Money.ZERO, Money.ZERO, note);
    }

    /** A heading allocated the share of a total that its category normally takes. */
    public static BudgetLine suggested(String id, String eventId, BudgetCategory category,
                                       BigDecimal totalBudget) {
        return plannedFor(id, eventId, category, category.suggestedFor(totalBudget),
                category.getTypicalShare() * 100 + "% typical for this kind of event");
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public BudgetCategory getCategory() {
        return category;
    }

    /** What was set aside for this heading. */
    public BigDecimal getPlanned() {
        return planned;
    }

    /** Promised to suppliers and speakers but not yet paid. */
    public BigDecimal getCommitted() {
        return committed;
    }

    /** Paid out so far. */
    public BigDecimal getActual() {
        return actual;
    }

    public String getNote() {
        return note;
    }

    /**
     * What is still available to spend.
     *
     * <p>Committed money is deducted as well as spent money, because it is already
     * spoken for. Leaving it out is how an event books a supplier it has no money
     * left for.
     */
    public BigDecimal getRemaining() {
        BigDecimal left = Money.subtract(planned, Money.sum(committed, actual));
        return left.signum() < 0 ? Money.ZERO : left;
    }

    /**
     * Planned less committed less spent.
     *
     * <p>Left negative when the heading is overspent, because that is the fact worth
     * showing. {@link #getRemaining()} clamps it for callers that want a figure to
     * spend against.
     */
    public BigDecimal getVariance() {
        return Money.subtract(Money.subtract(planned, committed), actual);
    }

    /** Whether more is promised or spent than was set aside. */
    public boolean isOverspent() {
        return getVariance().signum() < 0;
    }

    /** Whether this heading is close enough to its limit to be worth watching. */
    public boolean isNearLimit() {
        if (planned.signum() == 0) {
            // No budget set and any spend at all is a problem worth flagging.
            return Money.isPositive(actual) || Money.isPositive(committed);
        }
        return Money.sharePercent(Money.sum(committed, actual), planned)
                .compareTo(WARNING_THRESHOLD) >= 0;
    }

    /** What share of the planned figure is already committed or spent. */
    public BigDecimal getUsedPercent() {
        return Money.sharePercent(Money.sum(committed, actual), planned);
    }

    /** What share of the planned figure has actually been paid out. */
    public BigDecimal getSpentPercent() {
        return Money.sharePercent(actual, planned);
    }

    /** A copy with a different planned figure. */
    public BudgetLine withPlanned(BigDecimal amount) {
        return new BudgetLine(id, eventId, category, amount, committed, actual, note);
    }
    /** Records money promised but not yet paid. */
    public void commit(BigDecimal amount) {
        this.committed = Money.sum(committed, amount);
    }

    /** Records money paid out, reducing what is still committed for it. */
    public void spend(BigDecimal amount) {
        BigDecimal value = Money.of(amount);
        this.actual = Money.sum(actual, value);
        BigDecimal stillCommitted = Money.subtract(committed, value);
        this.committed = stillCommitted.signum() < 0 ? Money.ZERO : stillCommitted;
    }
    /** How this heading should read in the status column. */
    public String getHealthLabel() {
        if (isOverspent()) {
            return "Over by " + Money.format(Money.abs(getVariance()));
        }
        if (planned.signum() == 0) {
            return Money.isPositive(actual) ? "No budget set" : "Unfunded";
        }
        BigDecimal used = getUsedPercent();
        if (used.compareTo(WARNING_THRESHOLD) >= 0) {
            return "At limit";
        }
        return Money.format(getRemaining()) + " left";
    }

    /** A line for the budget table. */
    public String getDisplayLine() {
        return category.getLabel() + "  planned " + Money.format(planned)
                + "  committed " + Money.format(committed)
                + "  spent " + Money.format(actual);
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
