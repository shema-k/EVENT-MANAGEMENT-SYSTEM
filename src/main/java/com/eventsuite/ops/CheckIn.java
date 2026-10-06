package com.eventsuite.ops;

import com.eventsuite.ticketing.AccessTier;
import com.eventsuite.ticketing.Ticket;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One scan at one door, whether it let somebody in or not.
 *
 * <p>This is the live record. Every scan is kept, so the attendance curve can be
 * built from when people actually turned up rather than from ticket sales, and so
 * a refused entry stays visible for someone to fix. Rejected scans are not
 * failures of the record; they are the record working.
 *
 * <p>The outcome is decided by the rule that runs at the door, not by the operator.
 * An operator can override a refusal, which is recorded separately as a manual
 * entry, because a person waving in a speaker whose ticket has not printed yet is
 * legitimate and must not require inventing a false outcome.
 */
public final class CheckIn implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.UK);

    private final String id;
    private final String eventId;
    private final String ticketReference;
    private final String holderName;
    private final AccessTier tier;
    private final String gateId;
    private final String gateName;
    private final String operatorName;
    private final Instant at;
    private final boolean manualOverride;

    private CheckInOutcome outcome;
    private String note;

    public CheckIn(String id,
                   String eventId,
                   String ticketReference,
                   String holderName,
                   AccessTier tier,
                   String gateId,
                   String gateName,
                   String operatorName,
                   Instant at,
                   CheckInOutcome outcome,
                   boolean manualOverride,
                   String note) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.ticketReference = ticketReference == null ? "" : ticketReference;
        this.holderName = Objects.requireNonNull(holderName, "holderName");
        this.tier = tier == null ? AccessTier.GENERAL : tier;
        this.gateId = gateId == null ? "" : gateId;
        this.gateName = gateName == null || gateName.isBlank() ? "Main entrance" : gateName;
        this.operatorName = operatorName == null ? "" : operatorName;
        this.at = Objects.requireNonNull(at, "at");
        this.outcome = outcome == null ? CheckInOutcome.INVALID : outcome;
        this.manualOverride = manualOverride;
        this.note = note == null ? "" : note;
    }

    /**
     * Decides what happens when this ticket meets this door.
     *
     * <p>The order of the checks is the logic. Ticket validity comes first, because
     * there is no point reasoning about access with a ticket that was refunded. Then
     * a duplicate, since that is a second arrival with the same pass. Then capacity,
     * because turning somebody away from a full event is a different decision from
     * turning them away from a locked door. Access is checked last of all, so that a
     * VIP at the correct door is never reported as being at the wrong one.
     */
    public static CheckInOutcome decide(Ticket ticket, AccessGate gate, boolean eventAtCapacity) {
        if (ticket == null) {
            return CheckInOutcome.NO_TICKET;
        }
        if (ticket.getStatus() == com.eventsuite.ticketing.TicketStatus.REFUNDED
                || ticket.getStatus() == com.eventsuite.ticketing.TicketStatus.VOID) {
            return CheckInOutcome.INVALID;
        }
        if (ticket.isCheckedIn()) {
            return CheckInOutcome.DUPLICATE;
        }
        if (ticket.getStatus() == com.eventsuite.ticketing.TicketStatus.RESERVED) {
            return CheckInOutcome.NOT_YET_VALID;
        }
        if (eventAtCapacity) {
            return CheckInOutcome.AT_CAPACITY;
        }
        if (gate == null || !gate.admits(ticket.getAccessTier(), true)) {
            if (!gate.isOpen()) {
                return CheckInOutcome.WRONG_DOOR;
            }
            return gate.getRequiredTier() != com.eventsuite.ticketing.AccessTier.GENERAL
                    ? CheckInOutcome.WRONG_DOOR : CheckInOutcome.INVALID;
        }
        return CheckInOutcome.ADMITTED;
    }

    /** A scan recorded from a real ticket at a real gate. */
    public static CheckIn of(String id, String eventId, Ticket ticket, AccessGate gate,
                             String operatorName, Instant at) {
        CheckInOutcome outcome = decide(ticket, gate, false);
        return new CheckIn(id, eventId, ticket.getReference(), ticket.getAttendeeName(),
                ticket.getAccessTier(), gate == null ? "" : gate.getId(),
                gate == null ? "Main entrance" : gate.getName(), operatorName, at, outcome,
                false, "");
    }

    /** An operator waving somebody in, against a ticket that would not scan. */
    public static CheckIn override(String id, String eventId, String holderName, String reason,
                                   String operatorName, AccessGate gate, Instant at) {
        return new CheckIn(id, eventId, "", holderName, AccessTier.STAFF,
                gate == null ? "" : gate.getId(),
                gate == null ? "Main entrance" : gate.getName(), operatorName, at,
                CheckInOutcome.ADMITTED, true, reason);
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    /** The ticket that was scanned, or empty for a manual entry. */
    public String getTicketReference() {
        return ticketReference;
    }

    public String getHolderName() {
        return holderName;
    }

    public AccessTier getTier() {
        return tier;
    }

    public String getGateId() {
        return gateId;
    }

    public String getGateName() {
        return gateName;
    }

    /** Who was operating the desk. */
    public String getOperatorName() {
        return operatorName;
    }

    public Instant getAt() {
        return at;
    }

    public CheckInOutcome getOutcome() {
        return outcome;
    }

    /**
     * Whether an operator let this person in against the rule.
     *
     * <p>Worth counting separately: a door that needs overriding for a fifth of its
     * visitors has a problem with its ticket list, not with its staff.
     */
    public boolean isManualOverride() {
        return manualOverride;
    }

    public String getNote() {
        return note;
    }

    public boolean isAdmitted() {
        return outcome.isAdmitted();
    }

    /** Whether this scan adds to the headcount. */
    public boolean countsAsAttendance() {
        return outcome.countsAsAttendance();
    }
    /** The arrival time in local time. */
    public LocalDateTime getLocalTime() {
        return LocalDateTime.ofInstant(at, ZoneId.systemDefault());
    }

    /** The arrival time as HH:mm:ss, for the live arrival feed. */
    public String getTimeLabel() {
        return WHEN.format(getLocalTime());
    }
    /** A line for the live arrivals feed. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(getTimeLabel());
        line.append("  ").append(getHolderName());
        line.append("  (").append(getTier().getLabel()).append(")");
        line.append("  @").append(getGateName());
        if (!isAdmitted()) {
            line.append("  - ").append(outcome.getLabel());
        } else if (isManualOverride()) {
            line.append("  - manual");
        }
        return line.toString();
    }

    @Override
    public String toString() {
        return getDisplayLine();
    }
}
