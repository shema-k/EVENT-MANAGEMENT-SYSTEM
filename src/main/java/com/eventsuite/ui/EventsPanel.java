package com.eventsuite.ui;

import com.eventsuite.core.Event;
import com.eventsuite.core.EventCategory;
import com.eventsuite.core.EventStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.service.EventSuite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import com.eventsuite.analytics.EventMetrics;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 * The catalogue: every registered event, grouped by category.
 *
 * <p>Grouped rather than listed because "what kind of things am I running" is the
 * question an organiser with more than a handful of events actually asks. A flat list
 * sorted by date answers it badly: the same concert appears between two workshops with
 * nothing to say they belong together.
 *
 * <p>Creating an event is one form rather than a wizard. The wizard exists to stop
 * people getting it wrong, and most of what it would ask for is already decided by the
 * category — which is why the tiers, budget headings and automation are seeded from
 * it as soon as the event exists rather than asked for separately.
 */
public final class EventsPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private JPanel groupArea;
    private JPanel summaryArea;

    public EventsPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        groupArea = new JPanel();
        groupArea.setOpaque(false);
        groupArea.setLayout(new BoxLayout(groupArea, BoxLayout.Y_AXIS));

        summaryArea = new JPanel(new GridLayout(0, 3, 12, 12));
        summaryArea.setOpaque(false);

        JPanel toolbar = Ui.row(
                Ui.primary("New event", () -> showCreateForm()),
                Ui.button("Refresh", this::reload),
                Ui.muted("Events are grouped by category. Click one to select it for every"
                        + " other screen."));

        JPanel body = Ui.column(toolbar, Ui.gap(14), summaryArea, Ui.gap(14), groupArea);
        screen("Catalogue", "Every registered event, grouped by what kind of thing it is.",
                body);
        reload();
    }

    @Override
    protected void reload() {
        try {
                    fillSummary();
                    fillGroups();
                    } catch (SQLException failure) {
                    showProblem(failure);
                    }
                }

                /**
                 * The counts along the top.
                 *
                 * <p>Total, and how far through the pipeline they are. Knowing there are four
                 * events and none of them has sold a ticket is the useful part, and a plain total
                 * does not say it.
                 */
                private void fillSummary() throws SQLException {
                    DashboardPanel.clear(summaryArea);
                    List<Event> all = suite().events().findAll();
                    long onSale = all.stream().filter(event -> event.getStatus().acceptsSales()).count();
                    long upcoming = all.stream()
                        .filter(event -> event.getDaysUntil(LocalDate.now()) >= 0).count();
                    long completed = all.stream().filter(event -> event.getStatus().isSettled()).count();

                    summaryArea.add(Ui.tile("Registered",
                        String.valueOf(all.size()),
                        upcoming + " still to come"));
                    summaryArea.add(Ui.tile("On sale",
                        String.valueOf(onSale),
                        onSale == 0 ? "Nothing selling" : "Taking money"));
                    summaryArea.add(Ui.tile("Finished",
                        String.valueOf(completed),
                        completed == 0 ? "None reconciled yet"
                                : "Ready to close the books"));
                }

                /** Rebuilds the grouped list. */
                private void fillGroups() throws SQLException {
                    DashboardPanel.clear(groupArea);
                    Map<EventCategory, List<Event>> grouped = suite().catalogue();

                    boolean anyShown = false;
                    for (Map.Entry<EventCategory, List<Event>> entry : grouped.entrySet()) {
                    if (entry.getValue().isEmpty()) {
                        continue;
                    }
                    anyShown = true;
                    groupArea.add(categoryGroup(entry.getKey(), entry.getValue()));
                    groupArea.add(Box.createVerticalStrut(16));
                    }
                    if (!anyShown) {
                    groupArea.add(Ui.card(null, Ui.emptyState("No events yet.",
                            "Use New event to register the first one. Its ticket tiers, budget"
                                    + " headings and automation are set up from the category.")));
                    }
                    groupArea.revalidate();
                    groupArea.repaint();
                }

                /**
                 * One category and its events.
                 *
                 * <p>The category's suggested capacity and typical budget are shown alongside,
                 * because they are what makes the category worth choosing and they give a sense of
                 * whether an event's figures are in the right range for the kind of thing it is.
                 */
                private JComponent categoryGroup(EventCategory category, List<Event> events) {
                    JPanel list = new JPanel();
                    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
                    list.setOpaque(false);
                    list.setAlignmentX(Component.LEFT_ALIGNMENT);

                    for (Event event : events) {
                    list.add(eventRow(category, event));
                    list.add(Box.createVerticalStrut(8));
                    }

                    return Ui.describedCard(category.getLabel(),
                        category.getDescription() + "  Typical capacity "
                                + category.getSuggestedCapacity() + ", usually "
                                + category.getSuggestedSpeakers() + " speakers.",
                        list);
                }

                /** One event, with its figures and the actions that change its state. */
    /** One event, with its figures and the actions that change its state. */
    private JComponent eventRow(EventCategory category, Event event) {
        JPanel row = new JPanel(new BorderLayout(14, 6));
        row.setBackground(Theme.current().field());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        row.add(factsPanel(event), BorderLayout.CENTER);
        row.add(actionsPanel(event), BorderLayout.EAST);
        return row;
    }

    /** The name, dates, venue and the two bars for one event. */
    private JPanel factsPanel(Event event) {
        JPanel facts = new JPanel();
        facts.setOpaque(false);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));

        JPanel titleLine = Ui.row(Ui.bodyBold(event.getName()),
                Ui.status(event.getStatus().getLabel()),
                Ui.muted("  " + event.getDateRange()));
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(titleLine);
        facts.add(Box.createVerticalStrut(5));

        long days = event.getDaysUntil(LocalDate.now());
        String when = days > 1 ? days + " days away"
                : days == 1 ? "Tomorrow"
                : days == 0 ? "Today" : Math.abs(days) + " days ago";
        JLabel place = Ui.muted((event.getVenueName().isBlank()
                ? "No venue set" : event.getVenueName())
                + "  \u00b7  " + when + "  \u00b7  " + event.getScale().getLabel()
                + " event  \u00b7  holds " + event.getCapacity());
        place.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(place);
        facts.add(Box.createVerticalStrut(8));

        try {
                        EventMetrics metrics = suite().metrics().forEvent(event.getId());
                        facts.add(Ui.bar("Tickets " + metrics.getTicketsSold() + " of "
                                        + metrics.getSellableCapacity(),
                                metrics.getSellThroughPercent().intValue(), Theme.current().accent()));
                        facts.add(Box.createVerticalStrut(5));
                        facts.add(Ui.bar("Budget " + Money.compact(metrics.getBudgetActual()) + " of "
                                        + Money.compact(metrics.getBudgetPlanned()),
                                metrics.getBudgetUsedPercent().intValue(),
                                metrics.isOverBudget() ? Theme.current().danger()
                                        : Theme.current().success()));
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not read the figures", failure.getMessage());
        }
        // The row is still worth showing without its figures. A catalogue that vanishes
        // because one event's numbers could not be read is worse than one missing a bar.
        return facts;
    }

    /** The status menu and the two buttons for one event. */
    private JPanel actionsPanel(Event event) {
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));

        JComboBox<EventStatus> moves =
                Ui.enumCombo(EventStatus.class, event.getStatus());
        moves.setMaximumSize(new Dimension(150, 30));
        moves.setEnabled(!event.getStatus().allowedMoves().isEmpty());
        moves.setToolTipText(moves.isEnabled() ? "Move this event on"
                : "No further moves from " + event.getStatus().getLabel());
        moves.addActionListener(selection -> {
            EventStatus chosen = (EventStatus) moves.getSelectedItem();
            if (chosen != null && chosen != event.getStatus()) {
                advance(event, chosen);
            }
        });
        actions.add(moves);
        actions.add(Box.createVerticalStrut(6));
        actions.add(Ui.smallButton("Select", () -> selectEvent(event)));
        actions.add(Box.createVerticalStrut(4));
        actions.add(Ui.smallButton("Remove", () -> remove(event)));
        return actions;
    }


    /**
     * Moves an event on, and refuses the moves the lifecycle does not allow.
     *
     * <p>The menu is built from the allowed moves rather than from every state, so an
     * illegal move cannot even be selected. The rule is still checked on the way
     * through, because a rule enforced only in a menu is a rule the next screen
     * forgets.
     */
    private void advance(Event event, EventStatus next) {
        try {
                        try {
                            suite().changeStatus(event, next);
                        } catch (IllegalStateException notAllowed) {
                            Ui.dialog(this, "Cannot move the event", notAllowed.getMessage());
                            return;
                        }
                        setSubtitle(event.getName() + " is now " + next.getLabel() + ".");
                        reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not save", failure.getMessage());
        }
    }

    private void selectEvent(Event event) {
        MainWindow window = MainWindow.of(this);
        if (window != null) {
            window.selectEvent(event.getId());
        }
    }

    /**
     * Removes an event, after saying plainly what would be lost.
     *
     * <p>The refusal comes from the store, because it is the store that knows whether
     * tickets were sold. The screen asks first because a confirmation dialog that
     * always says yes would make the store's refusal the only protection, which is too
     * late to be helpful.
     */
    private void remove(Event event) {
        try {
                            if (!suite().events().isDeletable(event.getId())) {
                                Ui.dialog(this, "Cannot remove this event",
                                        "It has tickets sold, money recorded or people who have checked in."
                                                + " Its history is the only record that it happened, so it"
                                                + " stays. Cancel it instead if it is not going ahead.");
                                return;
                            }
                            if (!Ui.confirm(this, "Remove " + event.getName() + "?",
                                    "This deletes the event and its ticket tiers. It cannot be undone."
                                            + " Nothing has been sold against it.")) {
                                return;
                            }
                            suite().deleteEvent(event.getId());
                            reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not remove", failure.getMessage());
        }
    }

    // ---------------------------------------------------------------- the form
    /** Opens the new-event form from the command palette. */
    public void newEventFromPalette() {
        showCreateForm();
    }



    /**
     * The new-event form.
     *
     * <p>Shown as a dialog with the category's suggestions shown live, so the effect of
     * choosing "Festival" is visible before the event is created rather than
     * discovered afterwards.
     */
    private void showCreateForm() {
        JTextField name = Ui.field("");
        JComboBox<EventCategory> category = Ui.enumCombo(EventCategory.class,
                EventCategory.CONFERENCE);
        JTextField organiser = Ui.field("");
        JTextField capacity = Ui.field("400");
        JTextField budget = Ui.field("15000");
        JTextField venue = Ui.field("");
        JTextField dates = Ui.field(LocalDate.now().plusMonths(2) + " to "
                + LocalDate.now().plusMonths(2));
        JTextField brand = Ui.field("#2563EB");
        JTextField description = Ui.field("");

        JLabel hint = Ui.muted(((EventCategory) category.getSelectedItem()).getDescription());

        category.addActionListener(event -> {
            EventCategory chosen = (EventCategory) category.getSelectedItem();
            hint.setText(chosen.getDescription() + "  Suggested capacity "
                    + chosen.getSuggestedCapacity() + ".");
            // Only offered as a suggestion, and only when the field still holds what a
            // form would have put there, so it never overwrites a number somebody typed.
            if (capacity.getText().trim().equals(String.valueOf(
                    EventCategory.CONFERENCE.getSuggestedCapacity()))) {
                capacity.setText(String.valueOf(chosen.getSuggestedCapacity()));
            }
        });

        JPanel form = Ui.column(
                Ui.titledGroup("What is it called?", name),
                Ui.gap(10),
                Ui.titledGroup("What kind of event?", Ui.row(category, hint)),
                Ui.gap(10),
                Ui.titledGroup("Who is organising it?", organiser),
                Ui.gap(10),
                Ui.row(
                        Ui.titledGroup("Capacity", capacity),
                        Ui.titledGroup("Planned budget", budget)),
                Ui.gap(10),
                Ui.titledGroup("Venue (a webinar needs none)", venue),
                Ui.gap(10),
                Ui.titledGroup("Dates", dates),
                Ui.gap(10),
                Ui.titledGroup("Brand colour", brand),
                Ui.gap(10),
                Ui.titledGroup("What is it for?", description));

        String outcome = Ui.formDialog(this, "Register a new event", form,
                "Register event", "Cancel");
        if (outcome == null) {
            return;
        }
        create(name.getText(), (EventCategory) category.getSelectedItem(),
                organiser.getText(), capacity.getText(), budget.getText(),
                venue.getText(), dates.getText(), brand.getText(), description.getText());
    }

    /**
     * Creates the event, reporting the first thing that was wrong with the form.
     *
     * <p>Validation lives here rather than in the dialog so it is the same check
     * whatever calls it. An unparseable capacity is caught and named, because
     * "something went wrong" tells the person nothing about which box to look at.
     */
    private void create(String name, EventCategory category, String organiser,
                        String capacityText, String budgetText, String venue,
                        String datesText, String brand, String description) {
        try {
                    if (name == null || name.isBlank()) {
                        Ui.dialog(this, "Name required", "An event needs a name.");
                        return;
                    }
                    int capacity;
                    try {
                        capacity = Integer.parseInt(capacityText.trim());
                    } catch (NumberFormatException notANumber) {
                        Ui.dialog(this, "Capacity not understood",
                                "\"" + capacityText + "\" is not a number. Capacity is how many"
                                        + " people the room holds.");
                        return;
                    }
                    if (capacity <= 0) {
                        Ui.dialog(this, "Capacity must be at least one",
                                "A room that holds nobody cannot hold an event.");
                        return;
                    }
                    BigDecimal budget = Money.of(budgetText);
                    if (budget.signum() < 0) {
                        Ui.dialog(this, "Budget cannot be negative",
                                "A planned budget is what you expect to spend, not a receipt.");
                        return;
                    }

                    LocalDate[] range = parseDates(datesText);
                    if (range == null) {
                        Ui.dialog(this, "Dates not understood",
                                "Use one date for a single day, or \"from to\" for several. "
                                        + "For example: 2026-04-14 to 2026-04-16");
                        return;
                    }

                    Event event = suite().createEvent(null, name, category, "", venue,
                            range[0], range[1], capacity, budget, organiser, description,
                            brand, "", "",
                            new ArrayList<>(List.of()));
                    setSubtitle(event.getName() + " registered as " + category.getLabel() + ".");
                    reload();
                    MainWindow window = MainWindow.of(this);
                    if (window != null) {
                        window.selectEvent(event.getId());
                    }
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not register the event", failure.getMessage());
        }
    }

    /**
     * Reads one date or a range.
     *
     * @return the start and end, or null when the text could not be read. A range is
     *         made valid rather than rejected when it runs backwards, because typing
     *         the two dates in the wrong order is an easy slip rather than a mistake
     *         worth blocking on.
     */
    static LocalDate[] parseDates(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.toLowerCase(java.util.Locale.ROOT)
                .replace(" to ", " ").split("\\s+");
        try {
            LocalDate start = LocalDate.parse(parts[0].trim());
            LocalDate end = parts.length > 1 ? LocalDate.parse(parts[1].trim()) : start;
            return end.isBefore(start) ? new LocalDate[]{start, start} : new LocalDate[]{start, end};
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private void showProblem(SQLException failure) {
        Ui.dialog(this, "Could not load the catalogue", failure.getMessage());
    }
}
