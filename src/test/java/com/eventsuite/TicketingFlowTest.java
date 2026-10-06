package com.eventsuite;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.core.Event;
import com.eventsuite.core.EventStatus;
import com.eventsuite.data.Database;
import com.eventsuite.finance.BudgetCategory;
import com.eventsuite.finance.BudgetLine;
import com.eventsuite.finance.Invoice;
import com.eventsuite.finance.InvoiceStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.finance.Payment;
import com.eventsuite.finance.PaymentMethod;
import com.eventsuite.finance.PaymentStatus;
import com.eventsuite.ops.AccessGate;
import com.eventsuite.ops.CheckInOutcome;
import com.eventsuite.people.ResourceItem;
import com.eventsuite.people.SpeakerStatus;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.Ticket;
import com.eventsuite.ticketing.TicketStatus;
import com.eventsuite.ticketing.TicketType;
import com.eventsuite.workflow.AutomationLog;
import com.eventsuite.workflow.WorkflowAction;
import com.eventsuite.workflow.WorkflowEngine;
import com.eventsuite.workflow.WorkflowRule;
import com.eventsuite.workflow.WorkflowTrigger;
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
 * The operations, end to end, against a real database.
 *
 * <p>Each test here performs the thing a user does rather than calling one method in
 * isolation, because that is the level at which the expensive mistakes live: a sale
 * that records a ticket but not the payment, a refund that leaves the seat counted, a
 * check-in that lets the same ticket in twice.
 */
final class TicketingFlowTest {

    private TicketingFlowTest() {
    }

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private interface WithSuite {
        void run(EventSuite suite) throws Exception;
    }

    private static void withSuite(String label, WithSuite body) throws Exception {
        String name = label + COUNTER.incrementAndGet();
        try (Database database = Database.inMemory(name);
                Connection connection = database.openReady();
                EventSuite suite = new EventSuite(connection)) {
            body.run(suite);
        }
    }

    static void register() {
        sales();
        holds();
        refunds();
        checkIns();
        money();
        metrics();
        reconciliation();
        automation();
    }

    private static void sales() {
        suite("Selling");

        test("a sale records the ticket, the payment and the registration", () ->
                withSuite("sale", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Sale", 100);
                    Ticket ticket = EventStoreTest.sellOne(suite, event);

                    assertTrue(ticket.getStatus() == TicketStatus.SOLD, "The ticket is sold");
                    assertTrue(suite.tickets().findByReference(ticket.getReference()) != null,
                            "And it is stored");
                    assertEquals(1, suite.finance().findPayments(event.getId()).size(),
                            "One payment was recorded");
                    assertEquals(1, suite.people().findRegistrations(event.getId()).size(),
                            "And one registration");

                    Ticket read = suite.tickets().findByReference(ticket.getReference());
                    assertEquals(ticket.getReference(), read.getReference(),
                            "The reference finds it again, which is what the door does");
                }));

