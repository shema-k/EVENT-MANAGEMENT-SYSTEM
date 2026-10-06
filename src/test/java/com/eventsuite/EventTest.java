package com.eventsuite;

import com.eventsuite.core.Event;
import com.eventsuite.core.EventCategory;
import com.eventsuite.core.EventScale;
import com.eventsuite.core.EventStatus;
import com.eventsuite.core.Venue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static com.eventsuite.TestRunner.assertEquals;
import static com.eventsuite.TestRunner.assertFalse;
import static com.eventsuite.TestRunner.assertThrows;
import static com.eventsuite.TestRunner.assertTrue;
import static com.eventsuite.TestRunner.suite;
import static com.eventsuite.TestRunner.test;

/** The catalogue: categories, the lifecycle, and the event record itself. */
final class EventTest {

    private EventTest() {
    }

    static Event anEvent(String id, String name, EventCategory category, LocalDate start,
                         LocalDate end, int capacity) {
        return new Event(id, name, category, "", "Kampala ICC",
                start, end, LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(17, 0),
                "Nadia", "A description of the event.", "#2563EB", "#F97316",
                "https://example.org", capacity, List.of("corporate"),
                EventStatus.DRAFT, new BigDecimal("20000"), "");
    }

    static void register() {
        categoryRules();
        statusRules();
        scaleRules();
        eventRules();
    }

    private static void categoryRules() {
        suite("Event categories");

        test("every category suggests tiers", () -> {
            for (EventCategory category : EventCategory.values()) {
                assertTrue(category.getSuggestedTicketTypes().length > 0,
                        category + " suggests no ticket types at all");
            }
        });

        test("every category suggests a capacity", () -> {
            for (EventCategory category : EventCategory.values()) {
                assertTrue(category.getSuggestedCapacity() > 0,
                        category + " has no suggested capacity");
            }
        });

        test("a webinar needs no venue", () ->
                assertFalse(EventCategory.WEBINAR.needsVenue(),
                        "A webinar has no room, and asking for one is noise"));

        test("a conference tracks speakers but a festival does not", () -> {
            assertTrue(EventCategory.CONFERENCE.tracksSpeakers(),
                    "A conference is mostly a speaker list");
            assertFalse(EventCategory.FESTIVAL.tracksSpeakers(),
                    "A festival is mostly a capacity and a stage budget");
        });

        test("only festivals and exhibitions multiply capacity by days", () -> {
            assertTrue(EventCategory.FESTIVAL.multipliesCapacityByDays(),
                    "A festival sells day passes separately");
            assertFalse(EventCategory.CONFERENCE.multipliesCapacityByDays(),
                    "A conference's capacity is simultaneous attendance");
        });

        test("a category name is recognised however it is written", () -> {
            assertEquals(EventCategory.PRODUCT_LAUNCH, EventCategory.fromLabel("product launch"),
                    "A space in the name must not defeat the lookup");
            assertEquals(EventCategory.PRODUCT_LAUNCH, EventCategory.fromLabel("PRODUCT_LAUNCH"),
                    "The stored form must also work");
            assertEquals(EventCategory.OTHER, EventCategory.fromLabel("interpretive dance"),
                    "Anything unrecognised falls back rather than failing");
            assertEquals(EventCategory.OTHER, EventCategory.fromLabel(null),
                    "A null category cannot crash the catalogue");
        });
    }

