package com.eventsuite.ui;

import com.eventsuite.core.Event;
import com.eventsuite.service.EventSuite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.sql.SQLException;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

/**
 * The behaviour every screen shares: know which event it is showing, and reload.
 *
 * <p>Panels are built once and refreshed, never rebuilt. A dashboard that rebuilds
 * itself every few seconds throws away the scroll position, the selected row and
 * whatever the user had typed into a field, which makes a live screen unusable. So
 * the structure is built in {@link #build()} and only the data changes in
 * {@link #reload()}.
 *
 * <p>Reads happen off the event thread. Hitting the database on the event thread
 * freezes the whole window for as long as the query takes, and a dashboard that
 * refreshes every few seconds would make it unusable on a busy event. The worker
 * hands the result back and the panel redraws, so a slow read slows the refresh rather
 * than the application.
 */
public abstract class BasePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * The application's operations.
     *
     * <p>Marked transient because a panel is not meant to be serialised and the
     * service holds a live database connection, which cannot be written to disk.
     */
    private final transient EventSuite suite;
    private String eventId = "";
    private JLabel subtitle;
    private JPanel content;
    private boolean loading;

    /** Set as the window closes, so a read in flight is abandoned rather than retried. */
    private volatile boolean closing;

    protected BasePanel(EventSuite suite) {
        this.suite = suite;
        // Called from the constructor so a subclass does not have to remember to. The
        // warning this raises is the price of every Swing panel doing the same thing,
        // and the alternative is a constructor every subclass has to get right.
        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.current().page());
        setBorder(javax.swing.BorderFactory.createEmptyBorder(22, 24, 22, 24));
    }

    public EventSuite suite() {
        return suite;
    }

    /**
     * The actions this screen contributes to the command palette.
     *
     * <p>Default is nothing, because most screens do not need to offer an action from
     * everywhere. Panels that do override this and return the things that can be done
     * from the keyboard rather than by reaching for a button.
     */
    public java.util.List<CommandPalette.Action> paletteActions() {
        return java.util.List.of();
    }

    /** Which event this panel is showing. Empty means the whole system. */
    public String getEventId() {
        return eventId;
    }

    /**
     * Points the panel at an event.
     *
     * <p>Rebuilds only when the event actually changes, so the top-level dashboard does
     * not tear itself down every time the selector is touched.
     */
    public void setEventId(String newEventId) {
        String target = newEventId == null ? "" : newEventId;
        if (target.equals(eventId) && content != null) {
            return;
        }
        eventId = target;
        build();
    }

    /** The event this panel is showing, or null. */
    public Event currentEvent() {
        try {
            return eventId.isEmpty() ? null : suite.events().findById(eventId);
        } catch (SQLException unreadable) {
            return null;
        }
    }

    /**
     * Builds the screen. Called once, and again when the event changes.
     *
     * <p>Subclasses put their layout into {@link #screen(String, String, java.awt.Component)}
     * rather than filling this panel themselves, so the header and padding stay the same
     * everywhere.
     */
    protected abstract void build();

    /**
     * Rebuilds the data in the panel.
     *
     * <p>Called by the refresh timer and after anything that changes the numbers. The
     * default does nothing, for panels whose content does not change on its own.
     */
    protected void reload() {
        // Overridden by the panels that show figures.
    }

    /**
     * Stops this panel accepting further refreshes.
     *
     * <p>Called as the window closes. A read that is already running would otherwise
     * finish against a connection that is closing underneath it, which the database
     * reports as an error nobody can act on.
     */
    public void stopRefreshing() {
        closing = true;
    }

    /** Whether the window is closing and refreshes should be refused. */
    public boolean isClosing() {
        return closing;
    }

    /**
     * Runs something that reads or writes the database, and puts any failure on screen.
     *
     * <p>This replaces the try/catch that every screen otherwise repeats in front of
     * every operation. Forty-five copies of the same four lines is forty-five places for
     * the handling to be inconsistent, and the inconsistency is always a different
     * wording for the same failure or, worse, a swallowed exception nobody sees.
     *
     * <p>The message names what was being attempted, because "could not load data" on
     * a screen with nine of them does not tell anybody which one failed.
     *
     * @param title what was being attempted, shown as the dialog's heading
     */
    protected void guard(String title, ThrowingAction action) {
        try {
            action.run();
        } catch (Exception failure) {
            Ui.dialog(this, title, failure.getMessage());
        }
    }

    /** Work that reads or writes the database and may fail. */
    @FunctionalInterface
    public interface ThrowingAction {
        void run() throws Exception;
    }

    /** Loads a panel's data without blocking the window. */
    public void refreshInBackground() {
        if (closing) {
            return;
        }
        if (loading) {
            // A slow read must not queue up a second refresh behind it. The next timer
            // tick will pick up whatever changed in the meantime.
            return;
        }
        loading = true;
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (closing) {
                    return null;
                }
                // The same lock the write transactions take, so a refresh cannot read
                // a sale half way through and show a ticket with no money against it.
                suite.getReadLock().lock();
                try {
                    reload();
                } catch (RuntimeException failure) {
                    // Deliberately swallowed. The store call that failed has already
                    // narrowed the problem, and an exception escaping a Swing worker
                    // would print to a console nobody is watching. The screen keeps
                    // showing the last figures it managed to read, which is more useful
                    // than a stack trace over the top of them.
                } finally {
                    suite.getReadLock().unlock();
                }
                return null;
            }

            @Override
            protected void done() {
                loading = false;
            }
        };
        worker.execute();
    }

    /**
     * Puts a titled screen into the panel.
     *
     * <p>The header carries the title, an explanatory line and a place for the panel's
     * own summary. Every screen gets the same shape, which is what makes moving
     * between them feel like moving between parts of one thing.
     */
    protected JPanel screen(String title, String explanation, java.awt.Component body) {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(Theme.current().page());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel header = new JPanel();
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);

        JLabel titleLabel = Ui.screenTitle(title);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(titleLabel);

        JLabel explanationLabel = Ui.muted(explanation);
        explanationLabel.setAlignmentX(LEFT_ALIGNMENT);
        explanationLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 0, 0, 0));
        header.add(explanationLabel);

        subtitle = Ui.muted("");
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 0, 0, 0));
        subtitle.setVisible(false);
        header.add(subtitle);

        panel.add(header, BorderLayout.NORTH);
        content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(Ui.scroll(body), BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        install(panel);
        return panel;
    }

    /**
     * Puts a screen with no scrolling body into the panel.
     *
     * <p>For screens that manage their own scroll — a dashboard of tiles that fits the
     * window, or one with a fixed table whose header must not scroll away.
     */
    protected JPanel fixedScreen(String title, String explanation, java.awt.Component body) {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(Theme.current().page());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel header = new JPanel();
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);

        JLabel titleLabel = Ui.screenTitle(title);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(titleLabel);

        JLabel explanationLabel = Ui.muted(explanation);
        explanationLabel.setAlignmentX(LEFT_ALIGNMENT);
        explanationLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 0, 0, 0));
        header.add(explanationLabel);

        subtitle = Ui.muted("");
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 0, 0, 0));
        subtitle.setVisible(false);
        header.add(subtitle);

        panel.add(header, BorderLayout.NORTH);
        content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(body, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        install(panel);
        return panel;
    }

    /**
     * Makes a freshly built screen this panel's contents.
     *
     * <p>Removes whatever was there first, which is what lets a panel rebuild itself
     * when the selected event changes without the old screen showing through.
     */
    private void install(JPanel panel) {
        removeAll();
        add(panel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** Sets the line under the screen title. Empty hides it. */
    protected void setSubtitle(String text) {
        if (subtitle == null) {
            return;
        }
        subtitle.setText(text == null ? "" : text);
        subtitle.setVisible(text != null && !text.isBlank());
    }

    /**
     * Replaces the body of the screen without rebuilding the header.
     *
     * <p>Used by the panels that rebuild their contents on a refresh, so the title and
     * the scroll position survive.
     */
    protected void replaceBody(java.awt.Component body) {
        if (content == null) {
            return;
        }
        content.removeAll();
        content.add(Ui.scroll(body), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    /** The green or amber used for a figure that should be watched. */
    protected Color healthColour(boolean problem) {
        return problem ? Theme.current().warning() : Theme.current().success();
    }

    /** A table built with the application's own styling, and no empty row height oddity. */
    protected javax.swing.JTable tableOf(javax.swing.table.TableModel model) {
        javax.swing.JTable table = Ui.table(model);
        table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        return table;
    }
}
