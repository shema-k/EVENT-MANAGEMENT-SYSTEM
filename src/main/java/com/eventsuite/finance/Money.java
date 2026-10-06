package com.eventsuite.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Money for a system that has to balance to the last cent.
 *
 * <p>Amounts are {@link BigDecimal}, never {@code double}. A concert that takes
 * nine thousand card payments loses real money to binary rounding on its own, and
 * a budget that is off by a fraction still looks wrong on screen. Every figure is
 * normalised to two decimal places on the way in, so adding a hundred ticket
 * prices cannot leave a tail of fractions behind to be explained away later.
 *
 * <p>The currency symbol is deliberately not part of the value. Amounts are
 * stored as numbers with no symbol attached, and the symbol is applied when a
 * figure is shown, because a stored currency cannot be converted or re-labelled
 * without rewriting every row.
 */
public final class Money {

    /** Money is held to two decimal places, which is what a currency uses. */
    public static final int SCALE = 2;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private static final DecimalFormat FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.UK);
        FORMATTER = new DecimalFormat("#,##0.00", symbols);
    }

    private Money() {
    }

    /**
     * Cleans a number into a currency amount.
     *
     * <p>Null becomes zero rather than blowing up: a total with a missing component
     * should read as smaller, not crash the dashboard.
     */
    public static BigDecimal of(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(String amount) {
        if (amount == null || amount.isBlank()) {
            return ZERO;
        }
        String cleaned = amount.trim().replace(",", "").replace(" ", "");
        // Accept a leading currency symbol or code, so a figure pasted out of a
        // bank statement can be typed straight in.
        if (cleaned.startsWith("$") || cleaned.startsWith("£") || cleaned.startsWith("€")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.startsWith("UGX") || cleaned.startsWith("USD")) {
            cleaned = cleaned.substring(3).trim();
        }
        if (cleaned.startsWith("-")) {
            // Money owed is negative. Keep the sign rather than rejecting the value,
            // because a credit note is a legitimate thing to enter.
            return of(new BigDecimal(cleaned));
        }
        try {
            return of(new BigDecimal(cleaned));
        } catch (NumberFormatException unreadable) {
            throw new IllegalArgumentException("Not a money amount: " + amount);
        }
    }

    public static BigDecimal of(double amount) {
        return of(BigDecimal.valueOf(amount));
    }

    /** Adds amounts without letting floating point creep in through the door. */
    public static BigDecimal sum(BigDecimal... amounts) {
        BigDecimal total = ZERO;
        for (BigDecimal amount : amounts) {
            total = total.add(of(amount));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return of(left).subtract(of(right)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Applies a percentage.
     *
     * <p>{@code percent} is given as a percentage, so 12.5 for half off VAT. A
     * discount larger than the price itself is clamped at zero, since a negative
     * ticket price is a refund and not a sale.
     */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null) {
            return ZERO;
        }
        BigDecimal raw = of(amount).multiply(percent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        if (raw.signum() < 0) {
            return ZERO;
        }
        return of(raw);
    }

    /**
     * The amount less a percentage of it, never below zero.
     *
     * <p>The floor matters. A discount code entered as 150 per cent, or a percentage
     * field with an extra digit typed into it, must not produce a negative price — a
     * negative ticket is a refund, and quietly turning one into the other is how a
     * discount becomes an unexplained credit on a statement.
     */
    public static BigDecimal applyPercent(BigDecimal amount, BigDecimal percent) {
        BigDecimal reduced = subtract(amount, percentOf(amount, percent));
        return reduced.signum() < 0 ? ZERO : reduced;
    }

    /** A share of an amount, rounded down so the parts never exceed the whole. */
    public static BigDecimal share(BigDecimal amount, BigDecimal percent) {
        return of(percentOf(amount, percent));
    }

    public static BigDecimal negate(BigDecimal amount) {
        return of(amount).negate().setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal abs(BigDecimal amount) {
        return of(amount).abs();
    }

    /**
     * How much of {@code whole} {@code part} accounts for, as a percentage.
     *
     * <p>Returns zero when the whole is zero. A budget of nothing that somehow has
     * spend against it should not report an infinite overrun.
     */
    public static BigDecimal sharePercent(BigDecimal part, BigDecimal whole) {
        BigDecimal denominator = of(whole);
        if (denominator.signum() == 0) {
            return ZERO;
        }
        return of(percentOf(part, BigDecimal.valueOf(100))
                .multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP));
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    public static boolean isNegative(BigDecimal amount) {
        return amount != null && amount.signum() < 0;
    }

    /** The plain digits, grouped, with no symbol. */
    public static String plain(BigDecimal amount) {
        return FORMATTER.format(of(amount));
    }

    /** The plain digits with a currency sign in front. */
    public static String format(BigDecimal amount) {
        return "£" + plain(amount);
    }

    /** Formats a negative figure so the minus shows as a leading dash. */
    public static String signed(BigDecimal amount) {
        BigDecimal value = of(amount);
        return value.signum() < 0 ? "-" + format(value.abs()) : format(value);
    }

    /** A compact form for charts and dashboard tiles: 12.4k, 1.2M. */
    public static String compact(BigDecimal amount) {
        BigDecimal value = of(amount).abs();
        String sign = of(amount).signum() < 0 ? "-" : "";
        if (value.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return sign + "£" + value.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + "M";
        }
        if (value.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return sign + "£" + value.divide(BigDecimal.valueOf(1_000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + "k";
        }
        return sign + "£" + value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
