package com.eventsuite;

import com.eventsuite.data.Database;
import com.eventsuite.service.EventSuite;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Renders every screen to a file, so the interface can be checked without a display.
 *
 * <p>Useful in two ways that a screenshot is not. It proves each screen builds and
 * paints, which catches a layout exception on a screen nobody happens to visit; and
 * it produces images on a machine with no desktop at all, which is the situation a
 * build server is in.
 *
 * <p>Run it after a change and look at what comes out. A screen that has silently
 * lost its charts looks obviously wrong in a way that no assertion would notice.
 */
public final class Shots {

    private Shots() {
    }

    public static void main(String[] args) throws Exception {
        Path databaseFile = Path.of(args.length > 0 ? args[0] : App.DATABASE_NAME);
        Path output = Path.of(args.length > 1 ? args[1] : "screenshots");
        boolean dark = args.length > 2 && args[2].equalsIgnoreCase("dark");

        try (Database database = new Database(databaseFile);
                Connection connection = database.openReady();
                EventSuite suite = new EventSuite(connection)) {

            String eventId = firstEventId(connection);
            if (eventId.isEmpty()) {
                // Nothing is rendered as though it existed. With no events in the
                // database there is nothing to show a dashboard of, and inventing data
                // to draw one would make the check worth less.
                System.out.println("No events in the database, so there is nothing to"
                        + " render. Create an event first.");
            }

            int written = com.eventsuite.ui.Shots.renderAll(connection, suite, output, eventId, dark);
            System.out.println("Wrote " + written + " screens for "
                    + (eventId.isEmpty() ? "every event" : eventId)
                    + (dark ? " (dark)" : "") + " to " + output.toAbsolutePath());

            List<String> broken = com.eventsuite.ui.Shots.verifyAll(connection, suite, eventId);
            if (broken.isEmpty()) {
                System.out.println("Every screen built without error.");
            } else {
                System.out.println("Screens that failed to build:");
                for (String failure : broken) {
                    System.out.println("  " + failure);
                }
                System.exit(1);
            }
        }
        // Exited explicitly. A Swing toolkit thread is still alive once main returns,
        // so without this the tool would finish its work and then hang on screen.
        System.exit(0);
    }

    private static String firstEventId(Connection connection) throws SQLException {
        List<com.eventsuite.core.Event> events =
                new com.eventsuite.data.EventStore(connection).findAll();
        return events.isEmpty() ? "" : events.get(0).getId();
    }
}
