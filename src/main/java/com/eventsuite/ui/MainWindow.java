package com.eventsuite.ui;

import com.eventsuite.core.Event;
import com.eventsuite.data.Database;
import com.eventsuite.service.EventSuite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.JSplitPane;
import javax.swing.Timer;

/**
 * The window: a sidebar of screens, an event selector, and a refresh timer.
 *
 * <p>Screens are built once and kept. Switching rebuilds nothing, which is why moving
 * between screens is instant and why a live screen does not lose its scroll position
 * while it refreshes.
 *
 * <p>The timer is what makes the dashboards real time. It runs every five seconds and
 * reloads only the screen on show, and only while the window is actually visible.
 * Both conditions are checked rather than assumed, because a timer refreshing a
 * hidden window is a database round trip every five seconds for a screen nobody is
 * looking at.
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** How often the visible screen reloads itself. */
    private static final int REFRESH_MILLIS = 5000;

    /** The screens, in sidebar order: key, label, explanation. */
    private static final String[][] SCREENS = {
            {"dashboard", "Dashboard", "Everything, and what needs a decision"},
            {"catalogue", "Catalogue", "Registered events, grouped by category"},
            {"ticketing", "Ticketing", "Tiers, sales and the tickets issued"},
            {"attendees", "Attendees", "Who is coming, and who is waiting"},
            {"speakers", "Speakers and kit", "The programme and what has been booked"},
            {"marketing", "Marketing", "Campaigns, costs and discount codes"},
            {"checkin", "Check-in", "The door, and every scan"},
            {"engagement", "Engagement", "What people did, and the footprint"},
            {"finance", "Finance", "Takings, costs, budget and the ledger"},
            {"analytics", "Analytics", "Feedback, return on spend, reconciliation"},
            {"automation", "Automation", "Rules and what they have done"},
    };

    /** The sidebar groups, in order. Each names the screens below it. */
    private static final String[][] GROUPS = {
            {"Overview", "dashboard catalogue"},
            {"Before the event", "ticketing attendees speakers marketing"},
            {"On the day", "checkin engagement"},
            {"Afterwards", "finance analytics automation"},
    };

    private final EventSuite suite;
    private final Database database;
    private final Map<String, BasePanel> panels = new LinkedHashMap<>();
    private final Map<String, NavRow> nav = new LinkedHashMap<>();

    private final JPanel navPanel = new JPanel(new BorderLayout());
    private final JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
    private final JPanel bodyPanel = new JPanel(new CardLayout());
    private final JLabel headerTitle = Ui.bodyBold("Event Management");
    private final JLabel headerDetail = Ui.muted("");
    private final JComboBox<String> eventChooser = new JComboBox<>();

    /** The event ids, in the same order as the chooser's items. */
    private final List<String> chooserIds = new ArrayList<>();

    private final CardLayout cards = new CardLayout();
    private final Timer timer;

    private String currentKey = "dashboard";
    private String currentEventId = "";
    private boolean refreshing;
    private boolean chooserListening;
    private CommandPalette palette;

    /**
     * Builds the window.
     *
     * <p>The database is held so the window can close it. The connection outlives the
     * call that opened it, because the refresh timer keeps reading it for as long as
     * the window is open; whoever started the application cannot close it on the way
     * out without pulling the ground from under a live screen.
     */
    public MainWindow(EventSuite suite, Database database) {
        super("Event Management");
        this.suite = suite;
        this.database = database;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1460, 960);
        setMinimumSize(new Dimension(1140, 780));
        setLocationRelativeTo(null);

        buildPanels();
        buildHeader();
        buildSidebar();
        buildPalette();

        bodyPanel.setLayout(cards);
        for (Map.Entry<String, BasePanel> entry : panels.entrySet()) {
            entry.getValue().setEventId(currentEventId);
            bodyPanel.add(entry.getValue(), entry.getKey());
        }

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navPanel, bodyPanel);
        split.setDividerSize(1);
        split.setDividerLocation(236);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);
        split.setEnabled(false);

        // Ctrl+K opens the command palette from anywhere in the window.
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK),
                        "command-palette");
        getRootPane().getActionMap().put("command-palette", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                palette.open();
            }
        });

        setLayout(new BorderLayout());
        add(headerPanel, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutDown();
            }
        });

        timer = new Timer(REFRESH_MILLIS, tick -> refreshVisible());
        timer.start();

        Theme.onChange(palette -> restyle());
        show("dashboard");
        refreshEventChooser();
        releaseExpiredHolds();
    }

    /** One instance of each screen, created once and kept for the life of the window. */
    private void buildPanels() {
        panels.put("dashboard", new DashboardPanel(suite));
        panels.put("catalogue", new EventsPanel(suite));
        panels.put("ticketing", new TicketingPanel(suite));
        panels.put("attendees", new AttendeesPanel(suite));
        panels.put("speakers", new SpeakersPanel(suite));
        panels.put("marketing", new MarketingPanel(suite));
        panels.put("checkin", new CheckInPanel(suite));
        panels.put("engagement", new EngagementPanel(suite));
        panels.put("finance", new FinancePanel(suite));
        panels.put("analytics", new AnalyticsPanel(suite));
        panels.put("automation", new WorkflowsPanel(suite));
    }

    /**
     * The header: which event is being looked at, and the theme switch.
     *
     * <p>The chooser sits here rather than in each screen because the selection is a
     * property of the window, not of a screen. Every panel is pointed at the same
     * event, so there is one thing to be confused about rather than eleven.
     */
    private void buildHeader() {
        headerPanel.setBackground(Theme.current().card());
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.current().border()),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        headerTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(headerTitle);
        titles.add(Box.createVerticalStrut(3));
        titles.add(headerDetail);

        eventChooser.setFont(Theme.Type.body());
        eventChooser.setPreferredSize(new Dimension(340, 32));
        eventChooser.setBackground(Theme.current().field());
        eventChooser.setForeground(Theme.current().text());

        JPanel chooserLabel = new JPanel(new BorderLayout(10, 0));
        chooserLabel.setOpaque(false);
        chooserLabel.add(Ui.body("Showing"), BorderLayout.WEST);
        chooserLabel.add(eventChooser, BorderLayout.CENTER);

        // The palette trigger looks like the search box it behaves as: it is the one
        // thing an administrator reaches for, so it is the one thing that deserves to
        // look like the primary control.
        JPanel paletteTrigger = new JPanel(new BorderLayout(10, 0));
        paletteTrigger.setBackground(Theme.current().field());
        paletteTrigger.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().fieldBorder(), 1, true),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        paletteTrigger.setCursor(java.awt.Cursor.getPredefinedCursor(
                java.awt.Cursor.HAND_CURSOR));
        paletteTrigger.setToolTipText("Open the command palette");
        paletteTrigger.add(Ui.muted("Search commands and actions"), BorderLayout.CENTER);
        JLabel shortcut = new JLabel("Ctrl K");
        shortcut.setFont(Theme.Type.smallBold());
        shortcut.setForeground(Theme.current().onAccent());
        shortcut.setOpaque(true);
        shortcut.setBackground(Theme.current().accent());
        shortcut.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        paletteTrigger.add(shortcut, BorderLayout.EAST);
        paletteTrigger.setPreferredSize(new Dimension(300, 34));
        paletteTrigger.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                palette.open();
            }
        });

        JPanel right = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(paletteTrigger);
        right.add(chooserLabel);

        headerPanel.add(titles, BorderLayout.WEST);
        headerPanel.add(right, BorderLayout.EAST);
    }

    /**
     * The sidebar, grouped by where in an event's life each screen matters.
     *
     * <p>The grouping is the point. Somebody preparing an event reads down the first
     * block; somebody running the door reads the third. A flat alphabetical list would
     * make the person with a queue at the door find the door.
     */
    private void buildSidebar() {
        navPanel.setBackground(Theme.current().sidebar());
        navPanel.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        for (String[] group : GROUPS) {
            JLabel heading = new JLabel(group[0].toUpperCase(java.util.Locale.ROOT));
            heading.setFont(Theme.Type.tiny());
            heading.setForeground(Theme.current().sidebarMuted());
            heading.setBorder(BorderFactory.createEmptyBorder(16, 12, 6, 12));
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(heading);
            for (String key : group[1].split(" ")) {
                NavRow row = new NavRow(key, labelFor(key));
                row.setToolTipText(explanationFor(key));
                row.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent event) {
                        show(key);
                    }
                });
                nav.put(key, row);
                list.add(row);
            }
        }
        navPanel.add(Ui.scroll(list), BorderLayout.CENTER);
    }

    /**
     * Fills the command palette with screens and the actions of the visible one.
     *
     * <p>Navigation comes from the screen list itself, so a new screen appears in the
     * palette the moment it exists. The actions come from the panel on show, which is
     * what makes "sell a ticket" available from the ticketing screen and "check in" from
     * the door without either being reachable from the wrong place.
     */
    private void buildPalette() {
        palette = new CommandPalette(this);
        List<CommandPalette.Action> actions = new ArrayList<>();

        for (String[] screen : SCREENS) {
            final String key = screen[0];
            actions.add(new CommandPalette.Action("Go to " + screen[1].toLowerCase(
                    java.util.Locale.ROOT), "Navigate",
                    screen[2] + " screen open " + key, () -> show(key)));
        }
        actions.add(new CommandPalette.Action("New event", "Create",
                "register add catalogue", () -> {
                    show("catalogue");
                    ((EventsPanel) panels.get("catalogue")).newEventFromPalette();
                }));
        actions.add(new CommandPalette.Action("Sell a ticket", "Ticketing",
                "sale take payment checkin box office", () -> {
                    show("ticketing");
                    ((TicketingPanel) panels.get("ticketing")).newSaleFromPalette();
                }));
        actions.add(new CommandPalette.Action("Check in someone", "Door",
                "scan admit arrival door", () -> show("checkin")));
        actions.add(new CommandPalette.Action("Run the automation rules", "Automation",
                "workflow fire rules", () -> {
                    show("automation");
                    ((WorkflowsPanel) panels.get("automation")).runRules();
                }));
        actions.add(new CommandPalette.Action("Write the event report", "Analytics",
                "export reconciliation summary", () -> {
                    show("analytics");
                    ((AnalyticsPanel) panels.get("analytics")).writeReport();
                }));
        actions.add(new CommandPalette.Action("Switch light and dark", "Display",
                "theme appearance colour", () -> {
                    Theme.toggle();
                    restyle();
                }));

        palette.setActions(actions);
    }

    private String labelFor(String key) {
        for (String[] screen : SCREENS) {
            if (screen[0].equals(key)) {
                return screen[1];
            }
        }
        return key;
    }

    private String explanationFor(String key) {
        for (String[] screen : SCREENS) {
            if (screen[0].equals(key)) {
                return screen[2];
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- navigation

    /** Shows one screen and paints the sidebar to match. */
    public void show(String key) {
        BasePanel panel = panels.get(key);
        if (panel == null) {
            return;
        }
        currentKey = key;
        panel.setEventId(currentEventId);
        cards.show(bodyPanel, key);
        paintNav();
        updateHeader();
        releaseExpiredHolds();
    }

    /**
     * Points every screen at an event.
     *
     * <p>Selecting an event from the catalogue lands on the dashboard for it, because
     * that is the screen which answers "how is this one doing" rather than sending
     * somebody back to the list they just used.
     */
    public void selectEvent(String eventId) {
        currentEventId = eventId == null ? "" : eventId;
        for (BasePanel panel : panels.values()) {
            panel.setEventId(currentEventId);
        }
        show(currentKey.equals("catalogue") || currentKey.isEmpty() ? "dashboard" : currentKey);
    }

    /** The event every screen is showing. Empty means all of them. */
    public String getSelectedEventId() {
        return currentEventId;
    }

    /** The window a panel belongs to, for screens that need to change the selection. */
    public static MainWindow of(Component component) {
        if (component == null) {
            return null;
        }
        Window window = javax.swing.SwingUtilities.getWindowAncestor(component);
        return window instanceof MainWindow main ? main : null;
    }

    private void paintNav() {
        for (Map.Entry<String, NavRow> entry : nav.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(currentKey));
        }
    }

    private void updateHeader() {
        if (currentEventId.isEmpty()) {
            headerTitle.setText("Event Management");
            headerDetail.setText(labelFor(currentKey) + "  \u00b7  every event");
            return;
        }
        Event event;
        try {
            event = suite.events().findById(currentEventId);
        } catch (SQLException unreadable) {
            event = null;
        }
        if (event == null) {
            headerTitle.setText("Event Management");
            headerDetail.setText("The selected event is no longer in the catalogue.");
            return;
        }
        headerTitle.setText(event.getName());
        headerDetail.setText(event.getCategory().getLabel() + "  \u00b7  "
                + event.getDateRange() + "  \u00b7  " + event.getStatus().getLabel());
    }

    /**
     * Refills the event picker.
     *
     * <p>The listener is attached once and reads the current selection rather than
     * capturing one, so refilling the list cannot leave a stale closure deciding what
     * "selected" means.
     */
    public void refreshEventChooser() {
        if (!chooserListening) {
            eventChooser.addActionListener(selection -> {
                int index = eventChooser.getSelectedIndex();
                if (index < 0 || index >= chooserIds.size()) {
                    return;
                }
                String chosen = chooserIds.get(index);
                if (!chosen.equals(currentEventId)) {
                    selectEvent(chosen);
                }
            });
            chooserListening = true;
        }
        chooserIds.clear();
        eventChooser.removeAllItems();
        chooserIds.add("");
        eventChooser.addItem("All events");
        try {
            for (Event event : suite.events().findAll()) {
                chooserIds.add(event.getId());
                eventChooser.addItem(event.getName() + "  \u00b7  " + event.getDateRange());
            }
        } catch (SQLException unreadable) {
            chooserIds.add("");
            eventChooser.addItem("Events could not be read");
        }
        int wanted = chooserIds.indexOf(currentEventId);
        eventChooser.setSelectedIndex(wanted >= 0 ? wanted : 0);
    }

    // ---------------------------------------------------------------- refresh

    /** Reloads the visible screen, if it is safe to do so. */
    private void refreshVisible() {
        if (refreshing || !isShowing()) {
            return;
        }
        refreshing = true;
        try {
            BasePanel panel = panels.get(currentKey);
            if (panel != null) {
                panel.refreshInBackground();
            }
        } finally {
            refreshing = false;
        }
    }

    /**
     * Releases unpaid holds whose deadline has passed.
     *
     * <p>Run at startup and on every screen change, because it must not depend on a
     * particular screen being open. Without it a room fills with holds from people who
     * went home, and the event sells out to nobody.
     */
    private void releaseExpiredHolds() {
        try {
            int released = suite.releaseExpiredHolds();
            if (released > 0) {
                headerDetail.setText(headerDetail.getText() + "  \u00b7  "
                        + released + " expired hold(s) released");
            }
        } catch (SQLException failure) {
            // Nothing to do. The sale path refuses to go over an allocation anyway, so
            // the worst case is that a stale hold is shown until it is paid or expires.
        }
    }

    /** Repaints after a theme change, without rebuilding any screen. */
    private void restyle() {
        headerPanel.setBackground(Theme.current().card());
        navPanel.setBackground(Theme.current().sidebar());
        bodyPanel.setBackground(Theme.current().page());
        show(currentKey);
    }

    /**
     * Closes cleanly, in the only order that works.
     *
     * <p>The timer is stopped first, then the screens are told the database is going,
     * then the connection is closed, and only then does the process end. Closing the
     * connection while the timer is still live is what produces a burst of
     * "database is already closed" errors on the way out, because a read that was
     * already in flight lands on a connection that has just gone.
     */
    private void shutDown() {
        timer.stop();
        for (BasePanel panel : panels.values()) {
            panel.stopRefreshing();
        }
        try {
            suite.close();
            database.close();
        } catch (RuntimeException failure) {
            // Closing anyway. A failure while tidying up must not stop the window going.
        }
        dispose();
        System.exit(0);
    }

    /**
     * One sidebar row.
     *
     * <p>A panel rather than a button, because a button brings its own border, focus
     * ring and padding, all of which make a list of eleven look like a form.
     */
    private static final class NavRow extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JLabel label;
        private boolean selected;
        private boolean hovered;

        NavRow(String key, String text) {
            super();
            setOpaque(false);
            setLayout(new BorderLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            // Room on the left for the accent bar that marks the current screen.
            setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label = new JLabel(text);
            label.setFont(Theme.Type.sidebar());
            add(label, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        void setSelected(boolean value) {
            this.selected = value;
            label.setFont(value ? Theme.Type.sidebarSelected() : Theme.Type.sidebar());
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D pen = (Graphics2D) graphics.create();
            pen.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected || hovered) {
                pen.setColor(selected ? Theme.current().sidebarSelected()
                        : Theme.current().sidebar().brighter());
                pen.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            if (selected) {
                // A slim accent bar on the left, rather than filling the row. It marks
                // the current screen without the heaviness of a filled button, which is
                // the difference between a navigation rail and a stack of buttons.
                pen.setColor(Theme.current().accent());
                pen.fillRoundRect(0, 6, 4, getHeight() - 12, 4, 4);
            }
            pen.dispose();
            super.paintComponent(graphics);
        }

        @Override
        public void paint(Graphics graphics) {
            label.setForeground(selected ? Theme.current().text()
                    : Theme.current().sidebarText());
            super.paint(graphics);
        }
    }

    /** Finds a container's ancestor of a given type, for panels that need the window. */
    static Container ancestorOf(Component component) {
        return component == null ? null : javax.swing.SwingUtilities.getWindowAncestor(component);
    }
}
