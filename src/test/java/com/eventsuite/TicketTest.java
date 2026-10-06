package com.eventsuite;

import com.eventsuite.ticketing.AccessTier;
import com.eventsuite.ticketing.Registration;
import com.eventsuite.ticketing.RegistrationStatus;
import com.eventsuite.ticketing.Ticket;
import com.eventsuite.ticketing.TicketStatus;
import com.eventsuite.ticketing.TicketType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static com.eventsuite.TestRunner.assertEquals;
import static com.eventsuite.TestRunner.assertFalse;
import static com.eventsuite.TestRunner.assertThrows;
import static com.eventsuite.TestRunner.assertTrue;
import static com.eventsuite.TestRunner.suite;
import static com.eventsuite.TestRunner.test;

/** Ticketing: tiers, allocation, references, and the lifecycle of a ticket. */
final class TicketTest {

    private TicketTest() {
    }

    static TicketType aTier(String id, String name, String price, int quantity,
                           AccessTier tier) {
        return new TicketType(id, "evt-1", name, "", new BigDecimal(price), quantity,
                tier, null, null, true, 0);
    }

    static void register() {
        suite("Ticket tiers");

        test("a tier knows what it costs and how many there are", () ->
                assertEquals("50.00", aTier("t1", "Standard", "50", 100,
                        AccessTier.GENERAL).getPrice().toPlainString(),
                        "Price is stored to two places"));

        test("a free tier says so rather than showing nil", () -> {
            TicketType free = aTier("t1", "Speaker Pass", "0", 10, AccessTier.SPEAKER);
            assertTrue(free.isFree(), "Zero is a free ticket");
            assertEquals("Free", free.getPriceLabel(), "And it reads as one");
        });

        test("allocation is what remains after what has sold", () -> {
            TicketType tier = aTier("t1", "Standard", "50", 100, AccessTier.GENERAL);
            assertEquals(100, tier.remaining(0), "Nothing sold yet");
            assertEquals(40, tier.remaining(60), "Sixty sold of a hundred");
        });

        test("remaining never goes negative", () -> {
            TicketType tier = aTier("t1", "Standard", "50", 10, AccessTier.GENERAL);
            assertEquals(0, tier.remaining(25),
                    "Minus fifteen chairs is not information anybody can use");
        });

        test("sold out when the allocation is reached", () -> {
            TicketType tier = aTier("t1", "Standard", "50", 10, AccessTier.GENERAL);
            assertFalse(tier.isSoldOut(9), "One left");
            assertTrue(tier.isSoldOut(10), "Full");
            assertTrue(tier.isSoldOut(11), "Over is still full");
        });

        test("revenue is price times what sold", () ->
                assertEquals("150.00", aTier("t1", "Standard", "50", 100,
                        AccessTier.GENERAL).revenueFor(3).toPlainString(),
                        "Three at fifty is a hundred and fifty"));

        test("a sales window that closes before it opens is rejected", () -> {
            String message = TestRunner.thrownMessage(() -> new TicketType("t1", "e1", "X", "",
                    BigDecimal.TEN, 10, AccessTier.GENERAL, LocalDate.of(2026, 5, 10),
                    LocalDate.of(2026, 5, 1), true, 0));
            assertTrue(message.contains("closes before it opens"),
                    "The message should say what is wrong, got: " + message);
        });

        test("a tier outside its window is not on sale", () -> {
            TicketType tier = new TicketType("t1", "e1", "Early", "", BigDecimal.TEN, 10,
                    AccessTier.GENERAL, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                    true, 0);
            assertFalse(tier.isOnSaleAt(LocalDate.of(2026, 4, 30)), "Before it opens");
            assertTrue(tier.isOnSaleAt(LocalDate.of(2026, 5, 15)), "During it");
            assertFalse(tier.isOnSaleAt(LocalDate.of(2026, 6, 1)), "After it closes");
        });

        accessRules();
        ticketRules();
        registrationRules();
    }

