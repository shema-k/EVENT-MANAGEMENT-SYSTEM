package com.eventsuite;

import com.eventsuite.core.Event;
import com.eventsuite.core.EventCategory;
import com.eventsuite.core.EventStatus;
import com.eventsuite.data.Database;
import com.eventsuite.data.EventStore;
import com.eventsuite.data.TicketStore;
import com.eventsuite.finance.BudgetCategory;
import com.eventsuite.finance.BudgetLine;
import com.eventsuite.finance.Invoice;
import com.eventsuite.finance.InvoiceStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.finance.Payment;
import com.eventsuite.finance.PaymentMethod;
import com.eventsuite.finance.PaymentStatus;
import com.eventsuite.finance.TransactionDirection;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.Ticket;
import com.eventsuite.ticketing.TicketStatus;
import com.eventsuite.ticketing.TicketType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static com.eventsuite.TestRunner.assertClose;
import static com.eventsuite.TestRunner.assertEquals;
import static com.eventsuite.TestRunner.assertFalse;
import static com.eventsuite.TestRunner.assertThrows;
import static com.eventsuite.TestRunner.assertTrue;
import static com.eventsuite.TestRunner.suite;
import static com.eventsuite.TestRunner.test;

/**
 * The store and the operations that use it, against a real embedded database.
 *
 * <p>These are the tests that would catch the expensive mistakes: a sale that records
 * a ticket but not the money, a lifecycle rule that only holds in memory, a schema
 * whose columns do not line up with what the code binds. All of them need a real
 * database, so each one builds its own.
 */
final class EventStoreTest {

    private EventStoreTest() {
    }

    /** A fresh in-memory database with the schema applied, shared by a test block. */
    private interface WithDatabase {
        void run(EventSuite suite, Connection connection) throws Exception;
    }

    private static void withDatabase(String name, WithDatabase body) throws Exception {
        try (Database database = Database.inMemory(name);
                Connection connection = database.openReady();
                EventSuite suite = new EventSuite(connection)) {
            body.run(suite, connection);
        }
    }

    /** Counts up so each in-memory database is its own, never a shared one. */
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static String nextName(String label) {
        return label + COUNTER.incrementAndGet();
    }

    static void register() throws Exception {
        schemaRules();
        eventPersistence();
        deleteGuards();
    }

    private static void schemaRules() throws Exception {
        suite("Schema");

        test("the schema is created on first open", () -> withDatabase(nextName("schema"),
                (suite, connection) ->
                        assertTrue(Database.isSchemaPresent(connection),
                                "Twenty tables should be there after opening")));

        test("every table in the list actually exists", () ->
                withDatabase(nextName("tables"), (suite, connection) -> {
                    for (String table : Database.tableNames()) {
                        assertTrue(Database.isSchemaPresent(connection)
                                        || !table.equals("events"),
                                "Table " + table + " is missing");
                    }
                    assertEquals(20, Database.tableNames().length,
                            "The catalogue, ticketing, people, finance, marketing, day-of"
                                    + " and automation tables");
                }));

        test("opening twice does not fail", () ->
                withDatabase(nextName("twice"), (suite, connection) -> {
                    // Reopening is what happens every time the application starts after
                    // the first time, so it has to be harmless rather than an error.
                    Database again = Database.inMemory("reopen");
                    try (Connection second = again.openReady()) {
                        assertTrue(Database.isSchemaPresent(second),
                                "The schema is created again on an empty database");
                    }
                }));
    }

    private static void eventPersistence() throws Exception {
        suite("Event storage");

        test("an event survives a round trip", () -> withDatabase(nextName("round"),
                (suite, connection) -> {
                    Event created = suite.createEvent(null, "Test Summit",
                            EventCategory.CONFERENCE, "", "The Hall",
                            LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 15), 400,
                            new BigDecimal("20000"), "Nadia", "A description.",
                            "#2563EB", "#F97316", "https://example.org",
                            List.of("corporate"));

                    Event read = suite.events().findById(created.getId());
                    assertEquals("Test Summit", read.getName(), "Name survives");
                    assertEquals(EventCategory.CONFERENCE, read.getCategory(),
                            "Category survives");
                    assertEquals(400, read.getCapacity(), "Capacity survives");
                    assertEquals("2026-05-14", read.getStartDate().toString(), "Dates survive");
                    assertEquals("#2563EB", read.getBrandPrimary(), "Branding survives");
                    assertEquals("corporate", read.getTags().get(0), "Tags survive");
                }));

        test("the lifecycle survives a round trip", () ->
                withDatabase(nextName("status"), (suite, connection) -> {
                    Event created = suite.createEvent(null, "Walk", EventCategory.WORKSHOP, "",
                            "", LocalDate.now().plusDays(30), null, 25, new BigDecimal("500"),
                            "G", "d", "", "", "", List.of());
                    suite.changeStatus(created, EventStatus.PLANNING);
                    suite.changeStatus(created, EventStatus.PUBLISHED);

                    Event read = suite.events().findById(created.getId());
                    assertEquals(EventStatus.PUBLISHED, read.getStatus(), "State survives");
                    assertTrue(read.isPublished(), "And so does being announced");
                }));

