package com.eventsuite;

import com.eventsuite.data.Database;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ui.MainWindow;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Starts the application.
 *
 * <p>One file, one window, no server. The database is opened, the schema is created if
 * it is not there, a first run is seeded so the screens have something to show, and the
 * window opens.
 *
 * <p>The connection is deliberately not closed when this method returns. The window
 * outlives {@code main}, and its refresh timer keeps reading the database every few
 * seconds for as long as the window is open. Closing the connection here — which a
 * try-with-resources block would do, and which looks like good hygiene — puts the
 * window on a dead connection within seconds of opening, and every screen then fails
 * silently. The window owns the connection and closes it when it closes.
 *
 * <p>For the same reason the method does not call {@code System.exit}. A Swing
 * application runs until its window is closed, and that is what should happen.
 *
 * <p>A failure to open the database is reported in plain words on the terminal rather
 * than as a stack trace. The most likely cause is the file being locked by another copy
 * of the application, and a stack trace does not say that.
 */
public final class App {

    /** Where the database lives, unless a different file is named on the command line. */
    static final String DATABASE_NAME = "events.db";

    private App() {
    }

    public static void main(String[] args) {
        configureLookAndFeel();

        Path file = Path.of(args.length > 0 ? args[0] : DATABASE_NAME);

        Database database = new Database(file);
        try {
            Connection connection = database.openReady();
            EventSuite suite = new EventSuite(connection);

            // No sample data is created. What is in the database is what the user put
            // there, and the first thing they see is an empty catalogue with a form to
            // fill in, not somebody else's events.
            MainWindow window = new MainWindow(suite, database);
            String firstEventId = firstEventId(connection);
            if (!firstEventId.isEmpty()) {
                window.selectEvent(firstEventId);
            }
            window.setVisible(true);
        } catch (SQLException failure) {
            report("Could not open " + file.toAbsolutePath(), failure);
        } catch (java.io.IOException failure) {
            report("Could not create the folder for " + file.toAbsolutePath(), failure);
        }
    }

    /** The first event in the catalogue, or empty when the user has not created one. */
    private static String firstEventId(Connection connection) {
        try {
            var events = new com.eventsuite.data.EventStore(connection).findAll();
            return events.isEmpty() ? "" : events.get(0).getId();
        } catch (SQLException unreadable) {
            return "";
        }
    }

    /**
     * Sets the cross-cutting Swing defaults before any window exists.
     *
     * <p>These belong in {@code main} rather than in a screen, because they change how
     * everything renders and setting them later would repaint controls that have
     * already been drawn differently. Anti-aliased text, no bevelled focus rings, and
     * smooth scrolling are the three things that most determine whether a Swing
     * application reads as modern or as something from 2003.
     */
    private static void configureLookAndFeel() {
        // Text must be anti-aliased or every label looks crunchy on a dark theme.
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        javax.swing.UIManager.put("swing.plaf.metal.controlFont", null);

        // A cleaner default: no focus rings, and scrollbars that scroll by a page
        // rather than by a pixel, which is what makes long lists usable.
        javax.swing.UIManager.put("Button.focusedView", Boolean.FALSE);
        javax.swing.UIManager.put("ScrollBar.thumbFocus", Boolean.FALSE);
        javax.swing.UIManager.put("ScrollBar.width", 10);
        javax.swing.UIManager.put("SplitPane.dividerSize", 1);
        javax.swing.UIManager.put("SplitPaneDivider.border",
                javax.swing.BorderFactory.createEmptyBorder());
        javax.swing.UIManager.put("TextComponent.mouseWheelSmoothScrolling", Boolean.TRUE);

        try {
            // The system look and feel draws tables, menus and dialogs with the
            // platform's own chrome. Metal, the default, draws everything as though it
            // were 1999, and nothing a screen can do undoes that on its own.
            javax.swing.UIManager.setLookAndFeel(
                    javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception cannotUseSystemLook) {
            // Fall back to the default. It is uglier, but it is not a reason to stop
            // the application from opening.
        }
    }

    /**
     * Explains a start-up failure.
     *
     * <p>The locked-file case is called out specifically, because it is the one that
     * actually happens and the one a stack trace gives no hint about.
     */
    private static void report(String what, Exception failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        StringBuilder explanation = new StringBuilder();
        explanation.append(what).append(".\n\n");
        if (message.contains("lock") || message.contains("Lock")) {
            explanation.append("Another copy of the program is already using that file.")
                    .append(" Close it and try again, or name a different file:\n\n")
                    .append("    ./run.sh other-events.db\n");
        } else {
            explanation.append(message).append('\n');
        }
        explanation.append("\nIf the file is unreadable it may be in use by something else,")
                .append(" or you may not have write permission in this folder.");
        System.err.println(explanation);
        System.err.println("Details: " + failure.getClass().getName() + ": " + message);
        System.exit(1);
    }
}
