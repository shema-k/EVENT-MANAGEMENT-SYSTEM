package com.eventsuite.analytics;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Whether an event made its money back, and how convincingly.
 *
 * <p>Return on investment for an event is not one number, because an event's return
 * is only partly ticket income. A conference that fills the room and returns two
 * pounds per pound has still built a mailing list, a speaker roster and a
 * reputation, and a festival that breaks even has still had a profitable second year
 * because people came back.
 *
 * <p>So three figures are reported side by side. {@link #getTicketReturnMultiple()}
 * is the strictest and the only one that can be audited from the ledger alone.
 * {@link #getTotalReturnMultiple()} includes sponsorship and exhibitors. And
 * {@link #getBreakEvenAttendees()} says how many people the event needed in the
 * door, which is the figure an organiser can actually act on.
 *
 * <p>Costs that are not cash are reported separately. Writing down equipment and
 * paying a supplier thirty days late both reduce what is left, but only one of them
 * has left the bank, and confusing the two is how an event appears profitable while
 * owing everybody money.
 */
public final class RoiCalculator implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Revenue below this, and no return figure is worth quoting. */
    private static final BigDecimal IMMATERIAL = BigDecimal.valueOf(0.01);

    private final BigDecimal ticketRevenue;
    private final BigDecimal otherRevenue;
    private final BigDecimal cashCosts;
    private final BigDecimal accruedCosts;
    private final BigDecimal marketingSpend;
    private final BigDecimal providerFees;
    private final BigDecimal attendees;
    private final BigDecimal capacity;
    private final BigDecimal averageTicketValue;

    public RoiCalculator(BigDecimal ticketRevenue,
                         BigDecimal otherRevenue,
                         BigDecimal cashCosts,
                         BigDecimal accruedCosts,
                         BigDecimal marketingSpend,
                         BigDecimal providerFees,
                         BigDecimal attendees,
                         BigDecimal capacity,
                         BigDecimal averageTicketValue) {
        this.ticketRevenue = Money.of(ticketRevenue);
        this.otherRevenue = Money.of(otherRevenue);
        this.cashCosts = Money.of(cashCosts);
        this.accruedCosts = Money.of(accruedCosts);
        this.marketingSpend = Money.of(marketingSpend);
        this.providerFees = Money.of(providerFees);
        this.attendees = Money.of(attendees);
        this.capacity = Money.of(capacity);
        this.averageTicketValue = Money.of(averageTicketValue);
    }
    /** Sponsorship, exhibitors, grants and anything else that is not a ticket. */
    public BigDecimal getOtherRevenue() {
        return otherRevenue;
    }
    public BigDecimal getMarketingSpend() {
        return marketingSpend;
    }

    /** What card processors and transfer services kept. */
    public BigDecimal getProviderFees() {
        return providerFees;
    }
    public BigDecimal getCapacity() {
        return capacity;
    }

    public BigDecimal getAverageTicketValue() {
        return averageTicketValue;
    }

    /** Ticket money less what the processors kept. This is the honest takings figure. */
    public BigDecimal getNetTicketRevenue() {
        return Money.subtract(ticketRevenue, providerFees);
    }

    /** Everything received, whatever it was for. */
    public BigDecimal getTotalRevenue() {
        return Money.sum(getNetTicketRevenue(), otherRevenue);
    }

    /**
     * Everything that has to be paid, whether or not it has been.
     *
     * <p>Accrued costs are included because leaving them out flatters every result.
     * A supplier invoice due next month is still a cost of this event.
     */
    public BigDecimal getTotalCosts() {
        return Money.sum(cashCosts, accruedCosts);
    }

    /**
     * What is left once everything is paid.
     *
     * <p>Marketing is already inside the cash costs, because campaign spend is
     * money out like any other; it is broken out separately only for reporting, and
     * is not deducted twice.
     */
    public BigDecimal getProfit() {
        return Money.subtract(getTotalRevenue(), getTotalCosts());
    }
    /**
     * Return per pound spent, tickets only.
     *
     * <p>Ticket revenue net of fees, over total costs. This is the strictest figure
     * and the one to quote when somebody asks whether the event paid for itself.
     */
    public double getTicketReturnMultiple() {
        return multiple(getNetTicketRevenue(), getTotalCosts());
    }

    /** Return per pound spent, including sponsorship and exhibitors. */
    public double getTotalReturnMultiple() {
        return multiple(getTotalRevenue(), getTotalCosts());
    }

    private static double multiple(BigDecimal returned, BigDecimal spent) {
        if (spent.signum() <= 0) {
            return returned.signum() > 0 ? Double.MAX_VALUE : 0;
        }
        return returned.doubleValue() / spent.doubleValue();
    }
    /** What each attendee cost the event to stage. */
    public BigDecimal getCostPerAttendee() {
        if (attendees.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.of(getTotalCosts().divide(attendees, 2, RoundingMode.HALF_UP));
    }

    /** What each attendee contributed. */
    public BigDecimal getRevenuePerAttendee() {
        if (attendees.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.of(getTotalRevenue().divide(attendees, 2, RoundingMode.HALF_UP));
    }

    /**
     * How many people had to come for the event to break even.
     *
     * <p>Total costs divided by what each attendee brings in, rounded up. The single
     * most useful planning figure there is: it is the number to check the ticket
     * price against, and it is only knowable once the costs are known.
     */
    public int getBreakEvenAttendees() {
        BigDecimal perHead = getRevenuePerAttendee();
        if (perHead.signum() <= 0) {
            return 0;
        }
        return getTotalCosts().divide(perHead, 0, RoundingMode.CEILING).intValue();
    }

    /**
     * Whether the event made money, allowing for a small margin.
     *
     * <p>Judged on profit rather than on the percentage, because a one-pound surplus
     * is not a return worth announcing and should not turn the indicator green.
     */
    public boolean isProfitable() {
        return getProfit().compareTo(IMMATERIAL) > 0;
    }

    /** Whether the event lost money, allowing for the same small margin. */
    public boolean madeLoss() {
        return getProfit().compareTo(Money.negate(IMMATERIAL)) < 0;
    }
    /** How full the room was, as a percentage of capacity. */
    public BigDecimal getOccupancyPercent() {
        if (capacity.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.sharePercent(attendees, capacity);
    }
    /** What the return figure should be labelled, in one word. */
    public String getReturnVerdict() {
        if (getTotalCosts().signum() <= 0) {
            return "No costs";
        }
        double multiple = getTotalReturnMultiple();
        if (multiple >= 2.0) {
            return "Strong";
        }
        if (multiple >= 1.2) {
            return "Healthy";
        }
        if (multiple >= 1.0) {
            return "Break even";
        }
        if (multiple >= 0.8) {
            return "Short";
        }
        return "Loss";
    }

    /** A one-line summary for the finance screen. */
    public String describe() {
        return String.format(Locale.UK,
                "%s: %s returned on %s spent (%s per attendee, break-even at %d).",
                getReturnVerdict(), Money.format(getTotalRevenue()), Money.format(getTotalCosts()),
                getTicketReturnMultiple() == Double.MAX_VALUE ? "everything" : "each pound returned "
                        + String.format(Locale.UK, "%.2f", getTicketReturnMultiple()),
                getBreakEvenAttendees());
    }

    @Override
    public String toString() {
        return describe();
    }
}
