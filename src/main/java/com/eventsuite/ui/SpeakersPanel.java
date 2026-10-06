package com.eventsuite.ui;

import com.eventsuite.core.Event;
import com.eventsuite.people.ResourceBooking;
import com.eventsuite.people.ResourceItem;
import com.eventsuite.people.Speaker;
import com.eventsuite.people.SpeakerStatus;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.AccessTier;
import com.eventsuite.ticketing.TicketType;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;

/**
 * The programme and the kit: who is speaking, and what has been booked for them.
 *
 * <p>Two things on one screen because they are booked against each other. A speaker
 * arriving on a 06:40 flight needs a room, a microphone, a chair and somewhere to wait,
 * and each of those is a separate record somewhere else. Keeping the two together is
 * what makes the last-minute question — "has everything for the 09:00 keynote actually
 * been booked" — answerable in one place.
 *
 * <p>Speaker status is the thing being managed. Knowing that half a lineup is still at
 * "approached" three weeks out is the single most useful thing on this screen, and it
 * is a separate column for that reason rather than being implied by the presence of a
 * row.
 */
public final class SpeakersPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private JPanel tileArea;
    private JPanel speakerArea;
    private JPanel resourceArea;
    private JTable bookingTable;
    private TableData bookingData;

    public SpeakersPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Speakers and kit",
                    "Choose an event in the header to manage its programme.",
                    Ui.emptyState("No event selected.",
                            "Speakers and kit are booked against an event."));
            return;
        }

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);
        speakerArea = new JPanel();
        speakerArea.setOpaque(false);
        speakerArea.setLayout(new BoxLayout(speakerArea, BoxLayout.Y_AXIS));
        resourceArea = new JPanel();
        resourceArea.setOpaque(false);
        resourceArea.setLayout(new BoxLayout(resourceArea, BoxLayout.Y_AXIS));
        bookingData = TableData.with("Booked for", "Kit", "How many", "When", "Cost",
                "Returned").withMoney(4).withStatus(5);
        bookingTable = tableOf(bookingData);

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Add a speaker", this::showSpeakerForm),
                        Ui.button("Add kit", this::showResourceForm),
                        Ui.button("Book kit for this event", this::showBookingForm),
                        Ui.button("Refresh", this::reload)),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                Ui.describedCard("Programme", "Everyone booked, and what is still outstanding.",
                        speakerArea),
                Ui.gap(14),
                Ui.describedCard("Kit for this event",
                        "What has been reserved, and whether it has come back.", resourceArea),
                Ui.gap(14),
                Ui.describedCard("Bookings", "Every reservation against an item.",
                        Ui.scroll(bookingTable)));
        screen("Speakers and kit", event.getName() + "  \u00b7  " + event.getDateRange(),
                body);
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
                            fillSpeakers(event);
                            fillResources(event);
                            fillBookings(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the programme", failure.getMessage());
        }
    }

    private void fillTiles(Event event) throws SQLException {
        DashboardPanel.clear(tileArea);
        List<Speaker> speakers = suite().people().findSpeakers(event.getId());
        long confirmed = speakers.stream().filter(s -> s.getStatus().isConfirmed()).count();
        long contracted = speakers.stream()
                .filter(speaker -> speaker.getStatus() == SpeakerStatus.CONTRACTED).count();
        long unconfirmed = speakers.size() - confirmed - speakers.stream()
                .filter(speaker -> speaker.getStatus() == SpeakerStatus.WITHDRAWN).count();
        BigDecimal fees = suite().people().sumSpeakerFees(event.getId());
        BigDecimal travel = suite().people().sumSpeakerTravelBudget(event.getId());
        List<Speaker> withoutSlot = suite().people().findConfirmedWithoutSession(event.getId());

        tileArea.add(Ui.tile("On the programme",
                String.valueOf(confirmed + contracted), "of " + speakers.size() + " booked",
                healthColour(unconfirmed > 0)));
        tileArea.add(Ui.tile("Still to confirm",
                String.valueOf(Math.max(0, unconfirmed)),
                unconfirmed == 0 ? "Lineup is complete" : "Chase them this week",
                healthColour(unconfirmed > 0)));
        tileArea.add(Ui.tile("Fees",
                com.eventsuite.finance.Money.format(fees),
                "confirmed and contracted"));
        tileArea.add(Ui.tile("Travel set aside",
                com.eventsuite.finance.Money.format(travel),
                withoutSlot.isEmpty() ? "Every slot has a room"
                        : withoutSlot.size() + " speaker(s) have no slot",
                healthColour(!withoutSlot.isEmpty())));
    }

    /**
     * The programme list.
     *
     * <p>Each entry shows the things that are separately missing — a slot, a room, a
     * session title, travel details — as explicit warnings, because each is a separate
     * thing to have forgotten and "incomplete" would not say which.
     */
    private void fillSpeakers(Event event) throws SQLException {
        DashboardPanel.clear(speakerArea);
        List<Speaker> speakers = suite().people().findSpeakers(event.getId());
        if (speakers.isEmpty()) {
            speakerArea.add(Ui.emptyState("Nobody booked yet.",
                    "A conference without speakers is a room with chairs in it."
                            + " Use Add a speaker to start the lineup."));
            return;
        }
        for (Speaker speaker : speakers) {
            speakerArea.add(speakerRow(speaker));
            speakerArea.add(Box.createVerticalStrut(8));
        }
    }

    private JComponent speakerRow(Speaker speaker) {
        JPanel row = new JPanel(new BorderLayout(14, 6));
        row.setBackground(Theme.current().field());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 150));

        JPanel facts = new JPanel();
        facts.setOpaque(false);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));

        JPanel title = Ui.row(Ui.bodyBold(speaker.getFullName()),
                Ui.status(speaker.getStatus().getLabel()));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(title);
        facts.add(Box.createVerticalStrut(5));

        String detail = speaker.getSessionTitle().isEmpty() ? "No session title yet"
                : speaker.getSessionTitle();
        JLabel session = Ui.body(detail);
        session.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(session);
        facts.add(Box.createVerticalStrut(5));

        List<String> gaps = gapsIn(speaker);
        String where = speaker.hasSession() && speaker.hasRoom()
                ? speaker.getSessionStart().toLocalTime().toString() + "  \u00b7  "
                        + speaker.getRoomName()
                : speaker.hasSession() ? "Slot " + speaker.getSessionStart()
                        .toLocalTime().toString() + ", no room yet" : "No slot in the programme";
        JLabel when = Ui.muted(where + "  \u00b7  "
                + com.eventsuite.finance.Money.format(speaker.getTotalCost()) + " all in");
        when.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(when);

        if (!gaps.isEmpty()) {
            JLabel warning = Ui.health("Missing: " + String.join(", ", gaps), true);
            warning.setAlignmentX(Component.LEFT_ALIGNMENT);
            facts.add(Box.createVerticalStrut(4));
            facts.add(warning);
        }
        row.add(facts, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JComboBox<SpeakerStatus> status = Ui.enumCombo(SpeakerStatus.class, speaker.getStatus());
        status.setMaximumSize(new java.awt.Dimension(140, 30));
        status.setEnabled(speakerStatusCanMove(speaker));
        status.addActionListener(change -> {
            SpeakerStatus chosen = (SpeakerStatus) status.getSelectedItem();
            if (chosen != null && chosen != speaker.getStatus()) {
                advance(speaker, chosen);
            }
        });
        actions.add(status);
        actions.add(Box.createVerticalStrut(5));
        actions.add(Ui.smallButton("Issue a free ticket", () -> issueSpeakerTicket(speaker)));
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    /**
     * Whether a speaker's booking state can move at all.
     *
     * <p>A withdrawn speaker is a closed state, and offering a menu of moves that all
     * fail is worse than offering none.
     */
    private boolean speakerStatusCanMove(Speaker speaker) {
        return java.util.Arrays.stream(SpeakerStatus.values())
                .anyMatch(candidate -> speaker.getStatus().canMoveTo(candidate));
    }

    /**
     * What is separately missing for this speaker.
     *
     * <p>Each gap is named rather than rolled into a single "incomplete" flag. A
     * speaker with a fee and no travel booking is a budgeting problem; a speaker with a
     * slot and no room is a logistics problem; they need different people to fix them.
     */
    private List<String> gapsIn(Speaker speaker) {
        List<String> gaps = new ArrayList<>();
        if (speaker.getSessionTitle().isBlank()) {
            gaps.add("a session title");
        }
        if (!speaker.hasSession()) {
            gaps.add("a slot");
        }
        if (speaker.hasSession() && !speaker.hasRoom()) {
            gaps.add("a room");
        }
        if (speaker.needsTravelArranging()) {
            gaps.add("travel arrangements");
        }
        if (speaker.needsHotelTheNightBefore() && speaker.getAccommodationNotes().isBlank()) {
            gaps.add("a hotel");
        }
        return gaps;
    }

    private void advance(Speaker speaker, SpeakerStatus next) {
        try {
                            speaker.moveTo(next);
                            suite().people().updateSpeaker(speaker);
                            setSubtitle(speaker.getFullName() + " is now " + next.getLabel() + ".");
                            reload();
                            } catch (IllegalStateException notAllowed) {
                            Ui.dialog(this, "Cannot move the speaker", notAllowed.getMessage());
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not save", failure.getMessage());
        }
    }

    /**
     * Gives a speaker a free ticket.
     *
     * <p>Uses a speaker-tier ticket at no cost, so the door knows they are staff rather
     * than a paying general admission, and so the free ticket is visible in the ticket
     * list rather than being a hole where a ticket should be.
     */
    private void issueSpeakerTicket(Speaker speaker) {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        try {
                    List<TicketType> tiers = suite().events().findTicketTypes(event.getId());
                    TicketType speakerTier = null;
                    for (TicketType type : tiers) {
                        if (type.getAccessTier() == AccessTier.SPEAKER) {
                            speakerTier = type;
                            break;
                        }
                    }
                    if (speakerTier == null) {
                        speakerTier = tiers.get(0);
                    }
                    com.eventsuite.people.Attendee attendee = suite().findOrCreateAttendee(
                            speaker.getFirstName(), speaker.getLastName(),
                            speaker.getEmail().isBlank() ? (speaker.getFirstName()
                                    + "." + speaker.getLastName() + "@speakers.invalid")
                                    : speaker.getEmail(),
                            speaker.getPhone(), speaker.getOrganisation(), speaker.getJobTitle(),
                            speaker.getDietaryNotes(), speaker.getAccessibilityNotes(), false);
                    com.eventsuite.ticketing.Ticket ticket = suite().sellTicket(event, speakerTier,
                            attendee, com.eventsuite.finance.PaymentMethod.OTHER, "", "",
                            BigDecimal.ZERO);
                    setSubtitle("Ticket " + ticket.getReference() + " issued to "
                            + speaker.getFullName() + ".");
                    } catch (SQLException | IllegalArgumentException failure) {
                    Ui.dialog(this, "Could not issue the ticket", failure.getMessage());
                    }
                }

                private void fillResources(Event event) throws SQLException {
                    DashboardPanel.clear(resourceArea);
                    List<ResourceBooking> bookings = suite().people().findBookingsForEvent(event.getId());
                    if (bookings.isEmpty()) {
                    resourceArea.add(Ui.emptyState("No kit booked for this event yet.",
                            "Chairs, microphones, staging and signage are usually booked first,"
                                    + " because they run out."));
                    return;
                    }
                    for (ResourceBooking booking : bookings) {
                    resourceArea.add(bookingRow(booking));
                    resourceArea.add(Box.createVerticalStrut(7));
                    }
                }

                private JComponent bookingRow(ResourceBooking booking) {
                    JPanel row = new JPanel(new BorderLayout(12, 5));
                    row.setBackground(Theme.current().field());
                    row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.current().border()),
                        BorderFactory.createEmptyBorder(9, 12, 9, 12)));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 90));

                    JPanel facts = new JPanel();
                    facts.setOpaque(false);
                    facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
                    JLabel name = Ui.bodyBold(booking.getQuantity() + " x " + booking.getResourceName());
                    name.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(name);
                    String when = booking.getFrom() == null ? "No dates recorded"
                        : booking.getFrom().format(DateTimeFormatter.ofPattern(
                                "d MMM HH:mm", java.util.Locale.ROOT));
                    JLabel detail = Ui.muted((booking.getForPurpose().isEmpty() ? "" : booking.getForPurpose()
                        + "  \u00b7  ") + when + "  \u00b7  "
                        + com.eventsuite.finance.Money.format(booking.getAgreedCost()));
                    detail.setAlignmentX(Component.LEFT_ALIGNMENT);
                    facts.add(detail);
                    row.add(facts, BorderLayout.CENTER);

                    JPanel actions = Ui.row(Ui.status(booking.isReturned() ? "Returned"
                                : booking.isDamaged() ? "Damaged" : "Out"),
                        Ui.smallButton("Mark returned", () -> markReturned(booking, false)),
                        Ui.smallButton("Damaged", () -> markReturned(booking, true)));
                    row.add(actions, BorderLayout.EAST);
                    return row;
                }

                private void markReturned(ResourceBooking booking, boolean damaged) {
                    try {
                                            suite().returnResource(booking, damaged);
                                            setSubtitle(booking.getResourceName() + " marked "
                                                + (damaged ? "damaged" : "returned") + ".");
                                            reload();
                    } catch (SQLException failure) {
                        Ui.dialog(this, "Could not record that", failure.getMessage());
                    }
    }

    private void fillBookings(Event event) throws SQLException {
        bookingData.rowsAsList().clear();
        for (ResourceBooking booking : suite().people().findBookingsForEvent(event.getId())) {
            bookingData.add(booking.getForPurpose().isEmpty() ? "\u2014"
                    : booking.getForPurpose(), booking.getResourceName(),
                    booking.getQuantity(),
                    booking.getFrom() == null ? "\u2014"
                            : booking.getFrom().format(DateTimeFormatter.ofPattern(
                            "d MMM HH:mm", java.util.Locale.ROOT)),
                    com.eventsuite.finance.Money.format(booking.getAgreedCost()),
                    booking.isReturned() ? (booking.isDamaged() ? "Damaged" : "Yes")
                            : "No");
        }
        bookingTable.repaint();
    }

    // ---------------------------------------------------------------- forms

    private void showSpeakerForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField firstName = Ui.field("");
        JTextField lastName = Ui.field("");
        JTextField email = Ui.field("");
        JTextField organisation = Ui.field("");
        JTextField jobTitle = Ui.field("");
        JTextField sessionTitle = Ui.field("");
        JTextField sessionSummary = Ui.field("");
        JTextField room = Ui.field("");
        JTextField startsAt = Ui.field(event.getStartDate() + " 09:00");
        JTextField endsAt = Ui.field(event.getStartDate() + " 09:45");
        JTextField fee = Ui.field("0");
        JTextField travel = Ui.field("0");
        JTextField travelNotes = Ui.field("");
        JTextField hotelNotes = Ui.field("");

        JPanel form = Ui.column(
                Ui.row(Ui.titledGroup("First name", firstName),
                        Ui.titledGroup("Last name", lastName)),
                Ui.gap(8),
                Ui.titledGroup("Email", email),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Job title", jobTitle),
                        Ui.titledGroup("Organisation", organisation)),
                Ui.gap(8),
                Ui.titledGroup("Session title", sessionTitle),
                Ui.gap(8),
                Ui.titledGroup("What the session covers", sessionSummary),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Room", room),
                        Ui.titledGroup("Starts", startsAt),
                        Ui.titledGroup("Ends", endsAt)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Fee", fee), Ui.titledGroup("Travel budget", travel)),
                Ui.gap(8),
                Ui.titledGroup("How they are getting here", travelNotes),
                Ui.gap(8),
                Ui.titledGroup("Where they are staying", hotelNotes));
        if (Ui.formDialog(this, "Add a speaker to " + event.getName(), form, "Add", "Cancel")
                == null) {
            return;
        }
        if (firstName.getText().isBlank()) {
            Ui.dialog(this, "First name required", "Somebody has to be named.");
            return;
        }
        try {
                            LocalDateTime start = LocalDateTime.parse(startsAt.getText().trim()
                                    .replace(" ", "T"));
                            LocalDateTime end = LocalDateTime.parse(endsAt.getText().trim()
                                    .replace(" ", "T"));
                            suite().addSpeaker(event, firstName.getText().trim(),
                                    lastName.getText().trim(), email.getText().trim(), "",
                                    organisation.getText().trim(), jobTitle.getText().trim(),
                                    sessionTitle.getText().trim(), sessionSummary.getText().trim(),
                                    room.getText().trim(), start, end,
                                    com.eventsuite.finance.Money.of(fee.getText()),
                                    com.eventsuite.finance.Money.of(travel.getText()),
                                    travelNotes.getText().trim(), "", "", false, hotelNotes.getText().trim());
                            setSubtitle(firstName.getText().trim() + " added as a prospect.");
                            reload();
                            } catch (java.time.format.DateTimeParseException badTime) {
                            Ui.dialog(this, "Time not understood",
                                    "Use the form 2026-04-14 09:00 for the start and end.");
                            } catch (IllegalArgumentException badInput) {
                            Ui.dialog(this, "Speaker not accepted", badInput.getMessage());
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not add the speaker", failure.getMessage());
        }
    }

    private void showResourceForm() {
        JTextField name = Ui.field("");
        JComboBox<ResourceItem.Kind> kind =
                Ui.enumCombo(ResourceItem.Kind.class, ResourceItem.Kind.SEATING);
        JTextField quantity = Ui.field("100");
        JTextField unitCost = Ui.field("2.50");
        JTextField supplier = Ui.field("");
        JCheckBoxHolder hired = new JCheckBoxHolder();

        JPanel form = Ui.column(
                Ui.titledGroup("What is it", name),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Kind", kind), Ui.titledGroup("How many", quantity)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Cost each", unitCost),
                        Ui.titledGroup("Supplier", supplier)),
                Ui.gap(8),
                Ui.titledGroup("Hired rather than owned", hired.box));
        if (Ui.formDialog(this, "Add a kit item to the inventory", form, "Add", "Cancel")
                == null) {
            return;
        }
        if (name.getText().isBlank()) {
            Ui.dialog(this, "Name required", "Give the item a name somebody will recognise.");
            return;
        }
        try {
                            int count = Integer.parseInt(quantity.getText().trim());
                            ResourceItem item = suite().addResource(name.getText().trim(),
                                    (ResourceItem.Kind) kind.getSelectedItem(), count,
                                    com.eventsuite.finance.Money.of(unitCost.getText()),
                                    com.eventsuite.finance.Money.ZERO, supplier.getText().trim(), "",
                                    hired.isSelected());
                            setSubtitle(count + " x " + item.getName() + " added to the inventory.");
                            } catch (NumberFormatException notANumber) {
                            Ui.dialog(this, "Quantity not understood",
                                    "\"" + quantity.getText() + "\" is not a number.");
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not add the item", failure.getMessage());
        }
    }

    private void showBookingForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        List<ResourceItem> items;
        try {
                    items = suite().people().findAllResources();
                    } catch (SQLException failure) {
                    Ui.dialog(this, "Could not read the inventory", failure.getMessage());
                    return;
                    }
                    if (items.isEmpty()) {
                    Ui.dialog(this, "No kit in the inventory",
                            "Add an item first, then book it against this event.");
                    return;
                    }
                    JComboBox<ResourceItem> item = Ui.combo(items, items.get(0));
                    JTextField howMany = Ui.field("1");
                    JTextField purpose = Ui.field("");
                    JTextField from = Ui.field(event.getStartDate() + " 08:00");
                    JTextField until = Ui.field(event.getEndDate() + " 18:00");
                    JLabel available = Ui.muted("");
                    item.addActionListener(change -> updateAvailability(item, available));
                    updateAvailability(item, available);

                    JPanel form = Ui.column(
                        Ui.titledGroup("Item", item),
                        Ui.gap(4),
                        available,
                        Ui.gap(8),
                        Ui.row(Ui.titledGroup("How many", howMany),
                                Ui.titledGroup("For what", purpose)),
                        Ui.gap(8),
                        Ui.row(Ui.titledGroup("From", from), Ui.titledGroup("Until", until)));
                    if (Ui.formDialog(this, "Book kit for " + event.getName(), form, "Book it", "Cancel")
                        == null) {
                    return;
                    }
                    try {
                                            ResourceItem chosen = (ResourceItem) item.getSelectedItem();
                                            int count = Integer.parseInt(howMany.getText().trim());
                                            suite().bookResource(event, chosen, count,
                                                LocalDateTime.parse(from.getText().trim().replace(" ", "T")),
                                                LocalDateTime.parse(until.getText().trim().replace(" ", "T")),
                                                purpose.getText().trim(), chosen.getTotalCost());
                                            setSubtitle(count + " x " + chosen.getName() + " booked.");
                                            reload();
                                            } catch (NumberFormatException notANumber) {
                                            Ui.dialog(this, "How many not understood",
                                                "Put a whole number of items.");
                                            } catch (java.time.format.DateTimeParseException badTime) {
                                            Ui.dialog(this, "Time not understood", "Use the form 2026-04-14 08:00.");
                    } catch (SQLException failure) {
                        Ui.dialog(this, "Could not book that", failure.getMessage());
                    }
    }

    private void updateAvailability(JComboBox<ResourceItem> combo, JLabel label) {
        ResourceItem chosen = (ResourceItem) combo.getSelectedItem();
        if (chosen == null) {
            return;
        }
        int free = suite().availability(chosen);
        label.setText(free + " of " + chosen.getQuantity() + " free"
                + (chosen.getSupplier().isEmpty() ? "" : "  \u00b7  from " + chosen.getSupplier()));
        label.setForeground(free <= 0 ? Theme.current().danger() : Theme.current().muted());
    }

    /** Carries a checkbox's value in and out of a form. */
    private static final class JCheckBoxHolder {
        private final javax.swing.JCheckBox box = Ui.check("", false);

        boolean isSelected() {
            return box.isSelected();
        }
    }
}
