package com.eventsuite.ticketing;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * Somebody's place at one event, and what they owe for it.
 *
 * <p>The fee is held here rather than being derived from the ticket, because the
 * two genuinely differ. A bundle pays one fee for a ticket and a workshop, a
 * sponsor's guest pays nothing at all, and a discount code changes what was
 * charged without changing the published price. The ticket records what happened;
 * this records what was owed and how much of it has been settled.
 *
 * <p>Money taken against a registration is {@code feePaid}, and the difference
 * between the fee and the amount paid is {@link #getOutstanding()}. That single
 * figure is what the finance screen reconciles against the bank.
 */
public final class Registration implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String eventId;
    private final String attendeeId;
    private final String attendeeName;
    private final String attendeeEmail;
    private final String ticketReference;
    private final String referralSource;
    private final BigDecimal fee;
    private final Instant registeredAt;

    /** What has been settled against the fee. Not final: it grows as money arrives. */
    private BigDecimal feePaid;

    private RegistrationStatus status;
    private String waitlistPosition;

    public Registration(String id,
                        String eventId,
                        String attendeeId,
                        String attendeeName,
                        String attendeeEmail,
                        String ticketReference,
                        String referralSource,
                        BigDecimal fee,
                        BigDecimal feePaid,
                        Instant registeredAt,
                        RegistrationStatus status,
                        String waitlistPosition) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.attendeeId = Objects.requireNonNull(attendeeId, "attendeeId");
        this.attendeeName = Objects.requireNonNull(attendeeName, "attendeeName");
        this.attendeeEmail = attendeeEmail == null ? "" : attendeeEmail;
        this.ticketReference = ticketReference == null ? "" : ticketReference;
        this.referralSource = referralSource == null ? "" : referralSource.trim();
        this.fee = Money.of(fee);
        this.feePaid = Money.of(feePaid);
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.status = status == null ? RegistrationStatus.PENDING : status;
        this.waitlistPosition = waitlistPosition == null ? "" : waitlistPosition.trim();
    }

    public static Registration create(String id,
                                      String eventId,
                                      String attendeeId,
                                      String attendeeName,
                                      String attendeeEmail,
                                      BigDecimal fee,
                                      Instant registeredAt) {
        return new Registration(id, eventId, attendeeId, attendeeName, attendeeEmail, "", "",
                fee, Money.ZERO, registeredAt, RegistrationStatus.PENDING, "");
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAttendeeId() {
        return attendeeId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public String getAttendeeEmail() {
        return attendeeEmail;
    }

    /** The ticket issued against this registration, or empty if none yet. */
    public String getTicketReference() {
        return ticketReference;
    }

    /** Where the registration came from, used for marketing attribution. */
    public String getReferralSource() {
        return referralSource;
    }

    /** What the place costs in total, before any discount. */
    public BigDecimal getFee() {
        return fee;
    }

    /** What has actually been settled against the fee. */
    public BigDecimal getFeePaid() {
        return feePaid;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    /** The place on the waiting list, as text so "3" and "3rd" both fit. */
    public String getWaitlistPosition() {
        return waitlistPosition;
    }

    /** What is still owed. Never negative: an overpayment is a credit, not a debt. */
    public BigDecimal getOutstanding() {
        BigDecimal due = Money.subtract(fee, feePaid);
        return due.signum() < 0 ? Money.ZERO : due;
    }

    /** Whether the fee is settled in full. */
    public boolean isPaidInFull() {
        return getOutstanding().signum() == 0;
    }

    /** Whether some money is outstanding on a registration that still holds a place. */
    public boolean isOutstanding() {
        return getOutstanding().signum() > 0 && status.holdsPlace();
    }

    /** Whether anything was paid towards the fee. */
    public boolean hasPayment() {
        return Money.isPositive(feePaid);
    }

    /** A copy pointing at a ticket that has now been issued. */
    public Registration withTicket(String reference) {
        return new Registration(id, eventId, attendeeId, attendeeName, attendeeEmail, reference,
                referralSource, fee, feePaid, registeredAt, status, waitlistPosition);
    }

    /** A copy with money settled against it. */
    public Registration withPayment(BigDecimal paid) {
        return new Registration(id, eventId, attendeeId, attendeeName, attendeeEmail,
                ticketReference, referralSource, fee, paid, registeredAt, status, waitlistPosition);
    }

    /** A copy recorded at a different lifecycle state. */
    public Registration withStatus(RegistrationStatus newStatus) {
        return new Registration(id, eventId, attendeeId, attendeeName, attendeeEmail,
                ticketReference, referralSource, fee, feePaid, registeredAt, newStatus,
                waitlistPosition);
    }
    /** A copy at a different place on the waiting list. */
    public Registration withWaitlistPosition(String position) {
        return new Registration(id, eventId, attendeeId, attendeeName, attendeeEmail,
                ticketReference, referralSource, fee, feePaid, registeredAt, status, position);
    }

    /**
     * Moves the registration to a new state.
     *
     * @throws IllegalStateException when the lifecycle does not allow the move
     */
    public void moveTo(RegistrationStatus next) {
        if (status.canMoveTo(next)) {
            status = next;
            if (next != RegistrationStatus.WAITLISTED) {
                // Leaving the queue means the position no longer means anything.
                waitlistPosition = "";
            }
        } else {
            throw new IllegalStateException(
                    "Registration for " + attendeeName + " cannot go from " + status.getLabel()
                            + " to " + (next == null ? "nothing" : next.getLabel()));
        }
    }

    /** Puts somebody at a named place on the waiting list. */
    public void waitlist(String position) {
        if (!status.canMoveTo(RegistrationStatus.WAITLISTED)) {
            throw new IllegalStateException(
                    "Registration for " + attendeeName + " cannot be waitlisted from "
                            + status.getLabel());
        }
        status = RegistrationStatus.WAITLISTED;
        waitlistPosition = position == null ? "" : position.trim();
    }

    /** Records money taken towards the fee. */
    public void recordPayment(BigDecimal amount) {
        this.feePaid = Money.sum(feePaid, amount);
    }

    /** Marks the person as having arrived. */
    public void markAttended() {
        if (status == RegistrationStatus.WALK_IN || status == RegistrationStatus.CONFIRMED) {
            status = RegistrationStatus.ATTENDED;
        }
    }
    /** The name in upper case, for matching typed names at the door. */
    public String getNameForMatching() {
        return attendeeName.toUpperCase(Locale.ROOT);
    }

    /** A line for the attendee list. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(attendeeName);
        line.append(" - ").append(status.getLabel());
        if (!ticketReference.isEmpty()) {
            line.append(" - ").append(ticketReference);
        }
        return line.toString();
    }

    /** Restores a state read back from the database. */
    public void restoreStatus(RegistrationStatus restored) {
        this.status = restored == null ? RegistrationStatus.PENDING : restored;
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
