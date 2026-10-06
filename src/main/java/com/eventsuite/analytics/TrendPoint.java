package com.eventsuite.analytics;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One point on a time series drawn on a dashboard.
 *
 * <p>Trends need a label and a value, and the label has to be readable at the size
 * the chart is drawn. A date truncated to the hour works for arrivals; a full
 * timestamp does not fit under a bar. The formatter is therefore chosen per series
 * rather than stored globally.
 */
public final class TrendPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.UK);
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.UK);

    /** How much of a time each point covers. */
    public enum Granularity {
        MINUTE, HOUR, DAY, MONTH
    }

    private final LocalDateTime at;
    private final double value;
    private final String label;

    public TrendPoint(LocalDateTime at, double value) {
        this(at, value, at == null ? "" : at.format(CLOCK));
    }

    public TrendPoint(LocalDateTime at, double value, String label) {
        this.at = at;
        this.value = value;
        this.label = label == null ? "" : label;
    }

    /** A point labelled the way its series needs. */
    public static TrendPoint of(LocalDateTime at, double value, Granularity granularity) {
        return new TrendPoint(at, value, format(at, granularity));
    }

    private static String format(LocalDateTime at, Granularity granularity) {
        if (at == null) {
            return "";
        }
        return switch (granularity == null ? Granularity.HOUR : granularity) {
            case MINUTE -> at.format(CLOCK);
            case HOUR -> at.format(HOUR);
            case DAY -> at.format(DAY);
            case MONTH -> at.format(MONTH);
        };
    }

    public LocalDateTime getAt() {
        return at;
    }

    public double getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /** The value rounded for display, with no trailing zeros. */
    public String getValueLabel() {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.UK, "%.1f", value);
    }
    @Override
    public String toString() {
        return getLabel() + ": " + getValueLabel();
    }
}