    private static void accessRules() {
        suite("Access tiers");

        test("a higher tier outranks a lower one", () -> {
            assertTrue(AccessTier.VIP.canOpen(AccessTier.PREMIUM), "VIP opens premium");
            assertFalse(AccessTier.PREMIUM.canOpen(AccessTier.VIP),
                    "Premium does not open the VIP lounge");
        });

        test("everyone with a valid ticket opens the main door", () -> {
            for (AccessTier tier : AccessTier.values()) {
                assertTrue(tier.canOpen(AccessTier.GENERAL),
                        tier + " must be able to get into the building");
            }
        });

        test("a general ticket does not open the green room", () ->
                assertFalse(AccessTier.GENERAL.canOpen(AccessTier.SPEAKER),
                        "The green room is not a room anyone can wander into"));

        test("staff and speakers open working areas", () -> {
            assertTrue(AccessTier.STAFF.canOpen(AccessTier.SPEAKER), "Staff need the back");
            assertTrue(AccessTier.SPEAKER.canOpen(AccessTier.SPEAKER), "Speakers need the back");
        });

        test("complimentary tiers are the ones nobody pays for", () -> {
            assertTrue(AccessTier.SPEAKER.isComplimentary(), "Speakers do not pay");
            assertTrue(AccessTier.PRESS.isComplimentary(), "Nor do the press");
            assertFalse(AccessTier.GENERAL.isComplimentary(), "The public does");
        });

        test("only some tiers bring a guest", () -> {
            assertTrue(AccessTier.VIP.allowsPlusOne(), "A VIP may bring someone");
            assertFalse(AccessTier.GENERAL.allowsPlusOne(),
                    "A standard ticket admits one person, not two");
        });
    }

    private static void ticketRules() {
        suite("Ticket lifecycle");

        test("a ticket is sold, then used", () -> {
            Ticket ticket = sampleTicket();
            ticket.moveTo(TicketStatus.SOLD);
            assertTrue(ticket.isSold(), "Paid for");
            ticket.moveTo(TicketStatus.CHECKED_IN);
            assertTrue(ticket.isCheckedIn(), "And used");
        });

        test("a ticket cannot be used before it is paid for", () -> {
            Ticket ticket = sampleTicket();
            String message = TestRunner.thrownMessage(() -> ticket.moveTo(TicketStatus.CHECKED_IN));
            assertTrue(message.contains("cannot go"),
                    "The rule should refuse it, got: " + message);
        });

        test("a ticket cannot be sold twice", () -> {
            Ticket ticket = sampleTicket();
            ticket.moveTo(TicketStatus.SOLD);
            String message = TestRunner.thrownMessage(() -> ticket.moveTo(TicketStatus.SOLD));
            assertTrue(message.length() > 0, "A second sale of one ticket is refused");
        });

        test("a paid ticket can be refunded and the seat goes back", () -> {
            Ticket ticket = sampleTicket();
            ticket.moveTo(TicketStatus.SOLD);
            ticket.refund();
            assertEquals(TicketStatus.REFUNDED, ticket.getStatus(), "Refunded");
            assertFalse(ticket.isSold(), "And no longer counted as sold");
        });

        test("a used ticket cannot be un-admitted", () -> {
            Ticket ticket = sampleTicket();
            ticket.moveTo(TicketStatus.SOLD);
            ticket.checkIn(Instant.now(), "Main entrance", "desk");
            String message = TestRunner.thrownMessage(ticket::refund);
            assertTrue(message.contains("Only a sold ticket"),
                    "Somebody already inside cannot be refunded away, got: " + message);
        });

        test("paying for a ticket clears its hold", () -> {
            Ticket held = new Ticket("ABC234", "e1", "t1", "a1", "Test", "t@example.org",
                    "Standard", AccessTier.GENERAL, "50.00", "", Instant.now(),
                    Instant.now().plusSeconds(900), "", "");
            held.moveTo(TicketStatus.RESERVED);
            held.moveTo(TicketStatus.SOLD);
            assertEquals(null, held.getHoldExpiresAt(),
                    "A deadline that no longer applies must not release a paid seat later");
        });

        test("an unpaid hold expires and stops admitting", () -> {
            Ticket held = new Ticket("ABC234", "e1", "t1", "a1", "Test", "t@example.org",
                    "Standard", AccessTier.GENERAL, "50.00", "", Instant.now(),
                    Instant.now().minusSeconds(60), "", "");
            held.moveTo(TicketStatus.RESERVED);
            assertTrue(held.isHoldExpired(Instant.now()), "Past its deadline");
            assertFalse(held.admitsAt(Instant.now()),
                    "So it cannot let anybody in");
        });

        test("a sold ticket admits its holder", () -> {
            Ticket ticket = sampleTicket();
            ticket.moveTo(TicketStatus.SOLD);
            assertTrue(ticket.admitsAt(Instant.now()), "Paid for and inside the date");
        });

        test("references avoid the characters that get misheard", () -> {
            String reference = Ticket.newReference(12);
            assertEquals(12, reference.length(), "The length asked for");
            assertFalse(reference.contains("O"), "O is read as a zero");
            assertFalse(reference.contains("0"), "Zero is read as an O");
            assertFalse(reference.contains("1"), "One is read as an L");
            assertFalse(reference.contains("I"), "And L as a one");
        });

        test("two references are not the same", () -> {
            assertFalse(Ticket.newReference().equals(Ticket.newReference()),
                    "Collision at ten characters is not something that happens");
        });

        test("a code is matched in full, or by a long enough prefix", () -> {
            Ticket ticket = sampleTicket();
            ticket.moveTo(TicketStatus.SOLD);
            assertTrue(ticket.matchesCode(ticket.getReference().toLowerCase(
                    java.util.Locale.ROOT)), "Case is not the holder's fault at a desk");
            assertTrue(ticket.matchesCode(ticket.getReference().substring(0, 4)),
                    "Four characters off a badly printed ticket");
            assertFalse(ticket.matchesCode(ticket.getReference().substring(0, 2)),
                    "Two characters match most of the book and tell nobody anything");
            assertFalse(ticket.matchesCode(""), "An empty field is not a code");
        });
    }

