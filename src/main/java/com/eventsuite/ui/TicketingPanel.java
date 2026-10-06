package com.eventsuite.ui;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.core.Event;
import com.eventsuite.core.EventStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.AccessTier;
import com.eventsuite.ticketing.Registration;
import com.eventsuite.ticketing.RegistrationStatus;
import com.eventsuite.ticketing.Ticket;
import com.eventsuite.ticketing.TicketStatus;
import com.eventsuite.ticketing.TicketType;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Selling: the ticket tiers, taking a payment, and the list of tickets issued.
 *
 * <p>Three things live on one screen because they are three steps of one job. A sale
 * needs a tier, the tier needs an attendee, and both need to be visible afterwards so
 * the sale can be checked. Splitting them across three screens would mean opening the
 * sold list to confirm what was just taken, which is two seconds of work repeated
 * hundreds of times a day.
 *
 * <p>The tiers show what has sold against the allocation for each, because "have we
 * sold out" is the question this screen exists to answer and a price on its own does
 * not say it.
 */
public final class TicketingPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private JPanel tierArea;
    private JPanel tileArea;
    private JTable ticketTable;
    private TableData ticketData;

    public TicketingPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Ticketing",
                    "Choose an event in the header to sell its tickets.",
                    Ui.emptyState("No event selected.",
                            "Every ticket belongs to an event, so pick one first."));
            return;
        }

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);
        tierArea = new JPanel();
        tierArea.setOpaque(false);
        tierArea.setLayout(new BoxLayout(tierArea, BoxLayout.Y_AXIS));

        ticketData = TableData.with("Reference", "Holder", "Tier", "Access",
                "Paid", "Status", "Arrived")
                .withStatus(5);
        ticketTable = tableOf(ticketData);

        JPanel ticketsCard = Ui.card("",
                Ui.titledGroup("Tickets issued",
                        Ui.scroll(ticketTable)),
                Ui.gap(10),
                Ui.row(Ui.smallButton("Refund selected", this::refundSelected),
                        Ui.muted("Refunding puts the seat back on sale and records the "
                                + "reversal in the ledger.")));

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Sell a ticket", this::showSaleForm),
                        Ui.button("Add a ticket tier", this::showTierForm),
                        Ui.button("Add a discount code", this::showDiscountForm)),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                Ui.describedCard("Ticket tiers", "What is on sale, and how much of it has gone.",
                        tierArea),
                Ui.gap(14),
                ticketsCard);
        screen("Ticketing", describe(event), body);
        reload();
    }

    private String describe(Event event) {
        return event.getCategory().getLabel() + "  \u00b7  " + event.getDateRange()
                + "  \u00b7  " + event.getScale().getLabel() + " event holding "
                + event.getCapacity();
    }

    @Override
    protected void reload() {
        Event event = currentEvent();
        if (event == null || tierArea == null) {
            return;
        }
        try {
                            fillTiles(event);
                            fillTiers(event);
                            fillTickets(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the ticketing figures", failure.getMessage());
        }
    }

    private void fillTiles(Event event) throws SQLException {
        EventMetrics metrics = suite().metrics().forEvent(event.getId());
        DashboardPanel.clear(tileArea);
        tileArea.add(Ui.tileWithBar("Sold",
                String.valueOf(metrics.getTicketsSold()),
                "of " + metrics.getSellableCapacity() + " available",
                metrics.getSellThroughPercent().intValue()));
        tileArea.add(Ui.tile("Remaining",
                String.valueOf(metrics.getTicketsRemaining()),
                metrics.isSoldOut() ? "Sold out" : "Still to sell",
                metrics.isSoldOut() ? Theme.current().success() : Theme.current().text()));
        tileArea.add(Ui.tile("Taken",
                Money.compact(metrics.getGrossRevenue()),
                "average " + Money.format(metrics.getAverageTicketValue())));
        tileArea.add(Ui.tile("Waiting list",
                String.valueOf(metrics.getRegistrationsWaitlisted()),
                metrics.getRegistrationsWaitlisted() == 0 ? "Nobody waiting"
                        : "could be offered a place",
                metrics.getRegistrationsWaitlisted() > 0 ? Theme.current().warning()
                        : Theme.current().text()));
    }

    /** One row per tier, with its sales bar and what is left. */
    private void fillTiers(Event event) throws SQLException {
        DashboardPanel.clear(tierArea);
        List<TicketType> tiers = suite().events().findTicketTypes(event.getId());
        if (tiers.isEmpty()) {
            tierArea.add(Ui.emptyState("No ticket tiers.",
                    "Use Add a ticket tier so there is something to sell."));
            return;
        }
        for (TicketType type : tiers) {
            int sold = suite().events().countSoldForType(type.getId());
            tierArea.add(tierRow(type, sold, event));
            tierArea.add(Box.createVerticalStrut(8));
        }
    }

    private JComponent tierRow(TicketType type, int sold, Event event) {
        JPanel row = new JPanel(new BorderLayout(12, 8));
        row.setBackground(Theme.current().field());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 120));

        JPanel facts = new JPanel();
        facts.setOpaque(false);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
        JPanel title = Ui.row(Ui.bodyBold(type.getName()),
                type.isFree() ? Ui.muted("Free") : Ui.money(type.getPrice()),
                Ui.status(type.getAccessTier().getLabel()));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(title);
        facts.add(Box.createVerticalStrut(7));
        int percent = type.getQuantity() <= 0 ? 100
                : (int) Math.round(100.0 * sold / type.getQuantity());
        facts.add(Ui.bar(sold + " of " + type.getQuantity() + " sold", percent,
                type.isSoldOut(sold) ? Theme.current().success() : Theme.current().accent()));
        row.add(facts, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(Ui.smallButton(type.isSoldOut(sold) ? "Sold out" : "Sell",
                () -> sell(type, event)));
        actions.setEnabled(false);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private void fillTickets(Event event) throws SQLException {
        if (ticketData == null) {
            return;
        }
        ticketData.rowsAsList().clear();
        int row = 0;
        for (Ticket ticket : suite().tickets().findByEvent(event.getId())) {
            ticketData.add(ticket.getReference(), ticket.getAttendeeName(),
                    ticket.getTierName(), ticket.getAccessTier().getLabel(),
                    Money.format(new BigDecimal(ticket.getPricePaid())),
                    ticket.getStatus().getLabel(),
                    ticket.getCheckedInLabel());
            row++;
        }
        if (ticketTable != null) {
            ticketTable.repaint();
        }
        setSubtitle(row + " ticket(s) issued");
    }

    // ---------------------------------------------------------------- selling
    /** Opens the sale form from the command palette. */
    public void newSaleFromPalette() {
        showSaleForm();
    }



    /**
     * The sale form.
     *
     * <p>Takes a name and an email rather than picking from the attendee list, because
     * most sales are to people who have never been here before. If the email is already
     * on file the existing record is reused, so a returning attendee does not become a
     * second row with a different spelling of their company.
     */
    private void showSaleForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        try {
                        List<TicketType> tiers = suite().events().findTicketTypes(event.getId());
                        if (tiers.isEmpty()) {
                            Ui.dialog(this, "No tiers to sell",
                                    "Add a ticket tier before selling anything.");
                            return;
                        }

                        JTextField firstName = Ui.field("");
                        JTextField lastName = Ui.field("");
                        JTextField email = Ui.field("");
                        JTextField phone = Ui.field("");
                        JTextField organisation = Ui.field("");
                        JTextField dietary = Ui.field("");
                        JTextField seat = Ui.field("");
                        JComboBox<TicketType> tier = Ui.combo(tiers, tiers.get(0));
                        JComboBox<com.eventsuite.finance.PaymentMethod> method = Ui.enumCombo(
                                com.eventsuite.finance.PaymentMethod.class,
                                com.eventsuite.finance.PaymentMethod.CARD);
                        JTextField code = Ui.field("");

                        JLabel price = Ui.muted("");
                        tier.addActionListener(selection -> {
                            TicketType chosen = (TicketType) tier.getSelectedItem();
                            if (chosen != null) {
                                price.setText("Price " + chosen.getPriceLabel() + ", "
                                        + Math.max(0, chosen.getQuantity() - safeSold(chosen)) + " left");
                            }
                        });
                        tier.setSelectedIndex(0);

                        JPanel form = Ui.column(
                                Ui.titledGroup("Who is it for?", Ui.row(firstName, lastName)),
                                Ui.gap(8),
                                Ui.titledGroup("Email", email),
                                Ui.gap(8),
                                Ui.row(Ui.titledGroup("Phone", phone),
                                        Ui.titledGroup("Organisation", organisation)),
                                Ui.gap(8),
                                Ui.titledGroup("Dietary requirements", dietary),
                                Ui.gap(12),
                                Ui.row(Ui.titledGroup("Ticket", tier), price),
                                Ui.gap(8),
                                Ui.row(Ui.titledGroup("Paid by", method),
                                        Ui.titledGroup("Discount code", code)),
                                Ui.gap(8),
                                Ui.titledGroup("Seat or table (if seated)", seat));

                        if (Ui.formDialog(this, "Sell a ticket for " + event.getName(), form,
                                "Take payment", "Cancel") == null) {
                            return;
                        }
                        sell((TicketType) tier.getSelectedItem(), firstName.getText(),
                                lastName.getText(), email.getText(), phone.getText(),
                                organisation.getText(), dietary.getText(), seat.getText(),
                                (com.eventsuite.finance.PaymentMethod) method.getSelectedItem(),
                                code.getText(), event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not read the tiers", failure.getMessage());
        }
    }


    private int safeSold(TicketType type) {
        try {
            return suite().events().countSoldForType(type.getId());
        } catch (SQLException unreadable) {
            return 0;
        }
    }

    /**
     * Sells from the tier row, using whoever the name resolves to.
     *
     * <p>Finds a confirmed registration and sells the selected tier to them. If nobody
     * has registered yet the form is opened instead, since there is nobody to sell to.
     */
    private void sell(TicketType type, Event event) {
        try {
                        List<Registration> registrations = suite().people()
                                .findRegistrationsByStatus(event.getId(), RegistrationStatus.CONFIRMED);
                        if (registrations.isEmpty()) {
                            showSaleForm();
                            return;
                        }
                        List<String> names = new ArrayList<>();
                        for (Registration registration : registrations) {
                            names.add(registration.getAttendeeName());
                        }
                        String chosen = Ui.choose(this, "Sell " + type.getName() + " to",
                                "Who is this ticket for?", names);
                        if (chosen == null) {
                            return;
                        }
                        for (Registration registration : registrations) {
                            if (registration.getAttendeeName().equals(chosen)) {
                                com.eventsuite.people.Attendee attendee = suite().people()
                                        .findAttendee(registration.getAttendeeId());
                                if (attendee != null) {
                                    completeSale(event, type, attendee,
                                            com.eventsuite.finance.PaymentMethod.CARD, "", "");
                                    return;
                                }
                            }
                        }
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not complete the sale", failure.getMessage());
        }
    }


    /**
     * Commits a sale.
     *
     * <p>Everything that can be rejected is rejected here with a message naming the
     * thing that was wrong: no name, no usable email, a tier that is sold out, or an
     * event that is not selling. The store would refuse each of these too, but by then
     * the form has been filled in and the message would be about a constraint rather
     * than about the missing field.
     */
    private void sell(TicketType type, String firstName, String lastName, String email,
                      String phone, String organisation, String dietary, String seat,
                      com.eventsuite.finance.PaymentMethod method, String code, Event event) {
        if (type == null) {
            return;
        }
        if (firstName == null || firstName.isBlank()) {
            Ui.dialog(this, "First name required", "A ticket needs a name on it.");
            return;
        }
        if (email == null || email.isBlank()) {
            Ui.dialog(this, "Email required",
                    "The ticket is delivered by email, so an address is needed.");
            return;
        }
        try {
                            com.eventsuite.people.Attendee attendee = suite().findOrCreateAttendee(
                                    firstName.trim(), lastName == null ? "" : lastName.trim(), email.trim(),
                                    phone, organisation, "", dietary, "", false);

                            BigDecimal price = type.getPrice();
                            String trimmedCode = code == null ? "" : code.trim();
                            if (!trimmedCode.isEmpty()) {
                                com.eventsuite.marketing.DiscountCode found =
                                        suite().marketing().findDiscountByCode(event.getId(), trimmedCode);
                                if (found == null) {
                                    Ui.dialog(this, "Code not recognised",
                                            "There is no discount code called " + trimmedCode
                                                    + " on this event.");
                                    return;
                                }
                                String refusal = found.getRejectionReason(LocalDate.now(), type.getName(), price);
                                if (!refusal.isEmpty()) {
                                    Ui.dialog(this, "Code cannot be used", refusal);
                                    return;
                                }
                                price = found.priceOf(price);
                            }

                            Ticket ticket = suite().sellTicket(event, type, attendee, method, trimmedCode,
                                    seat, price);
                            setSubtitle("Sold " + ticket.getReference() + " to " + ticket.getAttendeeName()
                                    + " for " + Money.format(price) + ".");
                            reload();
                            } catch (IllegalArgumentException badDetails) {
                            Ui.dialog(this, "Details not accepted", badDetails.getMessage());
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not take the sale", failure.getMessage());
        }
    }

    private void completeSale(Event event, TicketType type,
                              com.eventsuite.people.Attendee attendee,
                              com.eventsuite.finance.PaymentMethod method, String code,
                              String seat) {
        sell(type, attendee.getFirstName(), attendee.getLastName(), attendee.getEmail(),
                attendee.getPhone(), attendee.getOrganisation(), attendee.getDietaryNotes(),
                seat, method, code, event);
    }

    private void refundSelected() {
        int row = ticketTable == null ? -1 : ticketTable.getSelectedRow();
        if (row < 0) {
            Ui.dialog(this, "Nothing selected", "Choose a ticket from the list first.");
            return;
        }
        Object reference = ticketData.valueAt(row, 0);
        try {
                            Ticket ticket = suite().tickets().findByReference(String.valueOf(reference));
                            if (ticket == null) {
                                Ui.dialog(this, "Ticket not found", "It may have been removed.");
                                return;
                            }
                            if (ticket.getStatus() != TicketStatus.SOLD) {
                                Ui.dialog(this, "Cannot refund this ticket",
                                        "Only a paid ticket can be refunded. This one is "
                                                + ticket.getStatus().getLabel().toLowerCase(java.util.Locale.ROOT)
                                                + ".");
                                return;
                            }
                            if (!Ui.confirm(this, "Refund " + ticket.getReference() + "?",
                                    ticket.getAttendeeName() + " paid " + Money.format(
                                            new BigDecimal(ticket.getPricePaid()))
                                            + ". The seat goes back on sale and the reversal is recorded.")) {
                                return;
                            }
                            suite().refundTicket(ticket, "Refunded at the desk");
                            setSubtitle("Refunded " + ticket.getReference());
                            reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not refund", failure.getMessage());
        }
    }

    // ---------------------------------------------------------------- forms

    private void showTierForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField name = Ui.field("Standard Pass");
        JTextField price = Ui.field("50");
        JTextField quantity = Ui.field(String.valueOf(event.getSellableCapacity()));
        JTextField closes = Ui.field("");
        JComboBox<AccessTier> access = Ui.enumCombo(AccessTier.class, AccessTier.GENERAL);

        JPanel form = Ui.column(
                Ui.titledGroup("Name", name),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Price", price),
                        Ui.titledGroup("How many", quantity)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Opens this access", access),
                        Ui.titledGroup("Sales close (optional)", closes)));
        if (Ui.formDialog(this, "Add a ticket tier", form, "Add tier", "Cancel") == null) {
            return;
        }
        try {
                    BigDecimal amount = Money.of(price.getText());
                    int count;
                    try {
                        count = Integer.parseInt(quantity.getText().trim());
                    } catch (NumberFormatException notANumber) {
                        Ui.dialog(this, "Quantity not understood",
                                "\"" + quantity.getText() + "\" is not a number.");
                        return;
                    }
                    if (count < 0) {
                        Ui.dialog(this, "Quantity cannot be negative",
                                "A tier with nothing in it is not a tier.");
                        return;
                    }
                    LocalDate close = closes.getText().isBlank() ? null
                            : LocalDate.parse(closes.getText().trim());
                    TicketType type = new TicketType(com.eventsuite.data.Ids.ticketType(),
                            event.getId(), name.getText().trim(), "", amount, count,
                            (AccessTier) access.getSelectedItem(), null, close, true,
                            suite().events().nextTicketTypeOrder(event.getId()));
                    suite().events().insertTicketType(type);
                    reload();
                    } catch (IllegalArgumentException badInput) {
                    Ui.dialog(this, "Tier not accepted", badInput.getMessage());
                    } catch (java.time.format.DateTimeParseException badDate) {
                    Ui.dialog(this, "Date not understood",
                            "Use the form 2026-04-14 for the sales closing date.");
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not add the tier", failure.getMessage());
        }
    }

    private void showDiscountForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField code = Ui.field("EARLY10");
        JTextField value = Ui.field("10");
        JTextField limit = Ui.field("100");
        JTextField until = Ui.field("");
        JComboBox<com.eventsuite.marketing.DiscountCode.Kind> kind =
                Ui.enumCombo(com.eventsuite.marketing.DiscountCode.Kind.class,
                        com.eventsuite.marketing.DiscountCode.Kind.PERCENT);

        JPanel form = Ui.column(
                Ui.titledGroup("Code", code),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Kind", kind), Ui.titledGroup("Value", value)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("How many uses (0 is unlimited)", limit),
                        Ui.titledGroup("Valid until (optional)", until)));
        if (Ui.formDialog(this, "Add a discount code", form, "Add code", "Cancel") == null) {
            return;
        }
        try {
                    int uses;
                    try {
                        uses = Integer.parseInt(limit.getText().trim());
                    } catch (NumberFormatException notANumber) {
                        Ui.dialog(this, "Uses not understood",
                                "Put a number, or 0 for no limit.");
                        return;
                    }
                    LocalDate validUntil = until.getText().isBlank() ? null
                            : LocalDate.parse(until.getText().trim());
                    suite().addDiscount(event.getId(), code.getText().trim(),
                            (com.eventsuite.marketing.DiscountCode.Kind) kind.getSelectedItem(),
                            Money.of(value.getText()), uses, LocalDate.now(), validUntil, "",
                            "Added from the ticketing screen");
                    setSubtitle("Discount code " + code.getText() + " added.");
                    } catch (IllegalArgumentException badInput) {
                    Ui.dialog(this, "Code not accepted", badInput.getMessage());
                    } catch (java.time.format.DateTimeParseException badDate) {
                    Ui.dialog(this, "Date not understood", "Use the form 2026-04-14.");
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not add the code", failure.getMessage());
        }
    }
}