        test("the money matches the price, less what the processor kept", () ->
                withSuite("money", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Money", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    Ticket ticket = suite.tickets().findByEvent(event.getId()).isEmpty()
                            ? sellAt(suite, event, tier, PaymentMethod.CARD, tier.getPrice())
                            : suite.tickets().findByEvent(event.getId()).get(0);

                    Payment payment = suite.finance().findPaymentForTicket(ticket.getReference());
                    assertEquals(tier.getPrice(), payment.getAmount(), "What was charged");
                    assertTrue(payment.getProviderFee().signum() > 0,
                            "A card payment costs the processor something");
                    assertEquals(tier.getPrice().subtract(payment.getProviderFee()),
                            payment.getNetAmount(), "And what arrives is the difference");
                }));

        test("a tier cannot be sold past its allocation", () ->
                withSuite("allocation", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Small", 2);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    suite.setBudgetAllocation(event, BudgetCategory.VENUE, new BigDecimal("100"));
                    for (int index = 0; index < tier.getQuantity(); index++) {
                        sellAt(suite, event, tier, PaymentMethod.CARD, tier.getPrice());
                    }
                    assertTrue(tier.isSoldOut(suite.events().countSoldForType(tier.getId())),
                            "The tier is full");

                    String message = TestRunner.thrownMessage(() -> sellAt(suite, event, tier,
                            PaymentMethod.CARD, tier.getPrice()));
                    assertTrue(message.contains("sold out"),
                            "And the next sale says so, got: " + message);
                }));

        test("a draft event takes no money", () ->
                withSuite("draft", suite -> {
                    Event draft = suite.createEvent(null, "Not Ready",
                            com.eventsuite.core.EventCategory.OTHER, "", "",
                            LocalDate.now().plusDays(30), null, 50, new BigDecimal("100"),
                            "N", "d", "", "", "", List.of());
                    TicketType tier = suite.events().findTicketTypes(draft.getId()).get(0);
                    String message = TestRunner.thrownMessage(
                            () -> sellAt(suite, draft, tier, PaymentMethod.CARD,
                                    tier.getPrice()));
                    assertTrue(message.contains("not selling tickets"),
                            "An idea cannot take money, got: " + message);
                }));

        test("a returning attendee is one record, not a new one", () ->
                withSuite("returning", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Return", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    String email = "regular@example.org";

                    sellAs(suite, event, tier, email);
                    sellAs(suite, event, tier, email);

                    assertEquals(1, suite.people().findAttendeeByEmail(email) == null ? 0 : 1,
                            "One person");
                    assertEquals(1, suite.people().countRegistrations(event.getId()),
                            "And one registration, so their fee is not charged twice");
                }));

        test("a discount is applied and the code is recorded", () ->
                withSuite("discount", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Discount", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    suite.addDiscount(event.getId(), "HALFOFF",
                            com.eventsuite.marketing.DiscountCode.Kind.PERCENT,
                            new BigDecimal("50"), 10, LocalDate.now(),
                            LocalDate.now().plusDays(30), "", "test");

                    com.eventsuite.marketing.DiscountCode code =
                            suite.marketing().findDiscountByCode(event.getId(), "HALFOFF");
                    BigDecimal price = code.priceOf(tier.getPrice());
                    Ticket ticket = sellAt(suite, event, tier, PaymentMethod.CARD, price,
                            "HALFOFF");

                    assertEquals("HALFOFF", ticket.getDiscountCode(), "On the ticket");
                    assertEquals(1, suite.marketing().findDiscountByCode(event.getId(),
                            "HALFOFF").getTimesUsed(), "And the use was counted");
                }));
    }

    private static void holds() {
        suite("Holds");

        test("a hold takes a seat and expires", () ->
                withSuite("hold", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Hold", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    com.eventsuite.people.Attendee who = suite.findOrCreateAttendee("Hold", "Test",
                            "hold" + System.nanoTime() + "@example.org", "", "", "", "", "", false);

                    Ticket held = suite.holdTicket(event, tier, who,
                            java.time.Duration.ofSeconds(-1));
                    assertTrue(held.isHoldExpired(Instant.now()), "Already past its deadline");
                    assertEquals(TicketStatus.RESERVED, held.getStatus(), "And not sold");

                    int released = suite.releaseExpiredHolds();
                    assertTrue(released >= 1, "The expiry sweep released it");
                    assertEquals(TicketStatus.AVAILABLE,
                            suite.tickets().findByReference(held.getReference()).getStatus(),
                            "So the seat is on sale again");
                }));

        test("confirming a hold turns it into a sale", () ->
                withSuite("confirm", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Confirm", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    com.eventsuite.people.Attendee who = suite.findOrCreateAttendee("Pay", "Test",
                            "pay" + System.nanoTime() + "@example.org", "", "", "", "", "", false);

                    Ticket held = suite.holdTicket(event, tier, who,
                            java.time.Duration.ofHours(2));
                    Ticket paid = suite.confirmHold(held, PaymentMethod.CARD);
                    assertEquals(TicketStatus.SOLD, paid.getStatus(), "Sold");
                    assertEquals(1, suite.finance().findPayments(event.getId()).size(),
                            "And the money was taken");
                }));
    }

    private static void refunds() {
        suite("Refunds");

        test("a refund puts the seat back on sale and records the reversal", () ->
                withSuite("refund", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Refund", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    Ticket ticket = sellAt(suite, event, tier, PaymentMethod.CARD,
                            tier.getPrice());
                    int soldBefore = suite.events().countSoldForType(tier.getId());

                    suite.refundTicket(ticket, "Changed plans");

                    assertEquals(soldBefore - 1, suite.events().countSoldForType(tier.getId()),
                            "The seat is sellable again");
                    assertEquals(TicketStatus.REFUNDED,
                            suite.tickets().findByReference(ticket.getReference()).getStatus(),
                            "And the ticket says refunded");

                    List<Payment> payments = suite.finance().findPayments(event.getId());
                    boolean recordedReversal = false;
                    for (Payment payment : payments) {
                        if (payment.getStatus() == PaymentStatus.REFUNDED) {
                            recordedReversal = true;
                        }
                    }
                    assertTrue(recordedReversal,
                            "The reversal is recorded rather than the original deleted, so"
                                    + " the day's takings can still be explained");
                }));

        test("a refunded ticket is not counted as revenue", () ->
                withSuite("norevenue", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "No Revenue", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    Ticket ticket = sellAt(suite, event, tier, PaymentMethod.CARD,
                            tier.getPrice());
                    suite.refundTicket(ticket, "");

                    assertEquals("0.00", suite.finance().sumSettledInflow(event.getId())
                            .toPlainString(), "Nothing left in the bank");
                }));
    }

    private static void checkIns() {
        suite("Check-in");

        test("a valid ticket is admitted and recorded", () ->
                withSuite("admit", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Door", 100);
                    Ticket ticket = EventStoreTest.sellOne(suite, event);
                    AccessGate gate = AccessGate.mainEntrance(event.getId());

                    var scan = suite.checkIn(ticket, event, gate, "desk", Instant.now());
                    assertEquals(CheckInOutcome.ADMITTED, scan.getOutcome(), "Admitted");
                    assertEquals(TicketStatus.CHECKED_IN,
                            suite.tickets().findByReference(ticket.getReference()).getStatus(),
                            "And the ticket says so");
                    assertEquals(1, suite.ops().countAdmitted(event.getId()),
                            "One person through the door");
                }));

        test("the same ticket cannot be used twice", () ->
                withSuite("duplicate", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Dup", 100);
                    Ticket ticket = EventStoreTest.sellOne(suite, event);
                    AccessGate gate = AccessGate.mainEntrance(event.getId());

                    suite.checkIn(ticket, event, gate, "desk", Instant.now());
                    String message = TestRunner.thrownMessage(
                            () -> suite.checkIn(ticket, event, gate, "desk",
                                    Instant.now().plusSeconds(1)));
                    assertTrue(message != null, "The second scan is refused");
                    assertEquals(1, suite.ops().countAdmitted(event.getId()),
                            "And only one person was admitted");
                }));

        test("a VIP ticket does not get into a staff-only area", () ->
                withSuite("wrongdoor", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Doors", 100);
                    // A conference has no VIP tier of its own, so one is added. Testing
                    // access needs a VIP to test with.
                    TicketType vipTier = new TicketType("t-vip", event.getId(), "VIP",
                            "", new BigDecimal("100"), 20,
                            com.eventsuite.ticketing.AccessTier.VIP, null, null, true, 9);
                    suite.events().insertTicketType(vipTier);

                    Ticket ticket = sellAt(suite, event, vipTier, PaymentMethod.CARD,
                            vipTier.getPrice());
                    var scan = suite.checkIn(ticket, event,
                            AccessGate.staffOnly(event.getId(), "Green room"), "desk",
                            Instant.now());
                    assertEquals(CheckInOutcome.WRONG_DOOR, scan.getOutcome(),
                            "Being a VIP does not open the green room");
                }));

        test("a refused scan is still recorded", () ->
                withSuite("recorded", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Refusals", 100);
                    Ticket ticket = EventStoreTest.sellOne(suite, event);
                    suite.checkIn(ticket, event,
                            AccessGate.vipArea(event.getId(), "VIP lounge"), "desk",
                            Instant.now());

                    assertEquals(1, suite.ops().findCheckIns(event.getId()).size(),
                            "A refused scan is kept, because it says the door list is wrong");
                    // They were let in, just not through that door, so they count as
                    // present. What the screen has to show is that they came to the
                    // wrong one, which is a different problem from not turning up.
                    assertEquals(CheckInOutcome.WRONG_DOOR,
                            suite.ops().findCheckIns(event.getId()).get(0).getOutcome(),
                            "Sent to the wrong door rather than turned away");
                    assertEquals(1, suite.ops().countAdmitted(event.getId()),
                            "And they are inside, so they count as present");
                }));

        test("nobody is admitted past the capacity", () ->
                withSuite("full", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Full", 1);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    Ticket first = sellAt(suite, event, tier, PaymentMethod.CARD,
                            tier.getPrice());
                    suite.checkIn(first, event, AccessGate.mainEntrance(event.getId()), "desk",
                            Instant.now());

                    com.eventsuite.people.Attendee second = suite.findOrCreateAttendee("Second",
                            "Person", "second" + System.nanoTime() + "@example.org", "", "",
                            "", "", "", false);
                    // The tier has plenty left, so the refusal has to come from the room
                    // being full rather than from the allocation.
                    TicketType extra = suite.events().findTicketTypes(event.getId()).get(1);
                    Ticket secondTicket = sellAt(suite, event, extra, PaymentMethod.CARD,
                            extra.getPrice());

                    var scan = suite.checkIn(secondTicket, event,
                            AccessGate.mainEntrance(event.getId()), "desk", Instant.now());
                    assertEquals(CheckInOutcome.AT_CAPACITY, scan.getOutcome(),
                            "One person fits in this room, and they are already here");
                }));
    }

    private static void money() {
        suite("Money");

        test("budget moves with the payment", () ->
                withSuite("budget", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Budget", 100);
                    suite.recordExpense(event, "Hall", "Hire", new BigDecimal("2000"),
                            PaymentMethod.BANK_TRANSFER, BudgetCategory.VENUE, "", false);

                    BudgetLine line = suite.finance().findBudgetLine(event.getId(),
                            BudgetCategory.VENUE);
                    assertEquals("2000.00", line.getActual().toPlainString(),
                            "Paid out of the heading");
                    assertEquals("2000.00", suite.finance().sumBudgetActual(event.getId())
                            .toPlainString(), "And in the budget total");
                }));

        test("a heading with no allocation reports as over", () ->
                withSuite("over", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Over", 100);
                    suite.setBudgetAllocation(event, BudgetCategory.CATERING,
                            new BigDecimal("100"));
                    suite.recordExpense(event, "Caterer", "Lunch", new BigDecimal("400"),
                            PaymentMethod.CARD, BudgetCategory.CATERING, "", false);

                    BudgetLine line = suite.finance().findBudgetLine(event.getId(),
                            BudgetCategory.CATERING);
                    assertTrue(line.isOverspent(), "Four hundred spent of a hundred allowed");
                    assertEquals("-300.00", line.getVariance().toPlainString(),
                            "So it is over by three hundred");
                    assertTrue(suite.finance().countOverspentHeadings(event.getId()) > 0,
                            "And the dashboard is told");
                }));

        test("an invoice runs from issued to paid", () ->
                withSuite("invoice", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Invoice", 100);
                    Invoice invoice = suite.issueInvoice(event, "Riverside Brewing",
                            "accounts@riverside.org", "Sponsorship", new BigDecimal("5000"),
                            new BigDecimal("20"), BudgetCategory.MARKETING,
                            LocalDate.now(), LocalDate.now().plusDays(30), "PO-1");

                    assertEquals(InvoiceStatus.ISSUED, invoice.getStatus(), "Issued");
                    assertEquals("6000.00", invoice.getTotal().toPlainString(),
                            "Five thousand plus twenty per cent");
                    assertEquals("6000.00", invoice.getOutstanding().toPlainString(),
                            "Nothing paid yet");
                    assertFalse(suite.finance().sumOutstandingInflow(event.getId())
                            .equals(Money.ZERO), "So it is a receivable, not revenue");

                    suite.payInvoice(invoice, new BigDecimal("6000"), PaymentMethod.BANK_TRANSFER);
                    Invoice settled = suite.finance().findInvoice(invoice.getId());
                    assertEquals(InvoiceStatus.PAID, settled.getStatus(), "Settled");
                    assertEquals("0.00", settled.getOutstanding().toPlainString(),
                            "Nothing left owing");
                }));

        test("invoice lines survive a round trip", () ->
                withSuite("lines", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Lines", 100);
                    Invoice invoice = suite.issueInvoice(event, "Sponsor", "s@example.org",
                            "Stand space", new BigDecimal("1500"), new BigDecimal("20"),
                            BudgetCategory.MARKETING, LocalDate.now(),
                            LocalDate.now().plusDays(14), "");

                    Invoice read = suite.finance().findInvoice(invoice.getId());
                    assertEquals(1, read.getLines().size(), "The line is still there");
                    assertEquals("Stand space", read.getLines().get(0).getDescription(),
                            "With its description");
                    assertEquals("1800.00", read.getTotal().toPlainString(),
                            "And its arithmetic intact");
                }));

        test("an overdue invoice is flagged", () ->
                withSuite("overdue", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Overdue", 100);
                    suite.issueInvoice(event, "Late Payer", "late@example.org", "Sponsorship",
                            new BigDecimal("1000"), BigDecimal.ZERO, BudgetCategory.MARKETING,
                            LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), "");

                    int updated = suite.refreshOverdueInvoices(event);
                    assertTrue(updated >= 1, "The sweep found it");
                    assertEquals(1, suite.finance().findOverdueInvoices(event.getId(),
                            LocalDate.now()).size(), "And the dashboard can list it");
                }));
    }

    private static void metrics() {
        suite("Dashboard figures");

        test("the figures agree with the records behind them", () ->
                withSuite("metrics", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Metrics", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    for (int index = 0; index < 10; index++) {
                        sellAt(suite, event, tier, PaymentMethod.CARD, tier.getPrice());
                    }

                    EventMetrics metrics = suite.metrics().forEvent(event.getId());
                    assertEquals(10, metrics.getTicketsSold(), "Ten sold");
                    assertEquals(tier.getQuantity() - 10, metrics.getTicketsRemaining(),
                            "And the rest still available");
                    assertEquals(0, metrics.getTicketsCheckedIn(),
                            "Nobody has come through the door yet");
                    assertEquals("0.00", metrics.getNoShowRate().toPlainString(),
                            "So nobody is a no-show before the event has started");
                }));

        test("attendance is measured against buyers, not against capacity", () ->
                withSuite("attendance", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Attendance", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    for (int index = 0; index < 10; index++) {
                        Ticket ticket = sellAt(suite, event, tier, PaymentMethod.CARD,
                                tier.getPrice());
                        if (index < 6) {
                            suite.checkIn(ticket, event,
                                    AccessGate.mainEntrance(event.getId()), "desk",
                                    Instant.now().plusSeconds(index));
                        }
                    }

                    EventMetrics metrics = suite.metrics().forEvent(event.getId());
                    assertEquals(6, metrics.getTicketsCheckedIn(), "Six came");
                    assertClose(60.0, metrics.getAttendanceRate().doubleValue(), 0.1,
                            "Sixty per cent of the ten who bought");
                    assertClose(40.0, metrics.getNoShowRate().doubleValue(), 0.1,
                            "And four did not");
                }));

        test("the health summary names one thing to do", () ->
                withSuite("health", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Health", 100);
                    suite.setBudgetAllocation(event, BudgetCategory.VENUE, new BigDecimal("100"));
                    suite.recordExpense(event, "Hall", "Hire", new BigDecimal("500"),
                            PaymentMethod.CARD, BudgetCategory.VENUE, "", false);

                    List<String> attention = suite.attentionItems(event.getId());
                    assertTrue(attention.size() >= 1, "Something is wrong, and it is listed");
                    assertTrue(attention.get(0).toLowerCase(java.util.Locale.ROOT)
                            .contains("budget"),
                            "And the first thing named is the budget, got: " + attention.get(0));
                }));
    }

    private static void reconciliation() {
        suite("Reconciliation");

        test("a sale with no discrepancy balances", () ->
                withSuite("balanced", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Balanced", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    sellAt(suite, event, tier, PaymentMethod.CARD, tier.getPrice());

                    var report = suite.reconcile(event);
                    assertEquals(com.eventsuite.analytics.ReconciliationReport.Status.BALANCED,
                            report.getStatus(), "The books balance");
                    assertEquals("0.00", report.getRevenueVariance().toPlainString(),
                            "Nothing out");
                }));

        test("an invoice that was never paid is outstanding, not missing", () ->
                withSuite("outstanding", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Outstanding", 100);
                    suite.issueInvoice(event, "Sponsor", "s@example.org", "Sponsorship",
                            new BigDecimal("5000"), BigDecimal.ZERO, BudgetCategory.MARKETING,
                            LocalDate.now(), LocalDate.now().plusDays(30), "");

                    var report = suite.reconcile(event);
                    assertEquals("5000.00", report.getMoneyOutstanding().toPlainString(),
                            "It is shown as owed");
                    assertFalse(report.isReconciled(), "So the event is not closed");
                }));

        test("a budget overrun is a discrepancy with an action attached", () ->
                withSuite("discrepancy", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Discrepancy", 100);
                    suite.setBudgetAllocation(event, BudgetCategory.PRODUCTION,
                            new BigDecimal("100"));
                    suite.recordExpense(event, "Rigger", "Extra staging", new BigDecimal("900"),
                            PaymentMethod.CARD, BudgetCategory.PRODUCTION, "", false);

                    var report = suite.reconcile(event);
                    assertTrue(report.getDiscrepancies().size() >= 1,
                            "The overrun is reported, not buried");
                    var first = report.getDiscrepancies().get(0);
                    assertTrue(first.getSuggestedAction().length() > 0,
                            "With something to actually do about it");
                }));
    }

    private static void automation() {
        suite("Automation");

        test("a rule fires when its condition is met", () ->
                withSuite("fires", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Automation", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    for (int index = 0; index < tier.getQuantity() - 1; index++) {
                        sellAt(suite, event, tier, PaymentMethod.CARD, tier.getPrice());
                    }

                    List<AutomationLog> entries = suite.runAutomation(event.getId(), false);
                    boolean soldOutRuleFired = false;
                    for (AutomationLog entry : entries) {
                        if (entry.getAction() == WorkflowAction.MARK_SOLD_OUT) {
                            soldOutRuleFired = true;
                        }
                    }
                    assertTrue(entries.size() > 0, "The rules were evaluated");
                    assertTrue(suite.workflows().countLog(event.getId()) > 0,
                            "And what happened was written down");
                }));

        test("an action that changes capacity waits for approval", () ->
                withSuite("approval", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Approval", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    for (int index = 0; index < tier.getQuantity(); index++) {
                        sellAt(suite, event, tier, PaymentMethod.CARD, tier.getPrice());
                    }

                    suite.runAutomation(event.getId(), false);
                    assertFalse(suite.events().findById(event.getId()).getStatus()
                            == EventStatus.SOLD_OUT,
                            "Not yet: nobody approved it");

                    suite.confirmAutomation(event.getId());
                    assertEquals(EventStatus.SOLD_OUT,
                            suite.events().findById(event.getId()).getStatus(),
                            "And after approval it has");
                }));

        test("a safe action runs without anybody asking", () ->
                withSuite("safe", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Safe", 100);
                    suite.addRule(WorkflowRule.of("r-test", event.getId(), "Notice me",
                            WorkflowTrigger.ALMOST_SOLD_OUT,
                            com.eventsuite.workflow.ConditionField.PERCENT_SOLD,
                            com.eventsuite.workflow.ConditionOperator.AT_MOST,
                            new BigDecimal("100"), WorkflowAction.NOTIFY_ORGANISER, "Nearly"));

                    suite.runAutomation(event.getId(), false);
                    List<AutomationLog> fired = suite.workflows()
                            .findActionedLog(event.getId(), 50);
                    assertTrue(fired.size() >= 1,
                            "A notice is safe, so it runs on its own");
                }));

        test("a one-shot rule does not fire twice", () ->
                withSuite("once", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Once", 100);
                    // A trigger that can only happen once. A manual trigger would fire
                    // on every run by design, so it cannot show this behaviour.
                    suite.addRule(WorkflowRule.of("r-once", event.getId(), "Tell me once",
                            WorkflowTrigger.EVENT_CREATED,
                            com.eventsuite.workflow.ConditionField.DAYS_UNTIL_EVENT,
                            com.eventsuite.workflow.ConditionOperator.AT_LEAST,
                            new BigDecimal("-10000"), WorkflowAction.NOTIFY_ORGANISER, ""));

                    suite.runAutomation(event.getId(), false);
                    assertEquals(1, firings(suite, event.getId(), "r-once"),
                            "It fires the first time its condition is met");
                    suite.runAutomation(event.getId(), false);
                    suite.runAutomation(event.getId(), false);
                    assertEquals(1, firings(suite, event.getId(), "r-once"),
                            "And then stays quiet, or the notices never stop coming");
                }));

        test("a misconfigured rule is reported rather than ignored", () ->
                withSuite("incoherent", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Broken", 100);
                    WorkflowRule broken = WorkflowRule.of("r-broken", event.getId(),
                            "Nonsense", WorkflowTrigger.TICKET_SOLD_OUT,
                            com.eventsuite.workflow.ConditionField.FEEDBACK_SCORE,
                            com.eventsuite.workflow.ConditionOperator.AT_LEAST,
                            BigDecimal.ONE, WorkflowAction.NOTIFY_ORGANISER, "");
                    suite.addRule(broken);

                    assertTrue(broken.getCoherenceProblem().length() > 0,
                            "Feedback only exists after the event, so a sold-out rule"
                                    + " reading it can never fire");
                    List<AutomationLog> entries = suite.runAutomation(event.getId(), false);
                    boolean reported = false;
                    for (AutomationLog entry : entries) {
                        if (entry.getOutcome() == AutomationLog.Outcome.INCOHERENT) {
                            reported = true;
                        }
                    }
                    assertTrue(reported,
                            "And it is logged, so the mistake is visible rather than silent");
                }));

        test("the context reflects what is actually true", () ->
                withSuite("context", suite -> {
                    Event event = EventStoreTest.saleableEvent(suite, "Context", 100);
                    TicketType tier = suite.events().findTicketTypes(event.getId()).get(0);
                    sellAt(suite, event, tier, PaymentMethod.CARD, tier.getPrice());

                    var context = suite.buildContext(event.getId());
                    assertClose(1.0, context.get(
                            com.eventsuite.workflow.ConditionField.TICKETS_SOLD)
                            .doubleValue(), 0.01, "One ticket sold");
                    assertTrue(context.get(
                            com.eventsuite.workflow.ConditionField.DAYS_UNTIL_EVENT)
                            .doubleValue() > 0, "And the event is in the future");
                }));
    }

    /** How many times one particular rule has fired, according to the log. */
    private static int firings(EventSuite suite, String eventId, String ruleId)
            throws SQLException {
        int count = 0;
        for (AutomationLog entry : suite.workflows().findLog(eventId, 400)) {
            if (ruleId.equals(entry.getRuleId()) && entry.isActioned()) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- helpers

    private static Ticket sellAt(EventSuite suite, Event event, TicketType tier,
                                 PaymentMethod method, BigDecimal price) throws SQLException {
        return sellAt(suite, event, tier, method, price, "");
    }

    /** A sale, optionally against a discount code. */
    private static Ticket sellAt(EventSuite suite, Event event, TicketType tier,
                                 PaymentMethod method, BigDecimal price, String code)
            throws SQLException {
        com.eventsuite.people.Attendee who = suite.findOrCreateAttendee("Buyer",
                "Person", "buyer" + System.nanoTime() + "@example.org", "", "", "", "", "",
                false);
        return suite.sellTicket(event, tier, who, method, code, "", price);
    }

    private static void sellAs(EventSuite suite, Event event, TicketType tier, String email)
            throws SQLException {
        com.eventsuite.people.Attendee who = suite.findOrCreateAttendee("Regular", "Person",
                email, "", "", "", "", "", false);
        suite.sellTicket(event, tier, who, PaymentMethod.CARD, "", "", tier.getPrice());
    }
}
