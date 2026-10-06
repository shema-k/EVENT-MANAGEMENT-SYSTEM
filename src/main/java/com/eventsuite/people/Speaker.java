package com.eventsuite.people;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Somebody on the programme: a talk, a panel, a performance or a workshop.
 *
 * <p>A speaker belongs to one event but is one person. The record holds who they
 * are, what they are being paid, when they are on, and how they are getting here.
 * The last of these is the field that gets left out of other systems and then
 * remembered the night before: a speaker booked on a flight at 06:00 that lands
 * after the session starts is a real and avoidable failure.
 *
 * <p>Fees are held even for unconfirmed speakers, because a quote is needed to
 * budget the event before anyone signs anything. Whether a fee counts towards
 * actual spend is decided by {@link SpeakerStatus}, not by this record.
 */
public final class Speaker implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String eventId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String organisation;
    private final String jobTitle;
    private final String biography;
    private final String sessionTitle;
    private final String sessionAbstract;
    private final String roomName;
    private final LocalDateTime sessionStart;
    private final LocalDateTime sessionEnd;
    private final BigDecimal feeAgreed;
    private final BigDecimal travelBudget;
    private final String travelNotes;
    private final String dietaryNotes;
    private final String accessibilityNotes;
    private final boolean needsAccommodation;
    private final String accommodationNotes;
    private final String photoPath;

    private SpeakerStatus status;

    public Speaker(String id,
                   String eventId,
                   String firstName,
                   String lastName,
                   String email,
                   String phone,
                   String organisation,
                   String jobTitle,
                   String biography,
                   String sessionTitle,
                   String sessionAbstract,
                   String roomName,
                   LocalDateTime sessionStart,
                   LocalDateTime sessionEnd,
                   BigDecimal feeAgreed,
                   BigDecimal travelBudget,
                   String travelNotes,
                   String dietaryNotes,
                   String accessibilityNotes,
                   boolean needsAccommodation,
                   String accommodationNotes,
                   String photoPath,
                   SpeakerStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.firstName = Objects.requireNonNull(firstName, "firstName").trim();
        this.lastName = lastName == null ? "" : lastName.trim();
        this.email = email == null ? "" : email.trim();
        this.phone = phone == null ? "" : phone.trim();
        this.organisation = organisation == null ? "" : organisation.trim();
        this.jobTitle = jobTitle == null ? "" : jobTitle.trim();
        this.biography = biography == null ? "" : biography.trim();
        this.sessionTitle = sessionTitle == null ? "" : sessionTitle.trim();
        this.sessionAbstract = sessionAbstract == null ? "" : sessionAbstract.trim();
        this.roomName = roomName == null ? "" : roomName.trim();
        this.sessionStart = sessionStart;
        this.sessionEnd = sessionEnd;
        this.feeAgreed = Money.of(feeAgreed);
        this.travelBudget = Money.of(travelBudget);
        this.travelNotes = travelNotes == null ? "" : travelNotes.trim();
        this.dietaryNotes = dietaryNotes == null ? "" : dietaryNotes.trim();
        this.accessibilityNotes = accessibilityNotes == null ? "" : accessibilityNotes.trim();
        this.needsAccommodation = needsAccommodation;
        this.accommodationNotes = accommodationNotes == null ? "" : accommodationNotes.trim();
        this.photoPath = photoPath == null ? "" : photoPath.trim();
        this.status = status == null ? SpeakerStatus.PROSPECT : status;
        if (sessionStart != null && sessionEnd != null && sessionEnd.isBefore(sessionStart)) {
            throw new IllegalArgumentException(
                    "Session for " + getFullName() + " ends before it starts");
        }
    }
    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getOrganisation() {
        return organisation;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    /** The introduction printed in the programme. */
    public String getBiography() {
        return biography;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public String getSessionAbstract() {
        return sessionAbstract;
    }

    /** Where on the programme they appear. */
    public String getRoomName() {
        return roomName;
    }

    public LocalDateTime getSessionStart() {
        return sessionStart;
    }

    public LocalDateTime getSessionEnd() {
        return sessionEnd;
    }

    /** What they are paid, agreed or quoted. */
    public BigDecimal getFeeAgreed() {
        return feeAgreed;
    }

    /** What is set aside for getting them here. */
    public BigDecimal getTravelBudget() {
        return travelBudget;
    }

    /** Flights, times, who is picking them up. The field that saves the night. */
    public String getTravelNotes() {
        return travelNotes;
    }

    public String getDietaryNotes() {
        return dietaryNotes;
    }

    public String getAccessibilityNotes() {
        return accessibilityNotes;
    }

    public boolean needsAccommodation() {
        return needsAccommodation;
    }

    public String getAccommodationNotes() {
        return accommodationNotes;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public SpeakerStatus getStatus() {
        return status;
    }

    public String getFullName() {
        return getLastName().isEmpty()
                ? getFirstName()
                : getFirstName() + " " + getLastName();
    }
    /** Fee plus travel: what this speaker costs the event in total. */
    public BigDecimal getTotalCost() {
        return Money.sum(feeAgreed, travelBudget);
    }

    /** Whether this speaker has a session in the programme. */
    public boolean hasSession() {
        return sessionStart != null;
    }

    /** Whether the session has a room assigned. */
    public boolean hasRoom() {
        return !roomName.isBlank();
    }
    /**
     * Whether this speaker needs a hotel the night before.
     *
     * <p>Someone whose session starts before noon almost certainly does, since a
     * flight arriving at 08:00 leaves no margin. This drives the accommodation
     * checklist, which is otherwise discovered to be empty on the morning.
     */
    public boolean needsHotelTheNightBefore() {
        if (!needsAccommodation) {
            return false;
        }
        return sessionStart == null || sessionStart.getHour() < 12;
    }

    /** Whether travel details are missing on somebody who has to travel. */
    public boolean needsTravelArranging() {
        return status.isConfirmed() && Money.isPositive(travelBudget) && travelNotes.isBlank();
    }

    /** A copy at a different status. */
    public Speaker withStatus(SpeakerStatus newStatus) {
        return new Speaker(id, eventId, firstName, lastName, email, phone, organisation, jobTitle,
                biography, sessionTitle, sessionAbstract, roomName, sessionStart, sessionEnd,
                feeAgreed, travelBudget, travelNotes, dietaryNotes, accessibilityNotes,
                needsAccommodation, accommodationNotes, photoPath, newStatus);
    }
    /**
     * Moves to a new booking state.
     *
     * @throws IllegalStateException when the transition is not allowed
     */
    public void moveTo(SpeakerStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Speaker " + getFullName() + " cannot go from "
                    + status.getLabel() + " to " + (next == null ? "nothing" : next.getLabel()));
        }
        status = next;
    }

    /** Restores a state read back from the database. */
    public void restoreStatus(SpeakerStatus restored) {
        this.status = restored == null ? SpeakerStatus.PROSPECT : restored;
    }

    /** Whether this speaker matches a typed search. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return getFullName().toLowerCase(Locale.ROOT).contains(needle)
                || organisation.toLowerCase(Locale.ROOT).contains(needle)
                || sessionTitle.toLowerCase(Locale.ROOT).contains(needle)
                || email.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** A line for the speaker list. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(getFullName());
        line.append("  ").append(status.getLabel());
        if (!sessionTitle.isEmpty()) {
            line.append("  -  ").append(sessionTitle);
        }
        if (hasRoom()) {
            line.append("  (").append(roomName).append(")");
        }
        return line.toString();
    }
    @Override
    public String toString() {
        return getDisplayLine();
    }
}
