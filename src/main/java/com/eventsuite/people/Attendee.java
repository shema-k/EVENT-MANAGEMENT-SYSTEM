package com.eventsuite.people;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * A person who comes to events, held once and reused.
 *
 * <p>An attendee is not the same thing as a registration. Somebody who has been to
 * four of your conferences is one attendee with four registrations, and keeping them
 * apart is what makes repeat attendance, lifetime value and per-person engagement
 * history answerable at all. Folding them together would make a returning attendee
 * look like a new one every time.
 *
 * <p>Contact details are validated on the way in. An email address that cannot
 * receive a ticket is worth catching at the desk rather than in the inbox.
 */
public final class Attendee implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String organisation;
    private final String jobTitle;
    private final String dietaryNotes;
    private final String accessibilityNotes;
    private final boolean consentMarketing;

    public Attendee(String id,
                    String firstName,
                    String lastName,
                    String email,
                    String phone,
                    String organisation,
                    String jobTitle,
                    String dietaryNotes,
                    String accessibilityNotes,
                    boolean consentMarketing) {
        this.id = Objects.requireNonNull(id, "id");
        this.firstName = Objects.requireNonNull(firstName, "firstName").trim();
        this.lastName = lastName == null ? "" : lastName.trim();
        this.email = normaliseEmail(email);
        this.phone = phone == null ? "" : phone.trim();
        this.organisation = organisation == null ? "" : organisation.trim();
        this.jobTitle = jobTitle == null ? "" : jobTitle.trim();
        this.dietaryNotes = dietaryNotes == null ? "" : dietaryNotes.trim();
        // Accessibility notes are handled more carefully than dietary ones. They can
        // describe a medical or personal condition, so they are never shown on a
        // shared attendee list; only the door list reads them.
        this.accessibilityNotes = accessibilityNotes == null ? "" : accessibilityNotes.trim();
        this.consentMarketing = consentMarketing;
    }

    /**
     * Cleans an email address into something deliverable.
     *
     * <p>Throws when it cannot be one. A registration with no usable address means
     * a ticket that never arrives, and that is caught at the desk instead.
     */
    private static String normaliseEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("An attendee needs an email address");
        }
        String cleaned = email.trim().toLowerCase(Locale.ROOT);
        int at = cleaned.indexOf('@');
        // indexOf returns -1 when there is no second '@', so the check is that it found
        // nothing. Testing it against `at` instead would reject every well-formed
        // address, since a single '@' never matches its own index.
        boolean singleAt = cleaned.indexOf('@', at + 1) == -1;
        boolean shapedRight = at > 0 && at < cleaned.length() - 1
                && singleAt
                && !cleaned.contains(" ")
                && cleaned.substring(at + 1).contains(".");
        if (!shapedRight) {
            throw new IllegalArgumentException("Not a usable email address: " + email);
        }
        return cleaned;
    }

    public String getId() {
        return id;
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

    /** Catering requirements: vegetarian, halal, nut allergy and so on. */
    public String getDietaryNotes() {
        return dietaryNotes;
    }

    /** Access requirements, shown only on the door list. */
    public String getAccessibilityNotes() {
        return accessibilityNotes;
    }

    /** Whether this person agreed to be sent marketing. */
    public boolean hasMarketingConsent() {
        return consentMarketing;
    }

    public String getFullName() {
        return getLastName().isEmpty()
                ? getFirstName()
                : getFirstName() + " " + getLastName();
    }

    /** A copy with a different marketing consent. */
    public Attendee withConsent(boolean granted) {
        return new Attendee(id, firstName, lastName, email, phone, organisation, jobTitle,
                dietaryNotes, accessibilityNotes, granted);
    }

    /** A copy with updated contact details. */
    public Attendee withContact(String newEmail, String newPhone) {
        return new Attendee(id, firstName, lastName, newEmail, newPhone, organisation,
                jobTitle, dietaryNotes, accessibilityNotes, consentMarketing);
    }

    /** Whether this person's record matches a free-text search. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return getFullName().toLowerCase(Locale.ROOT).contains(needle)
                || email.contains(needle)
                || organisation.toLowerCase(Locale.ROOT).contains(needle)
                || jobTitle.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** The name and any catering note, for the door list. */
    public String getDoorLine() {
        return dietaryNotes.isEmpty() ? getFullName()
                : getFullName() + "  " + dietaryNotes;
    }

    @Override
    public String toString() {
        return getFullName() + " <" + email + ">";
    }
}