    private static void statusRules() {
        suite("Event lifecycle");

        test("an event walks forward one step at a time", () -> {
            assertTrue(EventStatus.DRAFT.canMoveTo(EventStatus.PLANNING),
                    "A draft becomes a plan");
            assertFalse(EventStatus.DRAFT.canMoveTo(EventStatus.ON_SALE),
                    "A draft must be agreed before it takes money");
        });

        test("an event can be paused and withdrawn, but never un-sold", () -> {
            assertTrue(EventStatus.PUBLISHED.canMoveTo(EventStatus.PLANNING),
                    "Withdrawing an announcement before selling is fine");
            assertTrue(EventStatus.ON_SALE.canMoveTo(EventStatus.PUBLISHED),
                    "Pausing sales while tickets are already out is normal, and the"
                            + " tickets already sold stay valid");
            assertFalse(EventStatus.ON_SALE.canMoveTo(EventStatus.PLANNING),
                    "Going all the way back to planning would hide the fact that money"
                            + " has been taken");
            assertFalse(EventStatus.ON_SALE.canMoveTo(EventStatus.DRAFT),
                    "And a draft is by definition before anything was agreed");
        });

        test("cancellation is always available", () -> {
            assertTrue(EventStatus.ON_SALE.canMoveTo(EventStatus.CANCELLED),
                    "An event can be called off at any point");
            assertTrue(EventStatus.IN_PROGRESS.canMoveTo(EventStatus.CANCELLED),
                    "Refusing to cancel a live event would be absurd");
        });

        test("a cancelled event cannot be revived by a status change", () ->
                assertFalse(EventStatus.CANCELLED.canMoveTo(EventStatus.PLANNING),
                        "A cancellation is not a state to come back from"));

        test("a completed event is finished", () -> {
            assertFalse(EventStatus.COMPLETED.canMoveTo(EventStatus.ON_SALE),
                    "A finished event cannot go back on sale");
            assertTrue(EventStatus.COMPLETED.isSettled(),
                    "It is ready for reconciliation");
        });

        test("only on sale takes money", () -> {
            assertTrue(EventStatus.ON_SALE.acceptsSales(), "Sales happen here");
            assertFalse(EventStatus.PUBLISHED.acceptsSales(),
                    "Announced is not the same as selling");
            assertFalse(EventStatus.SOLD_OUT.acceptsSales(),
                    "Sold out means no further sales");
        });

        test("a state does not move to itself", () ->
                assertFalse(EventStatus.ON_SALE.canMoveTo(EventStatus.ON_SALE),
                        "A no-op is not a transition"));

        test("the allowed moves are exactly the legal ones", () -> {
            for (EventStatus from : EventStatus.values()) {
                for (EventStatus to : EventStatus.values()) {
                    if (from.allowedMoves().contains(to)) {
                        assertTrue(from.canMoveTo(to),
                                from + " lists " + to + " but refuses it");
                    }
                }
            }
        });
    }

    private static void scaleRules() {
        suite("Event scale");

        test("scale is derived from capacity, not chosen", () -> {
            assertEquals(EventScale.MICRO, EventScale.of(20), "Twenty people");
            assertEquals(EventScale.SMALL, EventScale.of(100), "A hundred people");
            assertEquals(EventScale.MEDIUM, EventScale.of(500), "Five hundred people");
            assertEquals(EventScale.LARGE, EventScale.of(2000), "Two thousand people");
            assertEquals(EventScale.MAJOR, EventScale.of(9000), "Nine thousand people");
        });

        test("a festival of this size needs a command centre", () ->
                assertTrue(EventScale.of(4000).needsCommandCentre(),
                        "A few thousand people need a control room"));

        test("a workshop does not", () ->
                assertFalse(EventScale.of(25).needsCommandCentre(),
                        "Twenty-five people are manageable"));

        test("only a major event needs staggered entry", () -> {
            assertTrue(EventScale.MAJOR.needsStaggeredEntry(),
                    "Nine thousand people cannot arrive in one minute");
            assertFalse(EventScale.LARGE.needsStaggeredEntry(),
                    "Two thousand can manage a single door");
        });
    }

    private static void eventRules() {
        suite("Event record");

        test("an unnamed event is named rather than blank", () ->
                assertEquals(Event.UNNAMED, anEvent("e1", "  ", EventCategory.CONFERENCE,
                        LocalDate.now(), LocalDate.now(), 100).getName(),
                        "A blank name is still a record with a name"));

        test("capacity must be at least one", () -> {
            String message = TestRunner.thrownMessage(() -> anEvent("e1", "X",
                    EventCategory.CONFERENCE, LocalDate.now(), LocalDate.now(), 0));
            assertTrue(message.contains("Capacity"),
                    "The message should name the field, got: " + message);
        });

        test("a reversed date range collapses to a single day", () -> {
            Event event = anEvent("e1", "X", EventCategory.CONFERENCE,
                    LocalDate.of(2026, 4, 16), LocalDate.of(2026, 4, 14), 100);
            assertEquals(1, event.getDurationDays(),
                    "Typing the two dates in the wrong order is a slip, not a rejection");
        });

        test("a single-day event has one day, not zero", () -> {
            Event event = anEvent("e1", "X", EventCategory.CONFERENCE,
                    LocalDate.of(2026, 4, 14), null, 100);
            assertEquals(1, event.getDurationDays(), "Never zero days");
        });

        test("a festival's sellable capacity is capacity times days", () -> {
            Event festival = anEvent("e1", "Fest", EventCategory.FESTIVAL,
                    LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 16), 5000);
            assertEquals(15000, festival.getSellableCapacity(),
                    "Three days of day passes are three times the room");
        });

