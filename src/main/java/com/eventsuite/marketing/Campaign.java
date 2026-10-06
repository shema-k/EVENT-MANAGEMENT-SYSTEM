package com.eventsuite.marketing;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * One promotional push on one channel, with what it cost and what it produced.
 *
 * <p>Campaigns are tracked per event and per channel, because that is the only way
 * to answer "where should the money go next year". The number that matters is not
 * reach — reach is what you bought — but registrations produced per pound spent.
 *
 * <p>Spend is not the same as what was budgeted, and both are kept. A campaign that
 * came in under budget having produced almost nobody is a worse outcome than one
 * that overspent and filled the room, and collapsing the two into a single figure
 * would hide exactly that.
 */
public final class Campaign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Where a campaign has got to. */
    public enum Stage {
        PLANNED("Planned"),
        RUNNING("Running"),
        FINISHED("Finished"),
        PAUSED("Paused"),
        CANCELLED("Cancelled");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** The stage with this name, or PLANNED when it is not one of ours. */
        public static Stage fromLabel(String text) {
            if (text != null) {
                for (Stage stage : values()) {
                    if (stage.name().equalsIgnoreCase(text.trim())) {
                        return stage;
                    }
                    if (stage.label.equalsIgnoreCase(text.trim())) {
                        return stage;
                    }
                }
            }
            return PLANNED;
        }
    }

    private final String id;
    private final String eventId;
    private final String name;
    private final MarketingChannel channel;
    private final BigDecimal budgeted;
    private final LocalDate startsOn;
    private final LocalDate endsOn;
    private final String audience;
    private final String message;
    private final String assetNotes;

    private Stage stage;
    private BigDecimal spent;
    private int reach;
    private int clicks;
    private int registrations;

    public Campaign(String id,
                    String eventId,
                    String name,
                    MarketingChannel channel,
                    BigDecimal budgeted,
                    LocalDate startsOn,
                    LocalDate endsOn,
                    String audience,
                    String message,
                    String assetNotes,
                    Stage stage,
                    BigDecimal spent,
                    int reach,
                    int clicks,
                    int registrations) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.name = Objects.requireNonNull(name, "name");
        this.channel = channel == null ? MarketingChannel.OTHER : channel;
        this.budgeted = Money.of(budgeted);
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.audience = audience == null ? "" : audience;
        this.message = message == null ? "" : message;
        this.assetNotes = assetNotes == null ? "" : assetNotes;
        this.stage = stage == null ? Stage.PLANNED : stage;
        this.spent = Money.of(spent);
        this.reach = Math.max(0, reach);
        this.clicks = Math.max(0, clicks);
        this.registrations = Math.max(0, registrations);
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException(
                    "Campaign '" + name + "' ends before it starts");
        }
    }

    /** A planned campaign with nothing spent or measured against it yet. */
    public static Campaign planned(String id, String eventId, String name,
                                   MarketingChannel channel, BigDecimal budget,
                                   LocalDate startsOn, LocalDate endsOn) {
        return new Campaign(id, eventId, name, channel, budget, startsOn, endsOn, "", "", "",
                Stage.PLANNED, Money.ZERO, 0, 0, 0);
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public MarketingChannel getChannel() {
        return channel;
    }

    /** What was allocated to this campaign. */
    public BigDecimal getBudgeted() {
        return budgeted;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    /** Who it was aimed at: "students", "enterprise buyers". */
    public String getAudience() {
        return audience;
    }

    /** The message being put across. */
    public String getMessage() {
        return message;
    }

    /** What was produced: copy, artwork, landing page. */
    public String getAssetNotes() {
        return assetNotes;
    }

    public Stage getStage() {
        return stage;
    }

    /** What was actually spent. */
    public BigDecimal getSpent() {
        return spent;
    }

    /** How many people the campaign reached. */
    public int getReach() {
        return reach;
    }

    /** How many of them clicked through. */
    public int getClicks() {
        return clicks;
    }

    /** How many registered as a result. */
    public int getRegistrations() {
        return registrations;
    }

    /** Whether money went into this campaign. */
    public boolean isPaid() {
        return channel.isPaid();
    }

    /** Whether the results can be traced to a specific source. */
    public boolean isAttributable() {
        return channel.isAttributable();
    }

    /**
     * What each registration cost.
     *
     * <p>Returns zero when the campaign was free or produced nothing, rather than
     * dividing by zero. A channel that cost nothing has an infinite return, which
     * is not a number worth showing.
     */
    public BigDecimal getCostPerRegistration() {
        if (registrations <= 0 || spent.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.of(spent.divide(BigDecimal.valueOf(registrations), 2,
                java.math.RoundingMode.HALF_UP));
    }

    /** How far through its budget the campaign is, as a percentage. */
    public BigDecimal getBudgetUsedPercent() {
        return Money.sharePercent(spent, budgeted);
    }

    /** Money left in the campaign budget. */
    public BigDecimal getBudgetRemaining() {
        BigDecimal left = Money.subtract(budgeted, spent);
        return left.signum() < 0 ? Money.ZERO : left;
    }

    /** Whether the campaign is spending more than it was given. */
    public boolean isOverBudget() {
        return spent.compareTo(budgeted) > 0;
    }
    /** Whether the campaign brought in anybody at all. */
    public boolean producedRegistrations() {
        return registrations > 0;
    }

    /**
     * Whether this campaign is working well enough to run again.
     *
     * <p>Judged on cost per registration against what the channel usually costs,
     * rather than on a fixed threshold, because a billboard that costs more per
     * head than email is still fine and email that costs more than a billboard is
     * not.
     */
    public boolean isEfficient() {
        if (registrations <= 0) {
            return false;
        }
        double expected = channel.getCostPerRegistration();
        if (expected <= 0) {
            // A channel that is free to use and produced registrations is efficient
            // by definition.
            return true;
        }
        return getCostPerRegistration().doubleValue() <= expected * 1.5;
    }

    /** Records money spent against the campaign. */
    public void spend(BigDecimal amount) {
        spent = Money.sum(spent, amount);
    }

    /** Records what the campaign achieved. */
    public void recordResults(int newReach, int newClicks, int newRegistrations) {
        this.reach = Math.max(0, newReach);
        this.clicks = Math.max(0, newClicks);
        this.registrations = Math.max(0, newRegistrations);
    }

    /** Moves the campaign to a new stage. */
    public void moveTo(Stage next) {
        this.stage = next == null ? Stage.PLANNED : next;
    }
    /** Whether the campaign is live on a given date. */
    public boolean isRunningOn(LocalDate date) {
        if (stage != Stage.RUNNING) {
            return false;
        }
        if (startsOn != null && date.isBefore(startsOn)) {
            return false;
        }
        return endsOn == null || !date.isAfter(endsOn);
    }

    /** Whether this campaign matches a typed search. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return name.toLowerCase(Locale.ROOT).contains(needle)
                || channel.getLabel().toLowerCase(Locale.ROOT).contains(needle)
                || audience.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** A line for the campaign list. */
    public String getDisplayLine() {
        return name + "  " + channel.getLabel() + "  spent " + Money.format(spent)
                + " of " + Money.format(budgeted) + "  " + registrations + " registrations";
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