    private static void registrationRules() {
        suite("Registrations");

        test("a registration is a place, a ticket is the proof", () -> {
            Registration registration = Registration.create("r1", "e1", "a1", "Test",
                    "t@example.org", new BigDecimal("50"), Instant.now());
            assertFalse(registration.getStatus().holdsPlace(), "A new form is not a place yet");
            registration.moveTo(RegistrationStatus.CONFIRMED);
            assertTrue(registration.getStatus().holdsPlace(), "Confirmed holds one");
        });

        test("the waiting list does not consume a place", () -> {
            Registration registration = Registration.create("r1", "e1", "a1", "Test",
                    "t@example.org", BigDecimal.TEN, Instant.now());
            registration.moveTo(RegistrationStatus.CONFIRMED);
            registration.waitlist("3");
            assertFalse(registration.getStatus().holdsPlace(),
                    "A waiting list that consumed capacity would make promoting pointless");
            assertEquals("3", registration.getWaitlistPosition(), "And it remembers its place");
        });

        test("leaving the queue forgets the position", () -> {
            Registration registration = Registration.create("r1", "e1", "a1", "Test",
                    "t@example.org", BigDecimal.TEN, Instant.now());
            registration.waitlist("3");
            registration.moveTo(RegistrationStatus.CONFIRMED);
            assertEquals("", registration.getWaitlistPosition(),
                    "A position in a queue you have left means nothing");
        });

        test("what is owed is the fee less what has been paid", () -> {
            Registration registration = Registration.create("r1", "e1", "a1", "Test",
                    "t@example.org", new BigDecimal("100"), Instant.now());
            assertEquals("100.00", registration.getOutstanding().toPlainString(), "Unpaid");
            registration.recordPayment(new BigDecimal("40"));
            assertEquals("60.00", registration.getOutstanding().toPlainString(), "Part paid");
            // Only a registration that holds a place is chased. A form somebody started
            // and abandoned owes nothing, and listing it as a debt would put money on
            // the books that was never expected.
            assertFalse(registration.isOutstanding(), "A half-filled form owes nothing");
            registration.moveTo(RegistrationStatus.CONFIRMED);
            assertTrue(registration.isOutstanding(),
                    "But a confirmed place with money outstanding does");
        });

        test("an overpayment is a credit, not a negative debt", () -> {
            Registration registration = Registration.create("r1", "e1", "a1", "Test",
                    "t@example.org", new BigDecimal("50"), Instant.now());
            registration.recordPayment(new BigDecimal("80"));
            assertEquals("0.00", registration.getOutstanding().toPlainString(),
                    "Nobody owes a negative amount");
            assertTrue(registration.isPaidInFull(), "And the fee is settled");
        });

        test("somebody who has attended cannot be walked in again", () -> {
            Registration registration = Registration.create("r1", "e1", "a1", "Test",
                    "t@example.org", BigDecimal.ZERO, Instant.now());
            registration.moveTo(RegistrationStatus.CONFIRMED);
            registration.markAttended();
            String message = TestRunner.thrownMessage(
                    () -> registration.moveTo(RegistrationStatus.WALK_IN));
            assertTrue(message.contains("cannot go"),
                    "Their record is history, got: " + message);
        });
    }

    private static Ticket sampleTicket() {
        TicketType tier = aTier("t1", "Standard", "50", 100, AccessTier.GENERAL);
        com.eventsuite.people.Attendee attendee = new com.eventsuite.people.Attendee("a1",
                "Test", "Person", "t.person@example.org", "", "", "", "", "", "", false);
        return Ticket.forAttendee("ABC234XYZ", "evt-1", tier, attendee, "50.00");
    }
}