        test("a conference's sellable capacity is its capacity", () -> {
            Event conference = anEvent("e1", "Conf", EventCategory.CONFERENCE,
                    LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 16), 400);
            assertEquals(400, conference.getSellableCapacity(),
                    "Three days of talks still hold four hundred at once");
        });

        test("a three-digit brand colour is expanded", () -> {
            Event event = new Event("e1", "X", EventCategory.CONFERENCE, "", "",
                    LocalDate.now(), null, null, null, null, "", "",
                    "#abc", "", "", 10, List.of(), EventStatus.DRAFT, BigDecimal.ZERO, "");
            assertEquals("#AABBCC", event.getBrandPrimary(),
                    "#abc is a common way of writing a colour and must be expanded");
        });

        test("a colour without its hash is accepted", () -> {
            Event event = new Event("e1", "X", EventCategory.CONFERENCE, "", "",
                    LocalDate.now(), null, null, null, null, "", "",
                    "2563eb", "", "", 10, List.of(), EventStatus.DRAFT, BigDecimal.ZERO, "");
            assertEquals("#2563EB", event.getBrandPrimary(),
                    "The hash is presentational and is added for painting with");
        });

        test("an unusable brand colour is dropped, not fatal", () -> {
            Event event = new Event("e1", "X", EventCategory.CONFERENCE, "", "",
                    LocalDate.now(), null, null, null, null, "", "",
                    "not-a-colour", "", "", 10, List.of(), EventStatus.DRAFT,
                    BigDecimal.ZERO, "");
            assertEquals("", event.getBrandPrimary(),
                    "Branding must never be the reason an event cannot be saved");
        });

        test("moving through the lifecycle records where it got to", () -> {
            Event event = anEvent("e1", "Summit", EventCategory.CONFERENCE,
                    LocalDate.now().plusDays(30), LocalDate.now().plusDays(31), 400);
            assertFalse(event.isPublished(), "A draft is not on the website");
            event.moveTo(EventStatus.PLANNING);
            event.moveTo(EventStatus.PUBLISHED);
            assertTrue(event.isPublished(), "Published means announced");
            event.moveTo(EventStatus.ON_SALE);
            assertTrue(event.getStatus().acceptsSales(), "On sale takes money");
        });

        test("an illegal move is refused by name", () -> {
            Event event = anEvent("e1", "Summit", EventCategory.CONFERENCE,
                    LocalDate.now(), LocalDate.now(), 400);
            String message = TestRunner.thrownMessage(() -> event.moveTo(EventStatus.ON_SALE));
            assertTrue(message.contains("Summit"),
                    "The message should name the event, got: " + message);
        });

        test("days until the event counts down and then goes negative", () -> {
            Event event = anEvent("e1", "Soon", EventCategory.CONFERENCE,
                    LocalDate.now().plusDays(7), LocalDate.now().plusDays(7), 100);
            assertEquals(7L, event.getDaysUntil(LocalDate.now()), "A week away");
            assertEquals(-3L, event.getDaysUntil(LocalDate.now().plusDays(10)),
                    "Three days past, so the sign says which side of the day we are on");
        });

        test("the event knows when it is happening", () -> {
            LocalDate today = LocalDate.now();
            Event event = anEvent("e1", "Now", EventCategory.CONFERENCE, today, today, 100);
            // Stated as fixed moments rather than "now", because an event whose doors
            // open at half past eight is not running at seven in the morning, and a
            // test that depends on the time of day fails once a day.
            assertTrue(event.isHappeningAt(today.atTime(10, 0)),
                    "Ten in the morning on the day it runs");
            assertFalse(event.isHappeningAt(today.atTime(7, 0)),
                    "Before the doors open it is not happening yet");
            assertFalse(event.isHappeningAt(today.atTime(23, 0)),
                    "And after it ends it is over");
            assertFalse(event.isHappeningAt(today.plusDays(1).atTime(10, 0)),
                    "Two days from now it was over and done with");
        });

        test("publishing gaps are named, not counted", () -> {
            Event event = new Event("e1", "", EventCategory.CONFERENCE, "", "",
                    LocalDate.now(), null, null, null, null, "", "", "", "", "", 10,
                    List.of(), EventStatus.DRAFT, BigDecimal.ZERO, "");
            List<String> gaps = event.getPublishingGaps();
            assertTrue(gaps.contains("a name"), "It has no name");
            assertTrue(gaps.contains("an organiser"), "It has no organiser");
            assertFalse(event.isReadyToPublish(), "So it is not ready");
        });

        test("a webinar is ready without a venue", () -> {
            Event event = new Event("e1", "Online session", EventCategory.WEBINAR, "", "",
                    LocalDate.now().plusDays(3), null, null, null, null, "Grace",
                    "A short online session.", "", "", "", 500, List.of(),
                    EventStatus.DRAFT, BigDecimal.ZERO, "");
            assertTrue(event.isReadyToPublish(),
                    "Demanding a venue for a webinar would be asking for something"
                            + " that does not exist");
        });
    }
}
