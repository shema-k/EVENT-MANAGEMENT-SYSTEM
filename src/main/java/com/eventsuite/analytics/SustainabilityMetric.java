package com.eventsuite.analytics;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What the event did to the environment, measured rather than estimated.
 *
 * <p>Sustainability claims are usually assertions. This record forces each category
 * to be an actual number with a unit, and provides the conversion factors so
 * different units add up to one comparable footprint. That is what makes it
 * possible to say an event emitted less than another, which a pile of unconverted
 * figures cannot do.
 *
 * <p>Factors are held per unit rather than per event, so waste in kilograms, energy
 * in kilowatt hours and travel in kilometres all contribute to a single
 * carbon figure. The factors are stated rather than hidden, because a footprint
 * computed with undisclosed assumptions is not evidence of anything.
 */
public final class SustainabilityMetric implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Kilograms of carbon dioxide equivalent per kilowatt hour of grid electricity. */
    public static final BigDecimal CARBON_PER_KWH = new BigDecimal("0.233");

    /** Kilograms of carbon dioxide equivalent per kilogram of general waste. */
    public static final BigDecimal CARBON_PER_KG_WASTE = new BigDecimal("0.45");

    /** Kilograms of carbon dioxide equivalent per kilogram of food waste. */
    public static final BigDecimal CARBON_PER_KG_FOOD = new BigDecimal("2.5");

    /** Kilograms of carbon dioxide equivalent per litre of fuel burnt by a van. */
    public static final BigDecimal CARBON_PER_LITRE_FUEL = new BigDecimal("2.31");

    /** Kilograms of carbon dioxide equivalent per passenger kilometre flown. */
    public static final BigDecimal CARBON_PER_FLIGHT_KM = new BigDecimal("0.15");

    /** Litres of water used per person for catering and cleaning. */
    public static final BigDecimal WATER_PER_PERSON = new BigDecimal("12");

    /** The measures a track. Fixed so the dashboard compares like with like. */
    public enum Measure {
        ELECTRICITY_KWH("Electricity", "kWh"),
        GAS_KWH("Gas", "kWh"),
        WASTE_KG("General waste", "kg"),
        FOOD_WASTE_KG("Food waste", "kg"),
        RECYCLED_KG("Recycled", "kg"),
        WATER_LITRES("Water", "litres"),
        FUEL_LITRES("Vehicle fuel", "litres"),
        FLIGHT_KM("Air travel", "km"),
        PLASTIC_KG("Single-use plastic", "kg");

        private final String label;
        private final String unit;

        Measure(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }

        public String getLabel() {
            return label;
        }

        public String getUnit() {
            return unit;
        }

        /**
         * Kilograms of carbon dioxide equivalent per unit of this measure.
         *
         * <p>Zero for measures that do not contribute directly to a footprint, such
         * as recycled material, which is counted for diversion rate instead.
         */
        public BigDecimal carbonFactor() {
            return switch (this) {
                case ELECTRICITY_KWH, GAS_KWH -> CARBON_PER_KWH;
                case WASTE_KG -> CARBON_PER_KG_WASTE;
                case FOOD_WASTE_KG -> CARBON_PER_KG_FOOD;
                case FUEL_LITRES -> CARBON_PER_LITRE_FUEL;
                case FLIGHT_KM -> CARBON_PER_FLIGHT_KM;
                case WATER_LITRES, RECYCLED_KG, PLASTIC_KG -> BigDecimal.ZERO;
            };
        }

        /** Whether this measure counts towards how much went to landfill. */
        public boolean countsAsWaste() {
            return this == WASTE_KG || this == FOOD_WASTE_KG;
        }
        /** The measure with this name, or general waste when it is not one of ours. */
        public static Measure fromLabel(String text) {
            if (text != null) {
                String needle = text.trim().replace(" ", "").replace("_", "");
                for (Measure measure : values()) {
                    if (measure.name().replace("_", "").equalsIgnoreCase(needle)) {
                        return measure;
                    }
                }
            }
            return WASTE_KG;
        }
    }

    private final String id;
    private final String eventId;
    private final Measure measure;
    private final BigDecimal quantity;
    private final String source;
    private final String note;
    private final boolean estimated;

    public SustainabilityMetric(String id,
                                String eventId,
                                Measure measure,
                                BigDecimal quantity,
                                String source,
                                String note,
                                boolean estimated) {
        this.id = id;
        this.eventId = eventId;
        this.measure = measure == null ? Measure.WASTE_KG : measure;
        this.quantity = Money.of(quantity).max(BigDecimal.ZERO);
        this.source = source == null ? "" : source.trim();
        this.note = note == null ? "" : note.trim();
        this.estimated = estimated;
    }
    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Measure getMeasure() {
        return measure;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    /** Where the number came from: a meter, an invoice, a haulier, a guess. */
    public String getSource() {
        return source;
    }

    public String getNote() {
        return note;
    }

    /** Whether this figure is an estimate rather than a reading. */
    public boolean isEstimated() {
        return estimated;
    }

    /** The carbon dioxide equivalent of this measure, in kilograms. */
    public BigDecimal getCarbonKg() {
        return Money.of(quantity.multiply(measure.carbonFactor()));
    }

    /** The figure with its unit, for the report. */
    public String getQuantityLabel() {
        return quantity.stripTrailingZeros().toPlainString() + " " + measure.getUnit();
    }

    /** Whether this measure goes towards the waste total. */
    public boolean isWaste() {
        return measure.countsAsWaste();
    }

    /** A line for the sustainability table. */
    public String getDisplayLine() {
        return measure.getLabel() + "  " + getQuantityLabel()
                + (estimated ? "  (estimated)" : "")
                + (source.isEmpty() ? "" : "  from " + source);
    }

    /**
     * Adds up the footprint of several metrics.
     *
     * <p>Only measures with a carbon factor contribute. Recycling is deliberately
     * excluded: diverting material from landfill is good, but counting it as a
     * negative emission would let an event net its footprint to zero, which is not
     * what happened.
     */
    public static BigDecimal totalCarbonKg(Iterable<SustainabilityMetric> metrics) {
        BigDecimal total = Money.ZERO;
        for (SustainabilityMetric metric : metrics) {
            total = Money.sum(total, metric.getCarbonKg());
        }
        return total;
    }

    /**
     * The share of waste that was recycled rather than sent to landfill.
     *
     * <p>Recycled mass over total mass generated, expressed as a percentage. Where
     * nothing was recorded it returns zero rather than guessing a hundred per cent
     * for an event that produced no waste at all.
     */
    public static BigDecimal diversionRate(Iterable<SustainabilityMetric> metrics) {
        BigDecimal generated = Money.ZERO;
        BigDecimal recycled = Money.ZERO;
        for (SustainabilityMetric metric : metrics) {
            if (metric.getMeasure() == Measure.RECYCLED_KG) {
                recycled = Money.sum(recycled, metric.getQuantity());
            } else if (metric.isWaste()) {
                generated = Money.sum(generated, metric.getQuantity());
            }
        }
        BigDecimal totalWaste = Money.sum(generated, recycled);
        if (totalWaste.signum() == 0) {
            return Money.ZERO;
        }
        return Money.of(recycled.multiply(BigDecimal.valueOf(100))
                .divide(totalWaste, 1, RoundingMode.HALF_UP));
    }

    /** A copy with a different quantity, used when a real reading replaces an estimate. */
    public SustainabilityMetric withQuantity(BigDecimal newQuantity) {
        return new SustainabilityMetric(id, eventId, measure, newQuantity, source, note, estimated);
    }
    @Override
    public String toString() {
        return getDisplayLine();
    }
}
