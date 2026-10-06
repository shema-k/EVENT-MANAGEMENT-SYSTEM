package com.eventsuite.ui;

import com.eventsuite.service.EventSuite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;

/**
 * Renders every screen to a PNG without showing a window.
 *
 * <p>Two reasons this exists. It verifies that each screen actually builds and paints
 * — a layout exception on a screen nobody happens to visit is otherwise invisible
 * until somebody visits it — and it produces images that can be looked at without a
 * desktop to hand, which matters on a machine with no display.
 *
 * <p>The panels are put in an off-screen frame rather than built bare. A Swing
 * component that has never been in a window has not been laid out, and laying it out
 * is where most of what can go wrong happens. So this exercises the same path the
 * real window does and differs only in never becoming visible.
 */
public final class Shots {

    /** The screens to render, keyed by the file name they are written under. */
    private static final String[][] SCREENS = {
            {"01-dashboard", "Dashboard of the whole book of events."},
            {"02-catalogue", "Registered events, grouped by category."},
            {"03-ticketing", "Tiers, sales and the tickets issued."},
            {"04-attendees", "Who is coming, and who is waiting."},
            {"05-speakers", "The programme and what has been booked."},
            {"06-marketing", "Campaigns, costs and discount codes."},
            {"07-checkin", "The door, and every scan."},
            {"08-engagement", "What people did, and the footprint."},
            {"09-finance", "Takings, costs, budget and the ledger."},
            {"10-analytics", "Feedback, return on spend, reconciliation."},
            {"11-automation", "Rules and what they have done."},
    };

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 1000;

    private Shots() {
    }

    /**
     * Renders every screen for an event.
     *
     * @param directory where the images are written
     * @param eventId the event to show, or empty for the whole book
     * @param dark whether to render in the dark theme as well
     * @return how many images were written
     */
    public static int renderAll(Connection connection, EventSuite suite, Path directory,
                                String eventId, boolean dark) throws IOException, SQLException {
        Files.createDirectories(directory);
        Theme.setDark(dark);

        JFrame offscreen = new JFrame("render");
        offscreen.setSize(WIDTH, HEIGHT);
        offscreen.setUndecorated(true);

        Map<String, BasePanel> panels = buildPanels(suite);
        int written = 0;

        for (String[] screen : SCREENS) {
            BasePanel panel = panels.get(screen[0]);
            if (panel == null) {
                continue;
            }
            panel.setEventId(eventId);
            offscreen.setContentPane(panel);
            offscreen.addNotify();
            offscreen.validate();
            layout(panel);
            // Painted twice: the first pass resolves any remaining layout, the second
            // draws it. One pass can capture a chart mid-layout, which looks like a
            // screen that has silently lost its data.
            paint(panel, directory.resolve(screen[0] + ".png"));
            paint(panel, directory.resolve(screen[0] + ".png"));
            written++;
        }
        offscreen.dispose();
        Theme.setDark(false);
        return written;
    }

    private static Map<String, BasePanel> buildPanels(EventSuite suite) {
        Map<String, BasePanel> panels = new LinkedHashMap<>();
        panels.put("01-dashboard", new DashboardPanel(suite));
        panels.put("02-catalogue", new EventsPanel(suite));
        panels.put("03-ticketing", new TicketingPanel(suite));
        panels.put("04-attendees", new AttendeesPanel(suite));
        panels.put("05-speakers", new SpeakersPanel(suite));
        panels.put("06-marketing", new MarketingPanel(suite));
        panels.put("07-checkin", new CheckInPanel(suite));
        panels.put("08-engagement", new EngagementPanel(suite));
        panels.put("09-finance", new FinancePanel(suite));
        panels.put("10-analytics", new AnalyticsPanel(suite));
        panels.put("11-automation", new WorkflowsPanel(suite));
        return panels;
    }

    /**
     * Lays out a component and everything inside it.
     *
     * <p>{@code validate} on its own does not descend the whole tree in an off-screen
     * frame, so a chart deep inside a scrolling column is left at zero by zero and
     * paints nothing. Walking the tree explicitly is what makes a chart with data in it
     * actually appear in the image.
     */
    private static void layout(java.awt.Component component) {
        if (component instanceof java.awt.Container container) {
            container.doLayout();
            for (java.awt.Component child : container.getComponents()) {
                layout(child);
            }
        }
    }

    /** Paints a component into a PNG file. */
    private static void paint(JComponent component, Path file) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = image.createGraphics();
        pen.setColor(Theme.current().page());
        pen.fillRect(0, 0, WIDTH, HEIGHT);
        component.paint(pen);
        pen.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    /**
     * Checks that every screen builds without throwing.
     *
     * <p>Separate from rendering so a headless build can run it with no images written
     * at all. Returns the names of the screens that failed, empty when all passed.
     */
    public static java.util.List<String> verifyAll(Connection connection, EventSuite suite,
                                                   String eventId) {
        java.util.List<String> broken = new java.util.ArrayList<>();
        for (BasePanel panel : buildPanels(suite).values()) {
            try {
                panel.setEventId(eventId);
                panel.refreshInBackground();
                panel.setSize(new Dimension(WIDTH, HEIGHT));
                layout(panel);
            } catch (RuntimeException failure) {
                broken.add(panel.getClass().getSimpleName() + ": " + failure);
            }
        }
        return broken;
    }
}
