package com.eventsuite.ui;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.core.Event;
import com.eventsuite.people.Attendee;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.Registration;
import com.eventsuite.ticketing.RegistrationStatus;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Who is coming: the people registered, and the ones waiting for a place.
 *
 * <p>The waiting list is given its own section rather than being a row in the same
 * table, because it is a different thing. A confirmed registration is a place; a
 * waiting list entry is a hope, and the two being interleaved alphabetically makes the
 * list impossible to work through at a desk.
 *
 * <p>Repeat attendance is the figure that decides whether marketing is worth doing
 * again, so it is on screen rather than buried in a report. An organiser who can see
 * that a third of the room has been before is making a different decision from one
 * looking at a raw count.
 */
public final class AttendeesPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter SINCE =
            DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK);

    private JPanel tileArea;
    private JTable attendeeTable;
    private TableData attendeeData;
    private JTable waitingTable;
    private TableData waitingData;
    private JTextField search;

    public AttendeesPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Attendees", "Choose an event in the header to see who is coming.",
                    Ui.emptyState("No event selected.", "Registrations belong to an event."));
            return;
        }

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);
        attendeeData = TableData.with("Name", "Email", "Organisation", "Status",
                "Registered", "Owed").withStatus(3).withMoney(5);
        attendeeTable = tableOf(attendeeData);
        waitingData = TableData.with("Name", "Email", "Position", "Registered");
        waitingTable = tableOf(waitingData);

        search = Ui.searchField("Search by name, email or company");

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Add attendee", this::showAddForm),
                        search,
                        Ui.smallButton("Offer waiting places", this::promoteWaiting),
                        Ui.button("Refresh", this::reload)),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                Ui.describedCard("Registered", "Everyone with a place, however they got it.",
                        Ui.scroll(attendeeTable)),
                Ui.gap(14),
                Ui.describedCard("Waiting list",
                        "In the order they joined. A cancellation offers a place to the"
                                + " first of these.",
                        Ui.scroll(waitingTable)));
        screen("Attendees", event.getName() + "  \u00b7  " + event.getDateRange(), body);

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent change) {
                reload();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent change) {
                reload();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent change) {
                reload();
            }
        });
        reload();
    }

    @Override
    protected void reload() {
        Event event = currentEvent();
        if (event == null || tileArea == null) {
            return;
        }
        try {
                            fillTiles(event);
                            fillAttendees(event);
                            fillWaiting(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the attendee list", failure.getMessage());
        }
    }

    private void fillTiles(Event event) throws SQLException {
        EventMetrics metrics = suite().metrics().forEvent(event.getId());
        DashboardPanel.clear(tileArea);
        int registered = suite().people().countRegistrations(event.getId());
        int returning = 0;
        for (Registration registration : suite().people().findRegistrations(event.getId())) {
            if (suite().people().findRegistrationsOfAttendee(
                    registration.getAttendeeId()).size() > 1) {
                returning++;
            }
        }

        tileArea.add(Ui.tile("Registered",
                String.valueOf(metrics.getRegistrationsConfirmed()),
                registered + " registration(s) in total"));
        tileArea.add(Ui.tile("Waiting",
                String.valueOf(metrics.getRegistrationsWaitlisted()),
                metrics.getRegistrationsWaitlisted() == 0 ? "Nobody waiting"
                        : "can be offered a place",
                metrics.getRegistrationsWaitlisted() > 0 ? Theme.current().warning()
                        : Theme.current().text()));
        tileArea.add(Ui.tile("Been before",
                String.valueOf(returning),
                returning == 0 ? "All new to you"
                        : Math.round(100.0 * returning / Math.max(1, registered))
                                + "% of registrations"));
        tileArea.add(Ui.tile("Still owed",
                com.eventsuite.finance.Money.compact(sumOwed(event.getId())),
                sumOwed(event.getId()).signum() > 0 ? "Chase before the event" : "All settled",
                sumOwed(event.getId()).signum() > 0 ? Theme.current().warning()
                        : Theme.current().success()));
    }

    private java.math.BigDecimal sumOwed(String eventId) throws SQLException {
        return com.eventsuite.finance.Money.subtract(
                suite().people().sumRegistrationFees(eventId),
                suite().people().sumRegistrationFeesPaid(eventId));
    }

    private void fillAttendees(Event event) throws SQLException {
        attendeeData.rowsAsList().clear();
        String query = search == null ? "" : search.getText().trim();
        for (Registration registration : suite().people().findRegistrations(event.getId())) {
            if (!query.isEmpty()) {
                Attendee attendee = suite().people().findAttendee(registration.getAttendeeId());
                boolean nameMatches = registration.getAttendeeName()
                        .toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(
                                java.util.Locale.ROOT));
                boolean companyMatches = attendee != null && attendee.getOrganisation()
                        .toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(
                                java.util.Locale.ROOT));
                if (!nameMatches && !companyMatches) {
                    continue;
                }
            }
            attendeeData.add(registration.getAttendeeName(),
                    registration.getAttendeeEmail(),
                    organisationOf(registration),
                    registration.getStatus().getLabel(),
                    SINCE.format(registration.getRegisteredAt()
                            .atZone(ZoneId.systemDefault())),
                    registration.isOutstanding()
                            ? com.eventsuite.finance.Money.format(registration.getOutstanding())
                            : "");
        }
        attendeeTable.repaint();
    }

    private String organisationOf(Registration registration) {
        try {
                    Attendee attendee = suite().people().findAttendee(registration.getAttendeeId());
                    return attendee == null ? "" : attendee.getOrganisation();
                    } catch (SQLException unreadable) {
                    // An unreadable company is not worth losing the row over.
                    return "";
                    }
                }

                private void fillWaiting(Event event) throws SQLException {
                    waitingData.rowsAsList().clear();
                    for (Registration registration : suite().people()
                        .findRegistrationsByStatus(event.getId(), RegistrationStatus.WAITLISTED)) {
                    waitingData.add(registration.getAttendeeName(), registration.getAttendeeEmail(),
                            registration.getWaitlistPosition().isEmpty() ? "\u2014"
                                    : registration.getWaitlistPosition(),
                            SINCE.format(registration.getRegisteredAt()
                                    .atZone(ZoneId.systemDefault())));
                    }
                    waitingTable.repaint();
                }

    /**
     * The form for adding somebody to the event by hand.
     *
     * <p>Most people register by buying a ticket, but a comp, a guest, a speaker's
     * plus-one or somebody paid in cash is added here. It creates the attendee record
     * and a confirmed registration for this event, with no payment taken: taking money
     * is a separate act and this form deliberately does not pretend to do it.
     */
    private void showAddForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField firstName = Ui.field("");
        JTextField lastName = Ui.field("");
        JTextField email = Ui.field("");
        JTextField phone = Ui.field("");
        JTextField organisation = Ui.field("");
        JTextField jobTitle = Ui.field("");
        JTextField dietary = Ui.field("");
        JTextField accessibility = Ui.field("");
        JComboBox<RegistrationStatus> status =
                Ui.enumCombo(RegistrationStatus.class, RegistrationStatus.CONFIRMED);

        JPanel form = Ui.column(
                Ui.row(Ui.titledGroup("First name", firstName),
                        Ui.titledGroup("Last name", lastName)),
                Ui.gap(8),
                Ui.titledGroup("Email", email),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Phone", phone),
                        Ui.titledGroup("Organisation", organisation)),
                Ui.gap(8),
                Ui.titledGroup("Job title", jobTitle),
                Ui.gap(8),
                Ui.titledGroup("Dietary requirements", dietary),
                Ui.gap(8),
                Ui.titledGroup("Access requirements", accessibility),
                Ui.gap(12),
                Ui.titledGroup("Their place", status));

        if (Ui.formDialog(this, "Add an attendee to " + event.getName(), form,
                "Add them", "Cancel") == null) {
            return;
        }
        addAttendee(event, firstName.getText(), lastName.getText(), email.getText(),
                phone.getText(), organisation.getText(), jobTitle.getText(),
                dietary.getText(), accessibility.getText(),
                (RegistrationStatus) status.getSelectedItem());
    }

    /**
     * Creates the attendee and a registration for them, then refreshes the list.
     *
     * <p>Rejected with a named message for the thing that was wrong, because a form
     * that fails with "something went wrong" does not tell anybody which field to look
     * at.
     */
    private void addAttendee(Event event, String firstName, String lastName, String email,
                             String phone, String organisation, String jobTitle,
                             String dietary, String accessibility,
                             RegistrationStatus status) {
        if (firstName == null || firstName.isBlank()) {
            Ui.dialog(this, "First name required", "Somebody has to be named.");
            return;
        }
        if (email == null || email.isBlank()) {
            Ui.dialog(this, "Email required",
                    "An attendee needs an email address, even if it is only to confirm"
                            + " their place.");
            return;
        }
        guard("Could not add the attendee", () -> {
            Attendee attendee = suite().findOrCreateAttendee(firstName.trim(),
                    lastName == null ? "" : lastName.trim(), email.trim(), phone,
                    organisation, jobTitle, dietary, accessibility, false);
            suite().people().insertRegistration(Registration.create(
                    com.eventsuite.data.Ids.registration(), event.getId(), attendee.getId(),
                    attendee.getFullName(), attendee.getEmail(),
                    com.eventsuite.finance.Money.ZERO, java.time.Instant.now())
                    .withStatus(status == null ? RegistrationStatus.CONFIRMED : status));
            setSubtitle("Added " + attendee.getFullName() + " as "
                    + (status == null ? RegistrationStatus.CONFIRMED : status)
                    .getLabel().toLowerCase(java.util.Locale.ROOT) + ".");
            reload();
        });
    }

    /**
     * Moves people off the waiting list into the places freed up.
     *
     * <p>Only fills the places that exist. It does not take payment, because offering
     * a place and collecting the money are separate acts and only a person should do
     * the second.
     */
    private void promoteWaiting() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        try {
                            int promoted = suite().promoteFromWaitingList(event);
                            setSubtitle(promoted == 0 ? "No free places to offer."
                                    : promoted + " place(s) offered.");
                            reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not offer places", failure.getMessage());
        }
    }
}