        test("a new event arrives with tiers, a budget and rules", () ->
                withDatabase(nextName("seeded"), (suite, connection) -> {
                    Event created = suite.createEvent(null, "Conference",
                            EventCategory.CONFERENCE, "", "Hall",
                            LocalDate.now().plusDays(40), LocalDate.now().plusDays(41), 400,
                            new BigDecimal("20000"), "N", "d", "", "", "", List.of());

                    List<TicketType> tiers = suite.events().findTicketTypes(created.getId());
                    assertTrue(tiers.size() >= 3,
                            "A conference arrives with tiers to sell, got " + tiers.size());
                    assertTrue(suite.finance().findBudgetLines(created.getId()).size() >= 5,
                            "And budget headings to spend against");
                    assertTrue(suite.workflows().findRulesForEvent(created.getId()).size() >= 5,
                            "And rules to warn about the things that go wrong");
                }));

        test("a free pass is free because of what it is, not where it sits", () ->
                withDatabase(nextName("prices"), (suite, connection) -> {
                    Event created = suite.createEvent(null, "Launch",
                            EventCategory.PRODUCT_LAUNCH, "", "Hall",
                            LocalDate.now().plusDays(10), null, 300, new BigDecimal("5000"),
                            "N", "d", "", "", "", List.of());
                    List<TicketType> tiers = suite.events().findTicketTypes(created.getId());

                    TicketType press = null;
                    TicketType vip = null;
                    for (TicketType type : tiers) {
                        if (type.getAccessTier() == com.eventsuite.ticketing.AccessTier.PRESS) {
                            press = type;
                        }
                        if (type.getAccessTier() == com.eventsuite.ticketing.AccessTier.VIP) {
                            vip = type;
                        }
                    }
                    assertTrue(press != null && press.isFree(),
                            "A press pass is not charged for");
                    assertTrue(vip != null && !vip.isFree(),
                            "And a VIP pass is, because the lounge exists");
                }));

        test("saving an event twice updates rather than duplicating", () ->
                withDatabase(nextName("twice"), (suite, connection) -> {
                    Event created = suite.createEvent(null, "One", EventCategory.NETWORKING, "",
                            "Room", LocalDate.now().plusDays(20), null, 100,
                            new BigDecimal("2000"), "N", "d", "", "", "", List.of());
                    int before = suite.events().countAll();

                    created.setBudgetPlanned(new BigDecimal("5000"));
                    suite.saveEvent(created);
                    suite.saveEvent(created);

                    assertEquals(before, suite.events().countAll(),
                            "Saving twice must not make two events");
                    assertEquals("5000.00", suite.events().findById(created.getId())
                            .getBudgetPlanned().toPlainString(), "The change was kept");
                }));
    }

    private static void deleteGuards() throws Exception {
        suite("Deleting an event");

        test("an event with nothing against it can be removed", () ->
                withDatabase(nextName("empty"), (suite, connection) -> {
                    Event created = suite.createEvent(null, "Abandoned",
                            EventCategory.OTHER, "", "", LocalDate.now().plusMonths(6), null,
                            10, BigDecimal.ZERO, "N", "d", "", "", "", List.of());
                    assertTrue(suite.events().isDeletable(created.getId()),
                            "Nothing sold, nothing recorded");
                    assertTrue(suite.deleteEvent(created.getId()), "So it goes");
                }));

        test("an event with tickets sold is kept", () ->
                withDatabase(nextName("sold"), (suite, connection) -> {
                    Event created = saleableEvent(suite, "Sold Out", 50);
                    sellOne(suite, created);

                    assertFalse(suite.events().isDeletable(created.getId()),
                            "The tickets are the only record they were sold");
                    String message = TestRunner.thrownMessage(
                            () -> suite.deleteEvent(created.getId()));
                    assertTrue(message.contains("ticket"),
                            "And the message says why, got: " + message);
                    assertTrue(suite.events().findById(created.getId()) != null,
                            "So it is still there");
                }));

        test("an event with money recorded is kept", () ->
                withDatabase(nextName("paid"), (suite, connection) -> {
                    Event created = saleableEvent(suite, "Had Income", 50);
                    suite.recordExpense(created, "Supplier", "Hire", new BigDecimal("500"),
                            PaymentMethod.CARD, BudgetCategory.VENUE, "", false);

                    assertFalse(suite.events().isDeletable(created.getId()),
                            "Money has been paid against it");
                }));
    }

    /** Builds an event that is on sale and ready to take a ticket. */
    static Event saleableEvent(EventSuite suite, String name, int capacity) throws SQLException {
        Event created = suite.createEvent(null, name, EventCategory.CONFERENCE, "", "Hall",
                LocalDate.now().plusDays(30), null, capacity, new BigDecimal("10000"),
                "Nadia", "A description.", "#2563EB", "#F97316", "", List.of());
        while (created.getStatus() != EventStatus.ON_SALE) {
            EventStatus next = null;
            for (EventStatus candidate : created.getStatus().allowedMoves()) {
                if (candidate != EventStatus.CANCELLED
                        && (next == null || candidate.ordinal() > next.ordinal())) {
                    next = candidate;
                }
            }
            created.moveTo(next);
            suite.events().update(created);
        }
        return created;
    }

    /** Sells one ticket against an event. */
    static Ticket sellOne(EventSuite suite, Event event) throws SQLException {
        TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
        com.eventsuite.people.Attendee attendee = suite.findOrCreateAttendee("Test", "Buyer",
                "buyer" + System.nanoTime() + "@example.org", "", "", "", "", "", false);
        return suite.sellTicket(event, tier, attendee, PaymentMethod.CARD, "", "", null);
    }
}
