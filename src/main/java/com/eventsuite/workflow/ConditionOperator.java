package com.eventsuite.workflow;

/**
 * How a rule compares a field against its threshold.
 *
 * <p>Only the six operations anybody actually needs for a threshold test. Anything
 * more expressive would mean a rule that cannot be shown on a single line in the
 * interface and cannot be understood by whoever has to maintain it at 11pm the
 * night before the event.
 */
public enum ConditionOperator {

    AT_MOST("<="),
    LESS_THAN("<"),
    AT_LEAST(">="),
    MORE_THAN(">"),
    EXACTLY("="),
    NOT_EQUALS("!=");

    private final String symbol;

    ConditionOperator(String symbol) {
        this.symbol = symbol;
    }

    /** The arithmetic symbol, so a rule reads as a sentence. */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Tests the comparison.
     *
     * <p>Takes the comparison result rather than the two operands, because that is
     * what {@code BigDecimal.compareTo} gives: it ignores scale, so 7.0 equals 7,
     * which {@code equals} would deny. Getting that wrong would make a rule on
     * "days until event = 7" fail on the day.
     */
    public boolean test(int comparison) {
        return switch (this) {
            case AT_MOST -> comparison <= 0;
            case LESS_THAN -> comparison < 0;
            case AT_LEAST -> comparison >= 0;
            case MORE_THAN -> comparison > 0;
            case EXACTLY -> comparison == 0;
            case NOT_EQUALS -> comparison != 0;
        };
    }

    /** A label for the rule editor. */
    public String getLabel() {
        return switch (this) {
            case AT_MOST -> "is at most";
            case LESS_THAN -> "is less than";
            case AT_LEAST -> "is at least";
            case MORE_THAN -> "is more than";
            case EXACTLY -> "is exactly";
            case NOT_EQUALS -> "is not";
        };
    }

    public static ConditionOperator fromLabel(String text) {
        if (text != null) {
            String needle = text.trim().toLowerCase(java.util.Locale.ROOT);
            for (ConditionOperator operator : values()) {
                if (operator.name().toLowerCase(java.util.Locale.ROOT).equals(needle)) {
                    return operator;
                }
                if (operator.symbol.equals(needle) || operator.getLabel().equals(needle)) {
                    return operator;
                }
            }
        }
        return AT_LEAST;
    }

    @Override
    public String toString() {
        return symbol;
    }
}
