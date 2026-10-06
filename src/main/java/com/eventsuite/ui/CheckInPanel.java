package com.eventsuite.ui;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.core.Event;
import com.eventsuite.ops.AccessGate;
import com.eventsuite.ops.CheckIn;
import com.eventsuite.ops.CheckInOutcome;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.Ticket;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * The door: scanning a ticket in, and what happened.
 *
 * <p>Built for speed, because it is used by somebody holding a queue of people. One
 * field takes focus, a code is typed, and the result appears as a big colour-coded
 * line. There is nothing else to press and nothing to choose, because a desk
 * operator with fourteen people waiting does not have time to navigate.
 *
 * <p>Every scan is shown, including the refusals. A desk that only shows the good ones
 * cannot tell the organiser that the door list is wrong or that a VIP has arrived
 * without the right ticket, which are the two things worth catching while the event is
 * still running.
 */
public final class CheckInPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private JTextField code;
    private JLabel verdict;
    private JLabel gateLabel;
    private JPanel tileArea;
    private JPanel arrivalsArea;
    private JTable scanTable;
    private TableData scanData;
    private AccessGate activeGate;
    private String operator = "";

    public CheckInPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Check-in", "Choose an event in the header to open its door.",
                    Ui.emptyState("No event selected.", "Pick an event first."));
            return;
        }

        List<AccessGate> gates = suite().gatesFor(event);
        activeGate = gates.isEmpty() ? AccessGate.mainEntrance(event.getId()) : gates.get(0);

        code = Ui.field("");
        code.setPreferredSize(new java.awt.Dimension(320, 42));
        code.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 17));
        code.setToolTipText("Type or scan a ticket reference");

        verdict = new JLabel("Ready.", JLabel.CENTER);
        verdict.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 19));

        gateLabel = Ui.muted("");

        JPanel desk = Ui.card("The desk",
                Ui.row(Ui.bodyBold("Reference or access code"), code,
                        Ui.primary("Admit", this::admit),
                        Ui.smallButton("No ticket", this::admitWithoutTicket)),
                Ui.gap(10),
                wrap(verdict),
                Ui.gap(8),
                wrap(gateLabel));

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);

        arrivalsArea = new JPanel();
        arrivalsArea.setOpaque(false);
        arrivalsArea.setLayout(new BoxLayout(arrivalsArea, BoxLayout.Y_AXIS));

        scanData = TableData.with("Time", "Holder", "Tier", "Door", "Outcome")
                .withStatus(4);
        scanTable = tableOf(scanData);

        JTextField operatorField = Ui.field("desk");
        operatorField.setToolTipText("Who is operating the desk");
        JPanel body = Ui.column(
                Ui.row(Ui.bodyBold("Operating as"), operatorField),
                Ui.gap(12),
                desk,
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                Ui.describedCard("Doors", "What each one needs, and whether it is open.",
                        gateList(event, gates)),
                Ui.gap(14),
                Ui.card(null,
                        Ui.titledGroup("Every scan", "Admissions and refusals alike.",
                                Ui.scroll(scanTable))),
                Ui.gap(14),
                Ui.describedCard("Arriving now", "The last scans as they happen.", arrivalsArea));
        screen("Check-in", event.getName() + "  \u00b7  " + event.getDateRange(), body);

        operatorField.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    @Override
                    public void insertUpdate(javax.swing.event.DocumentEvent event) {
                        operator = operatorField.getText();
                    }

                    @Override
                    public void removeUpdate(javax.swing.event.DocumentEvent event) {
                        operator = operatorField.getText();
                    }

                    @Override
                    public void changedUpdate(javax.swing.event.DocumentEvent event) {
                        operator = operatorField.getText();
                    }
                });

        code.addActionListener(pressed -> admit());
        reload();
    }

    private JPanel wrap(java.awt.Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected void reload() {
        Event event = currentEvent();
        if (event == null || tileArea == null) {
            return;
        }
        try {
                            EventMetrics metrics = suite().metrics().forEvent(event.getId());
                            DashboardPanel.clear(tileArea);
                            tileArea.add(Ui.tile("Admitted",
                                    String.valueOf(metrics.getTicketsCheckedIn()),
                                    "of " + event.getCapacity() + " capacity",
                                    metrics.getTicketsCheckedIn() >= event.getCapacity()
                                            ? Theme.current().success() : Theme.current().text()));
                            tileArea.add(Ui.tile("Bought a ticket",
                                    String.valueOf(metrics.getTicketsSold()),
                                    metrics.getTicketsCheckedIn() == 0 ? "Doors not open yet"
                                            : Math.round(metrics.getAttendanceRate().doubleValue())
                                                    + "% have arrived"));
                            tileArea.add(Ui.tile("Problems at the door",
                                    String.valueOf(metrics.getRejectedEntries()),
                                    metrics.getRejectedEntries() == 0 ? "None at all" : "Worth looking at",
                                    healthColour(metrics.getRejectedEntries() > 0)));
                            tileArea.add(Ui.tile("Waved in by hand",
                                    String.valueOf(suite().ops().countOverrides(event.getId())),
                                    "Overrides the rule allowed"));
                            fillArrivals(event);
                            fillScans(event);
                            updateGateLabel(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the door figures", failure.getMessage());
        }
    }

    private void fillArrivals(Event event) throws SQLException {
        DashboardPanel.clear(arrivalsArea);
        List<CheckIn> recent = suite().ops().findRecentCheckIns(event.getId(), 12);
        if (recent.isEmpty()) {
            arrivalsArea.add(Ui.emptyState("Nobody through the door yet.",
                    "Scans appear here as they happen."));
            return;
        }
        for (CheckIn scan : recent) {
            arrivalsArea.add(Ui.row(
                    Ui.muted(scan.getTimeLabel()),
                    Ui.body(scan.getHolderName()),
                    Ui.muted(scan.getTier().getLabel()),
                    Ui.status(scan.getOutcome().getLabel())));
            arrivalsArea.add(javax.swing.Box.createVerticalStrut(4));
        }
    }

    private void fillScans(Event event) throws SQLException {
        scanData.rowsAsList().clear();
        for (CheckIn scan : suite().ops().findCheckIns(event.getId())) {
            scanData.add(scan.getTimeLabel(), scan.getHolderName(),
                    scan.getTier().getLabel(), scan.getGateName(),
                    scan.getOutcome().getLabel());
        }
        scanTable.repaint();
    }

    /**
     * The door list.
     *
     * <p>Clicking a door makes it the active one, so the next scan is judged against
     * it. Selecting the door before the queue arrives is what stops a VIP being
     * announced at the general entrance because nobody had said which door it was.
     */
    private javax.swing.JComponent gateList(Event event, List<AccessGate> gates) {
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (AccessGate gate : gates) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            row.add(Ui.body(gate.getName()), BorderLayout.WEST);
            row.add(Ui.muted(gate.getRequirementLabel()), BorderLayout.CENTER);
            JPanel right = Ui.row(
                    Ui.status(gate.isOpen() ? "Open" : "Closed"),
                    Ui.smallButton("Use this door", () -> {
                        activeGate = gate;
                        updateGateLabel(event);
                    }),
                    Ui.smallButton(gate.isOpen() ? "Close" : "Open", () -> {
                        suite().setGateOpen(gate, !gate.isOpen());
                        reload();
                    }));
            row.add(right, BorderLayout.EAST);
            row.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 0, 6, 0));
            list.add(row);
        }
        return Ui.scroll(list);
    }

    private void updateGateLabel(Event event) {
        if (gateLabel == null || activeGate == null) {
            return;
        }
        gateLabel.setText("Judging scans against " + activeGate.getName()
                + " \u2014 needs " + activeGate.getRequirementLabel() + ".");
    }

    /**
     * Scans a code at the desk.
     *
     * <p>Exact match first, then a prefix. A prefix only when four characters or more
     * were typed, because anything shorter would match half the book and the operator
     * would be choosing from a list of hundreds at the door.
     */
    private void admit() {
        Event event = currentEvent();
        if (event == null || code == null) {
            return;
        }
        String typed = code.getText().trim();
        if (typed.isEmpty()) {
            say("Type a reference first.", Theme.current().muted());
            return;
        }
        try {
                    Ticket ticket = suite().tickets().findByReference(typed);
                    if (ticket == null) {
                        List<Ticket> partial = suite().tickets().findByReferencePrefix(typed, 6);
                        if (partial.size() == 1) {
                            ticket = partial.get(0);
                        } else if (partial.size() > 1) {
                            Ui.dialog(this, "Several tickets match",
                                    "\"" + typed + "\" matches " + partial.size() + " tickets."
                                            + " Scan the whole code, or search the attendee list.");
                            code.setText("");
                            code.requestFocusInWindow();
                            return;
                        } else {
                            Ticket holder = findByAccessCode(event, typed);
                            if (holder != null) {
                                say("Access code " + typed + " is not attached to a ticket.",
                                        Theme.current().warning());
                                code.setText("");
                                code.requestFocusInWindow();
                                return;
                            }
                            say("No ticket with that reference.", Theme.current().danger());
                            code.setText("");
                            code.requestFocusInWindow();
                            return;
                        }
                    }
                    if (!ticket.getEventId().equals(event.getId())) {
                        say("That ticket is for a different event.", Theme.current().danger());
                        code.setText("");
                        code.requestFocusInWindow();
                        return;
                    }

                    CheckIn scan = suite().checkIn(ticket, event, activeGate, operator, Instant.now());
                    say(scan.getOutcome().getLabel() + " \u2014 " + scan.getHolderName()
                                    + "  " + scan.getOutcome().getMessage(),
                            scan.isAdmitted() ? Theme.current().success() : Theme.current().danger());
                    code.setText("");
                    code.requestFocusInWindow();
                    reload();
                    } catch (SQLException failure) {
                    say("The scan could not be recorded.", Theme.current().danger());
                    Ui.dialog(this, "Scan failed", failure.getMessage());
                    }
                }

                private Ticket findByAccessCode(Event event, String code) throws SQLException {
                    com.eventsuite.people.Attendee attendee =
                        suite().people().findAttendeeByAccessCode(event.getId(), code);
                    if (attendee == null) {
                    return null;
                    }
                    List<Ticket> tickets = suite().tickets().findByAttendee(event.getId(),
                        attendee.getId());
                    return tickets.isEmpty() ? null : tickets.get(0);
                }

                /** Admits somebody who has no ticket, and records why. */
                private void admitWithoutTicket() {
                    Event event = currentEvent();
                    if (event == null) {
                    return;
                    }
                    JTextField name = Ui.field("");
                    JTextField reason = Ui.field("Left at the desk");
                    JPanel form = Ui.column(Ui.titledGroup("Their name", name), Ui.gap(8),
                        Ui.titledGroup("Why they are being admitted", reason));
                    if (Ui.formDialog(this, "Admit without a ticket", form, "Admit", "Cancel") == null) {
                    return;
                    }
                    if (name.getText().isBlank()) {
                    Ui.dialog(this, "Name required",
                            "Record who was admitted, even without a ticket.");
                    return;
                    }
                    try {
                                            CheckIn scan = suite().admitWithoutTicket(event, activeGate, name.getText().trim(),
                                                reason.getText(), operator, Instant.now());
                                            say("Admitted " + scan.getHolderName() + " without a ticket.",
                                                Theme.current().warning());
                                            reload();
                    } catch (SQLException failure) {
                        Ui.dialog(this, "Could not admit", failure.getMessage());
                    }
    }

    /** The big line under the field: what just happened, in colour. */
    private void say(String message, Color colour) {
        if (verdict == null) {
            return;
        }
        verdict.setText(message);
        verdict.setForeground(colour);
    }
}
