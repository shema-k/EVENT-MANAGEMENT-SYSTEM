package com.eventsuite.marketing;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * A code that reduces the price of a ticket.
 *
 * <p>Discounts are the most abused mechanism in ticketing, so the limits are part of
 * the record rather than left to the person typing it in. A code knows how many
 * times it may be used, how much it takes off, which tiers it applies to and when
 * it expires. That is what stops a well-meant "10% off partners" becoming an
 * unlimited code that quietly costs a third of the revenue.
 *
 * <p>Codes are also the honest answer to a question about why revenue fell: every
 * ticket sold through a code records it, so the discount total is computable rather
 * than a feeling at the end of the month.
 */
public final class DiscountCode implements Serializable {
    private static final long serialVersionUID = 1L;

    /** What kind of reduction the code gives. */
    public enum Kind {
        PERCENT("Percent off"),
        AMOUNT("Amount off"),
        FIXED_PRICE("Fixed price");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** The kind with this name, or a percentage off when it is not one of ours. */
        public static Kind fromLabel(String text) {
            if (text != null) {
                for (Kind kind : values()) {
                    if (kind.name().equalsIgnoreCase(text.trim())) {
                        return kind;
                    }
                    if (kind.label.equalsIgnoreCase(text.trim())) {
                        return kind;
                    }
                }
            }
            return PERCENT;
        }
    }

    /** Discounts are held at or below this, so a code cannot give a ticket away. */
    public static final BigDecimal MAXIMUM_DISCOUNT =
            BigDecimal.valueOf(90).setScale(2, java.math.RoundingMode.HALF_UP);

    private final String id;
    private final String eventId;
    private final String code;
    private final Kind kind;
    private final BigDecimal value;
    private final int usageLimit;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final String appliesToTiers;
    private final String note;

    private int timesUsed;

    public DiscountCode(String id,
                        String eventId,
                        String code,
                        Kind kind,
                        BigDecimal value,
                        int usageLimit,
                        LocalDate validFrom,
                        LocalDate validUntil,
                        String appliesToTiers,
                        String note) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.code = Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
        if (this.code.isEmpty()) {
            throw new IllegalArgumentException("A discount code cannot be blank");
        }
        this.kind = kind == null ? Kind.PERCENT : kind;
        this.value = Money.of(value);
        this.usageLimit = usageLimit;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.appliesToTiers = appliesToTiers == null ? "" : appliesToTiers.trim();
        this.note = note == null ? "" : note.trim();
        this.timesUsed = 0;
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException(
                    "Discount code " + this.code + " expires before it starts");
        }
        if (this.kind == Kind.PERCENT && this.value.compareTo(MAXIMUM_DISCOUNT) > 0) {
            throw new IllegalArgumentException(
                    "A discount of " + this.value.stripTrailingZeros().toPlainString()
                            + "% is too large; the maximum is "
                            + MAXIMUM_DISCOUNT.stripTrailingZeros().toPlainString() + "%");
        }
    }
    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    /** The code as a buyer types it: upper case. */
    public String getCode() {
        return code;
    }

    public Kind getKind() {
        return kind;
    }

    /** Percent, amount, or fixed price, depending on the kind. */
    public BigDecimal getValue() {
        return value;
    }

    /** How many times it may be used. Zero means unlimited. */
    public int getUsageLimit() {
        return usageLimit;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    /** Comma-separated tier names this applies to, or empty for all of them. */
    public String getAppliesToTiers() {
        return appliesToTiers;
    }

    public String getNote() {
        return note;
    }

    /** How many times it has been redeemed. */
    public int getTimesUsed() {
        return timesUsed;
    }

    /** Whether there is no limit on uses. */
    public boolean isUnlimited() {
        return usageLimit <= 0;
    }

    /** Whether the limit has been reached. */
    public boolean isExhausted() {
        return !isUnlimited() && timesUsed >= usageLimit;
    }

    /** Whether the date is inside the window, if there is one. */
    public boolean isInWindow(LocalDate date) {
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        return validUntil == null || !date.isAfter(validUntil);
    }
    /** Whether the code applies to a named ticket tier. */
    public boolean appliesTo(String tierName) {
        if (appliesToTiers.isEmpty() || tierName == null) {
            return true;
        }
        String needle = tierName.trim().toLowerCase(Locale.ROOT);
        for (String allowed : appliesToTiers.split(",")) {
            if (!allowed.isBlank() && allowed.trim().toLowerCase(Locale.ROOT).equals(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Why the code cannot be used right now.
     *
     * <p>An empty string means it can. Written as a reason rather than a boolean so
     * the checkout can tell the buyer what is wrong instead of silently ignoring
     * their code.
     */
    public String getRejectionReason(LocalDate date, String tierName, BigDecimal price) {
        if (isExhausted()) {
            return "This code has reached its limit of " + usageLimit + " uses.";
        }
        if (!isInWindow(date)) {
            return validUntil != null && date.isAfter(validUntil)
                    ? "This code expired on " + validUntil + "."
                    : "This code is not yet valid.";
        }
        if (!appliesTo(tierName)) {
            return "This code does not apply to the " + tierName + " ticket.";
        }
        if (!reduces(price)) {
            return "This code does not reduce the price of that ticket.";
        }
        return "";
    }

    /** Whether the code would actually take money off this price. */
    public boolean reduces(BigDecimal price) {
        return priceOf(price).compareTo(Money.of(price)) < 0;
    }

    /**
     * The price after the discount.
     *
     * <p>A fixed-price code can never make a ticket dearer, so a ticket priced above
     * the code's value is left alone rather than raised.
     */
    public BigDecimal priceOf(BigDecimal price) {
        BigDecimal original = Money.of(price);
        return switch (kind) {
            case PERCENT -> Money.applyPercent(original, value);
            case AMOUNT -> Money.of(Money.subtract(original, value));
            case FIXED_PRICE -> value.compareTo(original) < 0 ? value : original;
        };
    }
    /** A copy recording another redemption. */
    public DiscountCode withOneMoreUse() {
        return withUses(timesUsed + 1);
    }

    /** A copy with a different number of redemptions already recorded. */
    public DiscountCode withUses(int used) {
        DiscountCode copy = new DiscountCode(id, eventId, code, kind, value, usageLimit,
                validFrom, validUntil, appliesToTiers, note);
        copy.timesUsed = Math.max(0, used);
        return copy;
    }

    /**
     * Restores the redemption count read back from the database.
     *
     * <p>Set directly rather than by replaying a redemption per use, so loading a code
     * that has been used forty times does not build forty intermediate objects.
     */
    public void restoreUses(int used) {
        this.timesUsed = Math.max(0, used);
    }
    /** Whether this code matches a typed search. */
    public boolean matches(String query) {
        return query == null || query.isBlank()
                || code.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))
                || note.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    /** A line for the discount list. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(code);
        line.append("  ").append(kind.getLabel()).append(" ").append(
                value.stripTrailingZeros().toPlainString());
        if (kind == Kind.PERCENT) {
            line.append("%");
        } else {
            line.append(" (");
            line.append(Money.format(value));
            line.append(")");
        }
        line.append("  used ").append(timesUsed);
        if (!isUnlimited()) {
            line.append(" of ").append(usageLimit);
        }
        return line.toString();
    }
    @Override
    public String toString() {
        return getDisplayLine();
    }
}
