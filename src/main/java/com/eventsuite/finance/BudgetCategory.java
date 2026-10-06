package com.eventsuite.finance;

import java.math.BigDecimal;

/**
 * The headings an event's budget and spend are grouped under.
 *
 * <p>Fixed rather than free text, because a budget is only comparable across
 * events if both are divided the same way. A festival and a conference can then be
 * held against each other on "Production" without anyone having to guess where a
 * line belonged.
 *
 * <p>Each heading carries the percentage of the total it would normally take. That
 * is what the "suggest budget" button divides by, so the starting budget follows
 * the shape of the event rather than being the same split pasted into every one.
 */
public enum BudgetCategory {

    VENUE("Venue hire", 0.25, true),
    PRODUCTION("Production & AV", 0.15, true),
    MARKETING("Marketing & advertising", 0.15, true),
    TALENT("Speakers & performers", 0.12, true),
    CATERING("Catering", 0.10, true),
    STAFFING("Staffing & stewards", 0.08, true),
    SECURITY("Security & medical", 0.05, true),
    TRAVEL("Travel & accommodation", 0.04, true),
    PRINT("Print & signage", 0.03, false),
    PLATFORMS("Platforms & ticketing fees", 0.02, false),
    INSURANCE("Insurance & licences", 0.01, false);

    private final String label;
    private final double typicalShare;
    private final boolean core;

    BudgetCategory(String label, double typicalShare, boolean core) {
        this.label = label;
        this.typicalShare = typicalShare;
        this.core = core;
    }

    public String getLabel() {
        return label;
    }

    /** The share of a typical event's budget this heading takes. */
    public double getTypicalShare() {
        return typicalShare;
    }

    /**
     * Whether this heading is one of the big ones.
     *
     * <p>Core headings are what a dashboard leads with. The rest are shown in
     * detail but not treated as headline spend, because no single one of them is
     * normally enough to change a decision.
     */
    public boolean isCore() {
        return core;
    }

    /** What a budget of this size should set aside for this heading. */
    public BigDecimal suggestedFor(BigDecimal totalBudget) {
        return Money.percentOf(totalBudget, BigDecimal.valueOf(typicalShare * 100));
    }

    /** A heading whose name matches, falling back to the first core heading. */
    public static BudgetCategory fromLabel(String text) {
        if (text != null) {
            String needle = text.trim().replace(" ", "").replace("_", "");
            for (BudgetCategory category : values()) {
                if (category.name().replace("_", "").equalsIgnoreCase(needle)) {
                    return category;
                }
                if (category.label.replace(" ", "").equalsIgnoreCase(needle)) {
                    return category;
                }
            }
        }
        return VENUE;
    }

    /** The categories a new event's budget is seeded with. */
    public static BudgetCategory[] coreCategories() {
        BudgetCategory[] all = values();
        int count = 0;
        for (BudgetCategory category : all) {
            if (category.isCore()) {
                count++;
            }
        }
        BudgetCategory[] core = new BudgetCategory[count];
        int index = 0;
        for (BudgetCategory category : all) {
            if (category.isCore()) {
                core[index++] = category;
            }
        }
        return core;
    }

    @Override
    public String toString() {
        return label;
    }
}
