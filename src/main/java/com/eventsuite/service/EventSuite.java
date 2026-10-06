package com.eventsuite.service;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.analytics.MetricsService;
import com.eventsuite.analytics.ReconciliationReport;
import com.eventsuite.analytics.SustainabilityMetric;
import com.eventsuite.analytics.TrendPoint;
import com.eventsuite.core.Event;
import com.eventsuite.core.EventCategory;
import com.eventsuite.core.EventStatus;
import com.eventsuite.data.EventStore;
import com.eventsuite.data.FinanceStore;
import com.eventsuite.data.Ids;
import com.eventsuite.data.MarketingStore;
import com.eventsuite.data.OpsStore;
import com.eventsuite.data.PeopleStore;
import com.eventsuite.data.Sql;
import com.eventsuite.data.TicketStore;
import com.eventsuite.data.WorkflowStore;
import com.eventsuite.finance.BudgetCategory;
import com.eventsuite.finance.BudgetLine;
import com.eventsuite.finance.Invoice;
import com.eventsuite.finance.InvoiceStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.finance.Payment;
import com.eventsuite.finance.PaymentMethod;
import com.eventsuite.finance.TransactionDirection;
import com.eventsuite.marketing.DiscountCode;
import com.eventsuite.ops.AccessGate;
import com.eventsuite.ops.CheckIn;
import com.eventsuite.ops.CheckInOutcome;
import com.eventsuite.ops.EngagementRecord;
import com.eventsuite.ops.EngagementType;
import com.eventsuite.people.Attendee;
import com.eventsuite.people.ResourceBooking;
import com.eventsuite.people.ResourceItem;
import com.eventsuite.people.Speaker;
import com.eventsuite.ticketing.AccessTier;
import com.eventsuite.ticketing.Registration;
import com.eventsuite.ticketing.RegistrationStatus;
import com.eventsuite.ticketing.Ticket;
import com.eventsuite.ticketing.TicketStatus;
import com.eventsuite.ticketing.TicketType;
import com.eventsuite.workflow.ConditionField;
import com.eventsuite.workflow.WorkflowContext;
import com.eventsuite.workflow.WorkflowEngine;
import com.eventsuite.workflow.WorkflowRule;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The application's operations: everything the interface can actually do.
 *
 * <p>The screens never touch a store directly. They call a method here, which opens a
 * transaction, performs the whole operation, and commits or rolls back. That is what
 * makes a sale atomic: a ticket, the payment that paid for it, the registration it
 * settles and the discount it consumed are one unit of work, so a failure part way
 * through cannot leave a ticket nobody paid for.
 *
 * <p>The alternative — letting each screen write its own rows — is how systems end up
 * with tickets whose payment was never recorded, and it is why the store classes are
 * package-visible in spirit even though they are public for the tests.
 */
public final class EventSuite implements AutoCloseable {

    private final Connection connection;

    /**
     * Held for the length of every transaction, and by every screen while it refreshes.
     *
     * <p>Re-entrant because a save that begins its own transaction is sometimes called
     * from inside one — refunding a ticket, for instance, updates both the ticket and
     * the payment under a single transaction.
     */
    private final Lock transactionLock = new ReentrantLock();

    private final EventStore events;
    private final TicketStore tickets;
    private final PeopleStore people;
    private final FinanceStore finance;
    private final OpsStore ops;
    private final MarketingStore marketing;
    private final WorkflowStore workflows;
    private final MetricsService metrics;

    public EventSuite(Connection connection) {
        this.connection = connection;
        this.events = new EventStore(connection);
        this.tickets = new TicketStore(connection);
        this.people = new PeopleStore(connection);
        this.finance = new FinanceStore(connection);
        this.ops = new OpsStore(connection);
        this.marketing = new MarketingStore(connection);
        this.workflows = new WorkflowStore(connection);
        this.metrics = new MetricsService(connection);
    }

    /** The underlying connection, for the tests and for reports. */
    public Connection getConnection() {
        return connection;
    }

    public EventStore events() {
        return events;
    }

    public TicketStore tickets() {
        return tickets;
    }

    public PeopleStore people() {
        return people;
    }

    public FinanceStore finance() {
        return finance;
    }

    public OpsStore ops() {
        return ops;
    }

    public MarketingStore marketing() {
        return marketing;
    }

    public WorkflowStore workflows() {
        return workflows;
    }

    public MetricsService metrics() {
        return metrics;
    }

    // ---------------------------------------------------------------- events

    /**
     * Creates an event with its ticket tiers, budget headings and starter automation.
     *
     * <p>All four happen in one transaction. An event with no tiers cannot sell
     * anything, one with no budget cannot be reconciled, and one with no automation
     * misses the warnings everybody expects. Creating them together means a new event
     * is useful the moment it exists, rather than needing three more visits.
     */
    public Event createEvent(String id,
                             String name,
                             EventCategory category,
                             String venueId,
                             String venueName,
                             LocalDate startDate,
                             LocalDate endDate,
                             int capacity,
                             BigDecimal budgetPlanned,
                             String organiser,
                             String description,
                             String brandPrimary,
                             String brandAccent,
                             String websiteUrl,
                             List<String> tags) throws SQLException {
        Event event = new Event(id == null || id.isBlank() ? Ids.event() : id,
                name, category, venueId, venueName,
                startDate, endDate,
                null, null, null,
                organiser, description, brandPrimary, brandAccent, websiteUrl,
                capacity, tags, EventStatus.DRAFT, budgetPlanned, "");

        begin();
        try {
            events.insert(event);
            seedTicketTypes(event);
            seedBudget(event, budgetPlanned);
            seedAutomation(event.getId());
            commit();
        } catch (SQLException | RuntimeException failure) {
            rollback();
            throw failure;
        }
        return event;
    }

    /**
     * Gives a new event the tiers its category implies.
     *
     * <p>The prices are worked out from the capacity rather than invented: a
     * thousand-seat conference and a twenty-five seat workshop cannot have the same
     * ticket price, and a number scaled by room size is a better starting point than
     * a guess. Every tier is editable afterwards.
     */
    private void seedTicketTypes(Event event) throws SQLException {
        String[] names = event.getCategory().getSuggestedTicketTypes();
        BigDecimal[] prices = suggestedPrices(event);
        for (int index = 0; index < names.length; index++) {
            int quantity = allocationFor(event, index);
            TicketType type = new TicketType(Ids.ticketType(), event.getId(), names[index],
                    names[index] + " for " + event.getName(), prices[index], quantity,
                    accessTierFor(event.getCategory(), index),
                    null, null, index != 0, index);
            events.insertTicketType(type);
        }
    }

    /**
     * Starting prices for a new event's tiers.
     *
     * <p>Scaled so the cheapest tier costs a little per head of capacity, with better
     * seats costing a multiple of it. Realistic enough to be a starting point and low
     * enough that nobody sells themselves out before they have looked at it.
     *
     * <p>Whether a tier is free is decided by what it <em>is</em>, not by where it sits
     * in the list. A speaker, press or staff pass is not free because it happens to be
     * third in an array; it is free because nobody pays to be on the programme.
     * Keying the price off the position gets that wrong, and a priced VIP lounge or a
     * charged press pass is the kind of mistake nobody notices until the door.
     */
    private BigDecimal[] suggestedPrices(Event event) {
        String[] names = event.getCategory().getSuggestedTicketTypes();
        BigDecimal base = BigDecimal.valueOf(Math.max(5, event.getCapacity() / 20.0))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal[] prices = new BigDecimal[names.length];
        for (int index = 0; index < names.length; index++) {
            AccessTier tier = accessTierFor(event.getCategory(), index);
            prices[index] = tier.isComplimentary()
                    ? Money.ZERO
                    : Money.of(base.multiply(BigDecimal.valueOf(multiplierFor(tier))));
        }
        return prices;
    }

    /**
     * What a tier costs relative to the cheapest one.
     *
     * <p>General admission is the base. Premium and better seating cost about twice
     * it, because that is roughly what the difference in the room is worth.
     */
    private int multiplierFor(AccessTier tier) {
        return switch (tier) {
            case GENERAL, COMPANION -> 1;
            case PREMIUM -> 2;
            case VIP, PRESS -> 3;
            case SPEAKER, STAFF -> 0;
        };
    }

    /**
     * How many of each tier to offer.
     *
     * <p>The first tier gets the whole sellable capacity, because it is the one that
     * has to be able to sell the event out. Extra tiers are allowances on top, for the
     * early bird that creates urgency and the VIP allocation that has to be ring
     * fenced.
     */
    private int allocationFor(Event event, int index) {
        return switch (index) {
            case 0 -> event.getSellableCapacity();
            case 1 -> Math.max(1, event.getSellableCapacity() / 4);
            case 2 -> Math.max(1, event.getCapacity() / 20);
            default -> Math.max(1, event.getSellableCapacity() / 10);
        };
    }

    private AccessTier accessTierFor(EventCategory category, int index) {
        String name = category.getSuggestedTicketTypes()[index].toLowerCase(java.util.Locale.ROOT);
        if (name.contains("speaker")) {
            return AccessTier.SPEAKER;
        }
        if (name.contains("vip") || name.contains("balcony") || name.contains("hospitality")
                || name.contains("club") || name.contains("front row") || name.contains("lounge")) {
            return AccessTier.VIP;
        }
        if (name.contains("press")) {
            return AccessTier.PRESS;
        }
        if (name.contains("staff") || name.contains("crew")) {
            return AccessTier.STAFF;
        }
        if (name.contains("guest") || name.contains("party") || name.contains("supper")) {
            return AccessTier.COMPANION;
        }
        if (name.contains("standard") || name.contains("premium") || name.contains("terrace")) {
            return AccessTier.PREMIUM;
        }
        return AccessTier.GENERAL;
    }

    /** Seeds the budget headings from the total, using each category's usual share. */
    private void seedBudget(Event event, BigDecimal budgetPlanned) throws SQLException {
        BigDecimal total = Money.of(budgetPlanned);
        if (total.signum() <= 0) {
            return;
        }
        for (BudgetCategory category : BudgetCategory.coreCategories()) {
            finance.saveBudgetLine(BudgetLine.suggested(
                    event.getId() + ":" + category.name(), event.getId(), category, total));
        }
    }

    /**
     * Adds the default automation rules for an event.
     *
     * <p>Skipped for any rule an equivalent already has, so creating an event while
     * the event-wide rules exist does not produce two of each.
     */
    private void seedAutomation(String eventId) throws SQLException {
        for (WorkflowRule rule : WorkflowEngine.starterRules(eventId, eventId)) {
            if (!workflows.hasEquivalentRule(rule)) {
                workflows.insertRule(rule);
            }
        }
    }

    /** Moves an event to a new lifecycle state, enforcing the allowed moves. */
    public void changeStatus(Event event, EventStatus next) throws SQLException {
        event.moveTo(next);
        events.update(event);
    }

    /** Saves an edited event. */
    public void saveEvent(Event event) throws SQLException {
        events.save(event);
    }

    /** Removes an event, refusing if anything has been sold or anybody has arrived. */
    public boolean deleteEvent(String eventId) throws SQLException {
        return events.deleteIfUnused(eventId);
    }

    // ---------------------------------------------------------------- attendees

    /**
     * Finds or creates the attendee record for an email address.
     *
     * <p>A returning attendee is one row, not a new near-duplicate. Without this the
     * same person appears three times with three spellings of their company, and
     * every "how many people came" figure becomes guesswork.
     */
    public Attendee findOrCreateAttendee(String firstName, String lastName, String email,
                                        String phone, String organisation, String jobTitle,
                                        String dietary, String accessibility,
                                        boolean consentMarketing) throws SQLException {
        Attendee existing = people.findAttendeeByEmail(email);
        if (existing != null) {
            return existing;
        }
        Attendee created = new Attendee(Ids.attendee(), firstName, lastName, email, phone,
                organisation, jobTitle, dietary, accessibility, "", consentMarketing);
        people.insertAttendee(created);
        return created;
    }

    /** Saves an edited attendee. */
    public void saveAttendee(Attendee attendee) throws SQLException {
        people.saveAttendee(attendee);
    }

    // ---------------------------------------------------------------- sales

    /**
     * Sells a ticket: the ticket, the payment and the registration, all or nothing.
     *
     * <p>This is the most important method in the system, because it is the one that
     * makes money. The order is deliberate. The tier's stock is checked first, because
     * that is the one thing that cannot be undone afterwards; then the ticket is
     * inserted, so it cannot be sold twice; then the money is recorded, so the sale
     * exists even if the receipt fails to print; then the registration is settled.
     *
     * @param priceOverride what was actually charged, or null to use the tier price
     * @return the ticket that was issued
     * @throws SQLException when the sale cannot be completed for any reason
     */
    public Ticket sellTicket(Event event,
                             TicketType type,
                             Attendee attendee,
                             PaymentMethod method,
                             String discountCode,
                             String seatLabel,
                             BigDecimal priceOverride) throws SQLException {
        Instant now = Instant.now();
        BigDecimal price = priceOverride != null ? Money.of(priceOverride) : type.getPrice();

        begin();
        try {
            int sold = events.countSoldForType(type.getId());
            if (type.isSoldOut(sold)) {
                throw new SQLException("The " + type.getName() + " tickets are sold out");
            }
            if (!type.isOnSaleAt(LocalDate.now())) {
                throw new SQLException("The " + type.getName() + " tickets are not on sale today");
            }
            // The stored status is the authority, not the object the caller happened to
            // pass in. A caller holding an event from before it was moved on to sale
            // would otherwise be told its own event is not selling, and the sale would
            // be refused for a reason that no longer exists.
            Event stored = events.findById(event.getId());
            EventStatus actualStatus = stored == null ? event.getStatus() : stored.getStatus();
            if (!actualStatus.acceptsSales()) {
                throw new SQLException("This event is " + actualStatus.getLabel()
                        + ", so it is not selling tickets");
            }

            String reference = tickets.mintReference();
            Ticket ticket = Ticket.issue(reference, event.getId(), type, attendee,
                    price.toPlainString(), discountCode, now, seatLabel);
            ticket.moveTo(TicketStatus.SOLD);
            tickets.insert(ticket);

            if (Money.isPositive(price)) {
                Payment payment = Payment.sale(Ids.payment(), event.getId(), reference,
                        attendee.getFullName(), price, method, reference, attendee.getId(), now);
                finance.insertPayment(payment);
                finance.spendAgainst(event.getId(), BudgetCategory.PLATFORMS,
                        payment.getProviderFee());
            }

            if (!discountCode.isBlank()) {
                MarketingStore codes = marketing;
                DiscountCode found = codes.findDiscountByCode(event.getId(), discountCode);
                if (found != null) {
                    codes.recordRedemption(found.getId());
                }
            }

            settleRegistration(event, attendee, type, price, reference, now);
            commit();
            return ticket;
        } catch (SQLException | RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    /**
     * Records or updates the registration behind a sale.
     *
     * <p>Existing registrations are settled rather than duplicated, so somebody
     * buying a second ticket for the same event does not end up with two registration
     * records and two fees owed.
     */
    private void settleRegistration(Event event, Attendee attendee, TicketType type,
                                    BigDecimal price, String reference, Instant now)
            throws SQLException {
        Registration existing = people.findRegistrationOf(event.getId(), attendee.getId());
        if (existing == null) {
            Registration registration = new Registration(Ids.registration(), event.getId(),
                    attendee.getId(), attendee.getFullName(), attendee.getEmail(), reference,
                    "", Money.of(price), Money.of(price), now,
                    RegistrationStatus.CONFIRMED, "");
            people.insertRegistration(registration);
            return;
        }
        Registration updated = existing.withTicket(reference)
                .withPayment(Money.of(price));
        if (existing.getStatus() == RegistrationStatus.WAITLISTED
                || existing.getStatus() == RegistrationStatus.PENDING) {
            updated.moveTo(RegistrationStatus.CONFIRMED);
        }
        people.updateRegistration(updated);
    }

    /**
     * Holds a ticket without payment, for someone who has said they will buy later.
     *
     * <p>The hold has a deadline. A hold with no deadline is a reservation that
     * silently reduces the event's capacity for ever, because nothing ever releases
     * it.
     */
    public Ticket holdTicket(Event event, TicketType type, Attendee attendee,
                             java.time.Duration holdFor) throws SQLException {
        int sold = events.countSoldForType(type.getId());
        if (type.isSoldOut(sold)) {
            throw new SQLException("The " + type.getName() + " tickets are sold out");
        }
        Instant now = Instant.now();
        Ticket ticket = Ticket.forAttendee(tickets.mintReference(), event.getId(), type, attendee,
                type.getPrice().toPlainString(), now);
        ticket.moveTo(TicketStatus.RESERVED);
        begin();
        try {
            tickets.insert(ticket);
            // The deadline lives on the row rather than in memory, so it survives a
            // restart. A hold whose deadline lived only in the running application
            // would never expire if the application was closed.
            Ticket withHold = holdTicketWithExpiry(ticket, now.plus(holdFor));
            // The status is carried across explicitly. Rebuilding a ticket starts it
            // off available again, so without this the hold comes back looking unsold
            // while still carrying the deadline that makes it a hold.
            withHold.restore(TicketStatus.RESERVED, null, "", "");
            tickets.update(withHold);
            commit();
            return withHold;
        } catch (SQLException | RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    /**
     * Returns a copy of a ticket with a hold deadline.
     *
     * <p>The ticket's own field is final once constructed, so the deadline is applied
     * by rebuilding it. This is the one place that rebuild is needed and it is kept
     * here rather than spreading a mutable deadline through the model.
     */
    private Ticket holdTicketWithExpiry(Ticket ticket, Instant expiresAt) {
        return new Ticket(ticket.getReference(), ticket.getEventId(), ticket.getTicketTypeId(),
                ticket.getAttendeeId(), ticket.getAttendeeName(), ticket.getAttendeeEmail(),
                ticket.getTierName(), ticket.getAccessTier(), ticket.getPricePaid(),
                ticket.getDiscountCode(), ticket.getIssuedAt(), expiresAt,
                ticket.getSeatLabel(), ticket.getNotes());
    }

    /**
     * Turns a hold into a sale.
     *
     * @return the ticket, now sold
     */
    public Ticket confirmHold(Ticket ticket, PaymentMethod method) throws SQLException {
        begin();
        try {
            Ticket sold = rebuildAs(ticket, TicketStatus.SOLD);
            tickets.update(sold);
            BigDecimal price = new BigDecimal(sold.getPricePaid());
            if (Money.isPositive(price)) {
                Payment payment = Payment.sale(Ids.payment(), sold.getEventId(),
                        sold.getReference(), sold.getAttendeeName(), price, method,
                        sold.getReference(), sold.getAttendeeId(), Instant.now());
                finance.insertPayment(payment);
            }
            commit();
            return sold;
        } catch (SQLException | RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    private Ticket rebuildAs(Ticket ticket, TicketStatus status) {
        Ticket copy = new Ticket(ticket.getReference(), ticket.getEventId(),
                ticket.getTicketTypeId(), ticket.getAttendeeId(), ticket.getAttendeeName(),
                ticket.getAttendeeEmail(), ticket.getTierName(), ticket.getAccessTier(),
                ticket.getPricePaid(), ticket.getDiscountCode(), ticket.getIssuedAt(),
                null, ticket.getSeatLabel(), ticket.getNotes());
        copy.restore(status, ticket.getCheckedInAt(), ticket.getGate(), ticket.getCheckedInBy());
        return copy;
    }

    /**
     * Refunds a ticket and reverses the money.
     *
     * <p>Both happen in one transaction and the ticket goes back on sale, because a
     * refunded seat that stays counted is a seat nobody can buy. The refund is
     * recorded as a reversal rather than by deleting the original payment, so the
     * sale is still visible in the ledger and the day's takings can be explained.
     */
    public void refundTicket(Ticket ticket, String reason) throws SQLException {
        begin();
        try {
            if (ticket.getStatus() != TicketStatus.SOLD) {
                throw new SQLException("Only a sold ticket can be refunded; "
                        + ticket.getReference() + " is " + ticket.getStatus().getLabel());
            }
            Ticket refunded = rebuildAs(ticket, TicketStatus.REFUNDED);
            tickets.update(refunded);

            Payment original = finance.findPaymentForTicket(ticket.getReference());
            if (original != null && Money.isPositive(original.getAmount())) {
                finance.insertPayment(original.refunded(Instant.now(), original.getAmount()));
            }

            Attendee attendee = people.findAttendee(ticket.getAttendeeId());
            if (attendee != null) {
                Registration registration =
                        people.findRegistrationOf(ticket.getEventId(), attendee.getId());
                if (registration != null && registration.hasPayment()) {
                    people.updateRegistration(registration.withPayment(Money.negate(
                            registration.getFeePaid())));
                }
            }

            if (!reason.isBlank()) {
                // Recorded against the reversal rather than the ticket, so the note
                // survives the ticket being reused by somebody else.
                finance.insertPayment(new Payment(Ids.payment(), ticket.getEventId(),
                        "REF-" + ticket.getReference(), ticket.getAttendeeName(), reason,
                        TransactionDirection.OUTFLOW, PaymentMethod.CREDIT, Money.ZERO,
                        Money.ZERO, "Refunds", ticket.getReference(), ticket.getAttendeeId(),
                        "", "Refund reason", Instant.now(), Instant.now(), false,
                        com.eventsuite.finance.PaymentStatus.PART_REFUNDED));
            }
            commit();
        } catch (SQLException | RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    /**
     * Releases holds whose deadline has passed.
     *
     * <p>Called when the dashboard opens and on a timer while the door is running.
     * Without it, a room fills with holds from people who went home and the event
     * sells out to nobody.
     *
     * @return how many were released
     */
    public int releaseExpiredHolds() throws SQLException {
        Instant now = Instant.now();
        List<Ticket> expired = tickets.findExpiredHolds(now);
        int released = 0;
        for (Ticket ticket : expired) {
            begin();
            try {
                tickets.update(rebuildAs(ticket, TicketStatus.AVAILABLE));
                commit();
                released++;
            } catch (SQLException failure) {
                rollback();
                // One bad row must not stop the rest being released, or a single
                // corrupt hold would block every future one.
            }
        }
        return released;
    }

    // ---------------------------------------------------------------- the door

    /**
     * Checks a ticket in at a gate, recording the scan either way.
     *
     * <p>The refusal cases matter as much as the admissions. Recording them is what
     * makes a duplicate ticket, a wrong door or a full room visible while it can
     * still be fixed, rather than showing up as an unexplained shortfall later.
     */
    public CheckIn checkIn(Ticket ticket, Event event, AccessGate gate, String operator,
                           Instant when) throws SQLException {
        // The stored event is the authority on how full the room is. A stale capacity
        // from the caller would let the door admit past a limit that was reached some
        // time ago, which is the exact moment the limit matters most.
        Event stored = events.findById(event.getId());
        int capacity = stored == null ? event.getCapacity() : stored.getCapacity();
        int throughTheDoor = ops.countAdmitted(event.getId());
        CheckInOutcome outcome = CheckIn.decide(ticket, gate, throughTheDoor >= capacity);

        CheckIn record;
        if (outcome == CheckInOutcome.ADMITTED) {
            record = CheckIn.of(Ids.checkIn(), event.getId(), ticket, gate, operator, when);
        } else {
            record = new CheckIn(Ids.checkIn(), event.getId(), ticket.getReference(),
                    ticket.getAttendeeName(), ticket.getAccessTier(),
                    gate == null ? "" : gate.getId(),
                    gate == null ? "Main entrance" : gate.getName(),
                    operator, when, outcome, false, "");
        }

        begin();
        try {
            ops.insertCheckIn(record);
            if (outcome == CheckInOutcome.ADMITTED) {
                Ticket admitted = ticket;
                admitted.checkIn(when, gate == null ? "" : gate.getName(), operator);
                tickets.update(admitted);
                markAttended(ticket);
            }
            commit();
        } catch (SQLException failure) {
            rollback();
            // The unique index on ticket reference plus event is what catches a second
            // scan. It arrives as a constraint violation rather than as an outcome,
            // so it is translated here into the outcome the operator expects to see.
            if (isDuplicateScan(failure)) {
                CheckIn duplicate = new CheckIn(Ids.checkIn(), event.getId(),
                        ticket.getReference(), ticket.getAttendeeName(), ticket.getAccessTier(),
                        gate == null ? "" : gate.getId(),
                        gate == null ? "Main entrance" : gate.getName(),
                        operator, when, CheckInOutcome.DUPLICATE, false,
                        "Already recorded at the door");
                ops.insertCheckIn(duplicate);
                return duplicate;
            }
            throw failure;
        }
        return record;
    }

    private boolean isDuplicateScan(SQLException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        return message.contains("UX_CHECKIN_ONCE") || message.contains("Duplicate");
    }

    /** Records that somebody turned up without a ticket, and admits them. */
    public CheckIn admitWithoutTicket(Event event, AccessGate gate, String holderName,
                                      String reason, String operator, Instant when)
            throws SQLException {
        CheckIn record = CheckIn.override(Ids.checkIn(), event.getId(), holderName, reason,
                operator, gate, when);
        begin();
        try {
            ops.insertCheckIn(record);
            commit();
        } catch (SQLException failure) {
            rollback();
            throw failure;
        }
        return record;
    }

    private void markAttended(Ticket ticket) throws SQLException {
        Registration registration =
                people.findRegistrationOf(ticket.getEventId(), ticket.getAttendeeId());
        if (registration == null) {
            return;
        }
        if (registration.getStatus() == RegistrationStatus.WALK_IN
                || registration.getStatus() == RegistrationStatus.CONFIRMED) {
            people.updateRegistration(registration
                    .withStatus(RegistrationStatus.ATTENDED));
        }
    }

    /** Opens and closes a gate. */
    public AccessGate setGateOpen(AccessGate gate, boolean open) {
        if (open) {
            gate.open();
        } else {
            gate.close();
        }
        return gate;
    }

    /** The gates an event needs, built fresh for each run of the door screen. */
    public List<AccessGate> gatesFor(Event event) {
        List<AccessGate> gates = new ArrayList<>();
        gates.add(AccessGate.mainEntrance(event.getId()));
        if (event.getScale().needsCommandCentre()) {
            gates.add(AccessGate.vipArea(event.getId(), "VIP lounge"));
            gates.add(AccessGate.staffOnly(event.getId(), "Production and green room"));
            gates.add(AccessGate.mediaPoint(event.getId()));
        } else if (event.getCategory().tracksSpeakers()) {
            gates.add(AccessGate.staffOnly(event.getId(), "Green room"));
        }
        return gates;
    }

    // ---------------------------------------------------------------- money out

    /**
     * Records a bill being paid, and moves the budget heading at the same time.
     *
     * <p>Both in one transaction, because a payment without its budget movement leaves
     * the budget showing money available that has already gone, which is how an event
     * commits to a supplier it cannot afford.
     */
    public Payment recordExpense(Event event, String payee, String description, BigDecimal amount,
                                 PaymentMethod method, BudgetCategory category,
                                 String invoiceId, boolean vatApplicable) throws SQLException {
        Payment payment = Payment.expense(Ids.payment(), event.getId(),
                "EXP-" + Ids.next("x").substring(2), payee, description, amount, method,
                category == null ? "" : category.name(), invoiceId, vatApplicable,
                Instant.now());
        begin();
        try {
            finance.insertPayment(payment);
            if (category != null) {
                finance.commitTo(event.getId(), category, amount);
                payment.moveTo(com.eventsuite.finance.PaymentStatus.SETTLED);
                finance.updatePayment(payment);
                finance.spendAgainst(event.getId(), category, amount);
            }
            commit();
        } catch (SQLException | RuntimeException failure) {
            rollback();
            throw failure;
        }
        return payment;
    }

    /**
     * Commits money to a supplier without paying it yet.
     *
     * <p>The difference between the two is the point of having both. Money that has
     * been promised but not paid is still gone as far as the budget is concerned.
     */
    public void commitBudget(Event event, BudgetCategory category, BigDecimal amount)
            throws SQLException {
        finance.commitTo(event.getId(), category, amount);
    }

    /** Changes a budget heading's allocation. */
    public void setBudgetAllocation(Event event, BudgetCategory category, BigDecimal planned)
            throws SQLException {
        BudgetLine line = finance.findBudgetLine(event.getId(), category);
        if (line == null) {
            finance.saveBudgetLine(BudgetLine.plannedFor(
                    event.getId() + ":" + category.name(), event.getId(), category, planned, ""));
            return;
        }
        finance.saveBudgetLine(line.withPlanned(planned));
    }

    /**
     * Issues an invoice with one line on it.
     *
     * <p>Short because that is the only shape an invoice is ever created in here. A
     * sponsor's invoice is one description and one figure; anything more is entered as
     * several invoices, which keeps the reconciliation arithmetic honest.
     */
    public Invoice issueInvoice(Event event, String billedTo, String billedEmail,
                                String description, BigDecimal amount, BigDecimal taxRate,
                                BudgetCategory category, LocalDate issuedOn, LocalDate dueOn,
                                String purchaseOrder) throws SQLException {
        int year = issuedOn.getYear();
        int sequence = finance.lastInvoiceSequence(event.getId(), year) + 1;
        String number = Ids.receiptNumber("INV", year, sequence);

        Invoice draft = Invoice.draft(Ids.invoice(), event.getId(), number, billedTo,
                billedEmail, issuedOn, dueOn);
        Invoice.Line line = new Invoice.Line(description, BigDecimal.ONE, amount, taxRate,
                category);
        Invoice invoice = draft.withLine(line).withStatus(InvoiceStatus.ISSUED);
        finance.insertInvoice(invoice);
        // No separate receivable payment is raised. The invoice itself is the record of
        // what is owed, and a second row saying the same thing would double every
        // outstanding total and make the reconciliation report a shortfall that does
        // not exist.
        return invoice;
    }

    /** Records money against an invoice. */
    public void payInvoice(Invoice invoice, BigDecimal amount, PaymentMethod method)
            throws SQLException {
        begin();
        try {
            Invoice updated = invoice.withPayment(amount, Instant.now());
            finance.updateInvoice(updated);
            Payment payment = new Payment(Ids.payment(), invoice.getEventId(),
                    updated.getNumber(), updated.getBilledTo(),
                    "Invoice " + updated.getNumber(), TransactionDirection.INFLOW, method,
                    amount, method.providerFeeOn(amount), "Sponsorship", "", "",
                    updated.getId(), "", Instant.now(), Instant.now(), true,
                    com.eventsuite.finance.PaymentStatus.SETTLED);
            finance.insertPayment(payment);
            commit();
        } catch (SQLException | RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    /**
     * Marks invoices overdue whose date has passed.
     *
     * <p>Run when the finance screen opens rather than by a background job, because
     * there is no background in a desktop application. The status is derived from the
     * date every time it is asked for, so it is never stale for long.
     */
    public int refreshOverdueInvoices(Event event) throws SQLException {
        LocalDate today = LocalDate.now();
        int updated = 0;
        for (Invoice invoice : finance.findInvoices(event.getId())) {
            if (invoice.getStatus().shouldBeOverdue(today, invoice.getDueOn(),
                    invoice.isPaid())) {
                finance.updateInvoice(invoice.withStatus(InvoiceStatus.OVERDUE));
                updated++;
            }
        }
        return updated;
    }

    // ---------------------------------------------------------------- speakers and kit

    /** Adds a speaker to an event. */
    public Speaker addSpeaker(Event event, String firstName, String lastName, String email,
                              String phone, String organisation, String jobTitle,
                              String sessionTitle, String sessionAbstract, String room,
                              LocalDateTime start, LocalDateTime end, BigDecimal fee,
                              BigDecimal travelBudget, String travelNotes, String dietary,
                              String accessibility, boolean needsHotel, String hotelNotes)
            throws SQLException {
        Speaker speaker = new Speaker(Ids.speaker(), event.getId(), firstName, lastName,
                email, phone, organisation, jobTitle, "", sessionTitle, sessionAbstract, room,
                start, end, fee, travelBudget, travelNotes, dietary, accessibility,
                needsHotel, hotelNotes, "",
                com.eventsuite.people.SpeakerStatus.PROSPECT);
        people.insertSpeaker(speaker);
        return speaker;
    }

    /** Saves an edited speaker. */
    public void saveSpeaker(Speaker speaker) throws SQLException {
        people.updateSpeaker(speaker);
    }

    /** Adds a kit item to the inventory. */
    public ResourceItem addResource(String name, ResourceItem.Kind kind, int quantity,
                                    BigDecimal unitCost, BigDecimal replacementValue,
                                    String supplier, String notes, boolean hired)
            throws SQLException {
        ResourceItem item = new ResourceItem(Ids.resource(), name, kind, quantity, unitCost,
                replacementValue, supplier, notes, hired);
        people.insertResource(item);
        return item;
    }

    /**
     * Books kit for an event, refusing if there is not enough of it free.
     *
     * <p>The availability check happens here rather than in the kit screen, because it
     * has to be the same check whichever screen booked the item, and two events in the
     * same week must not each believe they have the last forty chairs.
     */
    public ResourceBooking bookResource(Event event, ResourceItem item, int quantity,
                                        LocalDateTime from, LocalDateTime until,
                                        String purpose, BigDecimal cost) throws SQLException {
        int bookedOut = people.countBookedForEvent(item.getId(), event.getId());
        for (ResourceBooking existing : people.findBookingsForResource(item.getId())) {
            if (!existing.getEventId().equals(event.getId())
                    && from != null && existing.clashesWith(
                            ResourceBooking.forDay("probe", existing.getEventId(), item,
                                    existing.getQuantity(), existing.getFrom(), existing.getUntil(),
                                    "", Money.ZERO))) {
                bookedOut += existing.getQuantity();
            }
        }
        if (!item.canBook(quantity, bookedOut)) {
            throw new SQLException("Only " + item.available(bookedOut) + " " + item.getName()
                    + " left; " + quantity + " were requested");
        }
        ResourceBooking booking = ResourceBooking.forDay(Ids.booking(), event.getId(), item,
                quantity, from, until, purpose, cost);
        people.insertBooking(booking);
        return booking;
    }

    /** How many of an item are still free. */
    public int availability(ResourceItem item) {
        try {
            return item.available(people.countBookedOut(item.getId()));
        } catch (SQLException unreadable) {
            // Reporting no availability beats reporting a wrong number on a screen
            // somebody is about to make a purchase decision from.
            return 0;
        }
    }

    /** Records that kit has come back, and whether it was damaged. */
    public void returnResource(ResourceBooking booking, boolean damaged) throws SQLException {
        if (damaged) {
            booking.markDamaged();
        } else {
            booking.markReturned();
        }
        people.updateBooking(booking);
    }

    // ---------------------------------------------------------------- engagement and after

    /** Records something an attendee did. */
    public EngagementRecord recordEngagement(String eventId, String attendeeId,
                                             String attendeeName, EngagementType type,
                                             String subject, String detail, int score,
                                             String sessionId, String channel) throws SQLException {
        EngagementRecord record = new EngagementRecord(Ids.engagement(), eventId, attendeeId,
                attendeeName, type, subject, detail, score, Instant.now(), sessionId, channel);
        ops.insertEngagement(record);
        return record;
    }

    /** Records a feedback response. */
    public com.eventsuite.analytics.Feedback recordFeedback(String eventId, String attendeeId,
                                                             String attendeeName,
                                                             com.eventsuite.analytics.FeedbackTopic topic,
                                                             int rating, String comment,
                                                             String source) throws SQLException {
        com.eventsuite.analytics.Feedback response =
                new com.eventsuite.analytics.Feedback(Ids.feedback(), eventId, attendeeId,
                        attendeeName, topic, rating, comment, Instant.now(), source);
        ops.insertFeedback(response);
        return response;
    }

    /** Records a sustainability measure, replacing any existing figure for it. */
    public void recordSustainability(String eventId,
                                     SustainabilityMetric.Measure measure, BigDecimal quantity,
                                     String source, String note, boolean estimated)
            throws SQLException {
        SustainabilityMetric existing = ops.findMeasure(eventId, measure);
        if (existing != null) {
            ops.updateSustainability(new SustainabilityMetric(existing.getId(), eventId, measure,
                    quantity, source, note, estimated));
            return;
        }
        ops.insertSustainability(new SustainabilityMetric(Ids.sustainability(), eventId, measure,
                quantity, source, note, estimated));
    }

    /** Adds a campaign. */
    public com.eventsuite.marketing.Campaign addCampaign(String eventId, String name,
                                                         com.eventsuite.marketing.MarketingChannel channel,
                                                         BigDecimal budget, LocalDate startsOn,
                                                         LocalDate endsOn, String audience,
                                                         String message) throws SQLException {
        com.eventsuite.marketing.Campaign campaign =
                com.eventsuite.marketing.Campaign.planned(Ids.campaign(), eventId, name, channel,
                        budget, startsOn, endsOn);
        marketing.insertCampaign(campaign);
        return campaign;
    }

    /** Records what a campaign achieved and what it cost. */
    public void recordCampaignResults(com.eventsuite.marketing.Campaign campaign,
                                      BigDecimal spent, int reach, int clicks,
                                      int registrations) throws SQLException {
        campaign.spend(spent);
        campaign.recordResults(reach, clicks, registrations);
        campaign.moveTo(com.eventsuite.marketing.Campaign.Stage.FINISHED);
        marketing.updateCampaign(campaign);
    }

    /**
     * Adds a discount code, refusing anything that would give the tickets away.
     *
     * <p>Rejects a fixed-price code at or below zero, because that would make a free
     * ticket appear to be a discount and would show up in the reconciliation as a
     * sale of nothing.
     */
    public DiscountCode addDiscount(String eventId, String code,
                                    DiscountCode.Kind kind, BigDecimal value,
                                    int usageLimit, LocalDate validFrom, LocalDate validUntil,
                                    String appliesToTiers, String note) throws SQLException {
        if (kind == DiscountCode.Kind.FIXED_PRICE && value.signum() <= 0) {
            throw new SQLException("A fixed price of " + value.toPlainString()
                    + " is not a discount");
        }
        if (existsDiscountCode(eventId, code)) {
            throw new SQLException("The code " + code + " already exists for this event");
        }
        DiscountCode created = new DiscountCode(Ids.discount(), eventId, code, kind, value,
                usageLimit, validFrom, validUntil, appliesToTiers, note);
        marketing.insertDiscount(created);
        return created;
    }

    private boolean existsDiscountCode(String eventId, String code) throws SQLException {
        return marketing.findDiscountByCode(eventId, code) != null;
    }

    // ---------------------------------------------------------------- automation

    /**
     * Builds the facts a rule is tested against, from the stored records.
     *
     * <p>This is the bridge between the two halves of the system: the analytics layer
     * knows what is true, the workflow layer knows what to do about it, and this is
     * where one is translated into the other.
     */
    public WorkflowContext buildContext(String eventId) throws SQLException {
        Event event = events.findById(eventId);
        EventMetrics snapshot = metrics.forEvent(eventId);
        WorkflowContext context = WorkflowContext.of(eventId);

        if (event != null) {
            context = context.withDaysUntil(event.getStartDate())
                    .withHoursUntil(event.getStartsAt());
        }
        context = context.with(ConditionField.PERCENT_SOLD,
                        BigDecimal.valueOf(snapshot.getSellThroughPercent().doubleValue()))
                .with(ConditionField.TICKETS_SOLD, snapshot.getTicketsSold())
                .with(ConditionField.TICKETS_REMAINING, snapshot.getTicketsRemaining())
                .with(ConditionField.REVENUE_COLLECTED, snapshot.getNetRevenue())
                .with(ConditionField.BUDGET_SPENT, snapshot.getBudgetActual())
                .with(ConditionField.BUDGET_REMAINING, snapshot.getBudgetRemaining())
                .with(ConditionField.BUDGET_OVERSHOULD, snapshot.getBudgetHeadingsOverspent())
                .with(ConditionField.CONFIRMED_REGISTRATIONS, snapshot.getRegistrationsConfirmed())
                .with(ConditionField.WAITLISTED_REGISTRATIONS, snapshot.getRegistrationsWaitlisted())
                .with(ConditionField.WAITLIST_LENGTH, snapshot.getRegistrationsWaitlisted())
                .with(ConditionField.SPEAKERS_UNCONFIRMED, snapshot.getSpeakersUnconfirmed())
                .with(ConditionField.SPEAKERS_WITHDRAWN, snapshot.getSpeakersWithdrawn())
                .with(ConditionField.CHECKED_IN, snapshot.getTicketsCheckedIn())
                .with(ConditionField.FEEDBACK_SCORE,
                        BigDecimal.valueOf(snapshot.getAverageFeedback()).setScale(2,
                                java.math.RoundingMode.HALF_UP))
                .with(ConditionField.POOR_FEEDBACK_COUNT,
                        ops.countPoorFeedback(eventId, 2))
                .with(ConditionField.CARBON_KG, snapshot.getCarbonKg())
                .with(ConditionField.RECYCLING_RATE, snapshot.getRecyclingRate())
                .with(ConditionField.MARKETING_SPEND, snapshot.getMarketingSpend())
                .with(ConditionField.INVOICES_OVERDUE,
                        finance.findOverdueInvoices(eventId, LocalDate.now()).size())
                .with(ConditionField.MONEY_OUTSTANDING, snapshot.getOutstandingMoney());
        try {
            context = context.with(ConditionField.CHECKINS_LAST_HOUR,
                    ops.countArrivalsSince(eventId, Instant.now().minusSeconds(3600)));
        } catch (SQLException unreadable) {
            // Left at zero rather than failing the whole context: a missing arrivals
            // figure should not stop every other rule being evaluated.
            context = context.with(ConditionField.CHECKINS_LAST_HOUR, 0);
        }
        return context;
    }

    /**
     * Runs every rule that applies to an event, and records the outcome.
     *
     * <p>Both enabled and disabled rules are evaluated, so the log shows a disabled
     * rule being skipped. A rule that quietly disappears from the log looks deleted,
     * and somebody will go looking for it.
     *
     * @param allowConfirmed true when a person has approved the guarded actions
     * @return the log entries from this run, most interesting first
     */
    public List<com.eventsuite.workflow.AutomationLog> runAutomation(String eventId,
                                                                    boolean allowConfirmed)
            throws SQLException {
        WorkflowContext context = buildContext(eventId);
        WorkflowEngine engine = new WorkflowEngine(workflows.findRulesForEvent(eventId));
        // Ids come from the store's sequence rather than the engine's. An engine is
        // built fresh on every run, so a counter inside it would start at one each time
        // and two runs would mint the same log ids, which the store then rejects as
        // duplicate keys.
        List<com.eventsuite.workflow.AutomationLog> entries =
                engine.run(context, allowConfirmed, Ids.log());
        for (com.eventsuite.workflow.AutomationLog entry : entries) {
            workflows.insertLog(entry);
            WorkflowRule rule = findRuleIn(engine, entry.getRuleId());
            if (rule != null) {
                // Persisted from the engine's own copy, which this run has just updated.
                // Re-reading the rule from the store here would fetch the pre-run state
                // and write it straight back, so the run count would never stick and a
                // rule that may only fire once would go on firing for ever.
                workflows.updateRule(rule);
            }
        }
        workflows.trimLog(eventId);
        return entries;
    }

    /** Finds a rule among those the engine ran, carrying the state the run left it in. */
    private WorkflowRule findRuleIn(WorkflowEngine engine, String ruleId) {
        for (WorkflowRule rule : engine.getRules()) {
            if (rule.getId().equals(ruleId)) {
                return rule;
            }
        }
        return null;
    }

    /** Approves the guarded actions held back by the last run. */
    public List<com.eventsuite.workflow.AutomationLog> confirmAutomation(String eventId)
            throws SQLException {
        WorkflowContext context = buildContext(eventId);
        WorkflowEngine engine = new WorkflowEngine(workflows.findRulesForEvent(eventId));
        // The engine starts with nothing pending, so the guarded rules are evaluated
        // once more with confirmation granted rather than reading a queue that only
        // exists inside one engine instance.
        List<com.eventsuite.workflow.AutomationLog> entries =
                engine.run(context, true, Ids.log());
        for (com.eventsuite.workflow.AutomationLog entry : entries) {
            if (!entry.isActioned()) {
                continue;
            }
            workflows.insertLog(entry);
            WorkflowRule rule = findRuleIn(engine, entry.getRuleId());
            if (rule != null) {
                workflows.updateRule(rule);
                applyGuardedAction(eventId, rule);
            }
        }
        return entries;
    }

    /**
     * Carries out an action that changes money or capacity.
     *
     * <p>Separate from the engine on purpose. The engine decides and logs; this
     * changes the world. Keeping them apart means the rules can be tested without a
     * database, and the irreversible actions are in one short list a reader can audit.
     */
    private void applyGuardedAction(String eventId, WorkflowRule rule) throws SQLException {
        Event event = events.findById(eventId);
        if (event == null) {
            return;
        }
        switch (rule.getAction()) {
            case MARK_SOLD_OUT, HOLD_REMAINING_TICKETS -> {
                if (event.getStatus().canMoveTo(EventStatus.SOLD_OUT)) {
                    event.restoreStatus(EventStatus.SOLD_OUT);
                    events.update(event);
                }
            }
            case PROMOTE_WAITLIST -> promoteFromWaitingList(event);
            case ISSUE_REFUND -> {
                // Refunds are not done automatically even when confirmed, because the
                // amount is not implied by the condition. The task is raised instead
                // and the list of who is owed is left to a person.
                workflows.insertLog(com.eventsuite.workflow.AutomationLog.fired(
                        Ids.log(), rule, buildContext(eventId), BigDecimal.ZERO));
            }
            default -> {
                // Every other action is either a message or a task, both of which are
                // represented by the log entry itself.
            }
        }
    }

    /**
     * Moves people off the waiting list into the places freed up.
     *
     * <p>Confirms as many as there is room for and leaves the rest waiting. It does
     * not send the offers or take the money — it makes the place held rather than
     * offered, because holding a place and claiming the money are separate acts and
     * only a person should do the second.
     */
    public int promoteFromWaitingList(Event event) throws SQLException {
        EventMetrics snapshot = metrics.forEvent(eventId(event));
        int room = event.getSellableCapacity() - snapshot.getTicketsSold();
        if (room <= 0) {
            return 0;
        }
        int promoted = 0;
        for (Registration waiting : people.findRegistrationsByStatus(event.getId(),
                RegistrationStatus.WAITLISTED)) {
            if (room <= 0) {
                break;
            }
            waiting.moveTo(RegistrationStatus.CONFIRMED);
            people.updateRegistration(waiting);
            room--;
            promoted++;
        }
        return promoted;
    }

    private String eventId(Event event) {
        return event.getId();
    }

    /** Adds a rule to an event. */
    public void addRule(WorkflowRule rule) throws SQLException {
        workflows.insertRule(rule);
    }

    /** Turns a rule on or off, keeping its history. */
    public void setRuleEnabled(WorkflowRule rule, boolean enabled) throws SQLException {
        workflows.updateRule(rule.withEnabled(enabled));
    }

    /** Removes a rule. */
    public boolean deleteRule(String ruleId) throws SQLException {
        return workflows.deleteRule(ruleId);
    }

    // ---------------------------------------------------------------- reconciliation

    /**
     * Builds the post-event reconciliation report.
     *
     * <p>Expected revenue is what the tickets and invoices say should have been taken;
     * recorded revenue is what the payment records say was. The difference between
     * them, judged against the money handled, is the report.
     */
    public ReconciliationReport reconcile(Event event) throws SQLException {
        LocalDate periodStart = event.getStartDate();
        LocalDate periodEnd = event.getEndDate();

        // Both sides are measured gross of processing fees. Fees are a known cost and
        // are reported on their own line; treating them as a shortfall would report
        // every card sale as money missing and bury a real discrepancy in the rounding.
        java.math.BigDecimal expected = tickets.sumPaid(event.getId());
        java.math.BigDecimal recorded = finance.sumGrossInflow(event.getId());
        java.math.BigDecimal costs = finance.sumSettledOutflow(event.getId());

        ReconciliationReport.Builder builder = ReconciliationReport.builder(
                event.getId(), event.getName(), periodStart, periodEnd)
                .revenue(expected, recorded)
                .costs(costs)
                .outstanding(finance.sumOutstandingInflow(event.getId())
                        .add(finance.sumInvoiceOutstanding(event.getId())))
                .unmatchedPayments(finance.countUnmatchedPayments(event.getId()))
                .invoices(finance.findInvoices(event.getId()).size(),
                        finance.countInvoicesByStatus(event.getId(), InvoiceStatus.PAID),
                        finance.findOverdueInvoices(event.getId(), LocalDate.now()).size())
                .unmatchedPayments(finance.countUnmatchedPayments(event.getId()));

        ReconciliationReport report = builder.build()
                .withProviderFees(finance.sumProviderFees(event.getId()));

        // Each discrepancy is a specific thing that does not add up, named so it can be
        // looked up rather than merely totalled.
        java.math.BigDecimal variance = report.getRevenueVariance();
        if (variance.signum() != 0) {
            report = builder.addDiscrepancy(new ReconciliationReport.Discrepancy(
                    "Ticket takings do not match the payment records",
                    expected, recorded, null, "")).build()
                    .withProviderFees(finance.sumProviderFees(event.getId()));
        }
        for (com.eventsuite.finance.BudgetLine line
                : finance.findOverspentHeadings(event.getId())) {
            report = ReconciliationReport.builder(event.getId(), event.getName(),
                            periodStart, periodEnd)
                    .revenue(expected, recorded)
                    .costs(costs)
                    .invoices(finance.findInvoices(event.getId()).size(),
                            finance.countInvoicesByStatus(event.getId(), InvoiceStatus.PAID),
                            finance.findOverdueInvoices(event.getId(), LocalDate.now()).size())
                    .unmatchedPayments(finance.countUnmatchedPayments(event.getId()))
                    .addDiscrepancy(new ReconciliationReport.Discrepancy(
                            line.getCategory().getLabel() + " spent over its allocation",
                            line.getPlanned(),
                            com.eventsuite.finance.Money.sum(line.getCommitted(), line.getActual()),
                            line.getCategory(), ""))
                    .build()
                    .withProviderFees(finance.sumProviderFees(event.getId()));
        }
        return report;
    }

    /** Writes the end-of-event report to a file, for keeping with the accounts. */
    public String writeEventReport(Event event) throws IOException, SQLException {
        StringBuilder text = new StringBuilder();
        text.append(event.getName()).append('\n');
        text.append(event.getCategory().getLabel()).append("  |  ")
                .append(event.getDateRange()).append("  |  ").append(event.getVenueName())
                .append("\n\n");

        EventMetrics snapshot = metrics.forEvent(event.getId());
        text.append("ATTENDANCE\n");
        text.append("  Tickets sold:      ").append(snapshot.getTicketsSold()).append('\n');
        text.append("  Checked in:        ").append(snapshot.getTicketsCheckedIn()).append('\n');
        text.append("  Attendance rate:   ").append(snapshot.getAttendanceRate())
                .append("%\n");
        text.append("  Sell-through:      ").append(snapshot.getSellThroughPercent())
                .append("%\n");
        text.append("  Unique attendees:  ").append(snapshot.getUniqueAttendees()).append('\n');
        text.append("  Door problems:     ").append(snapshot.getRejectedEntries()).append('\n');
        text.append('\n');

        text.append("MONEY\n");
        text.append("  Ticket takings:    ").append(Money.format(snapshot.getGrossRevenue()))
                .append('\n');
        text.append("  Processor fees:    ").append(Money.format(snapshot.getProviderFees()))
                .append('\n');
        text.append("  Other income:      ").append(Money.format(snapshot.getOtherRevenue()))
                .append('\n');
        text.append("  Costs:             ").append(Money.format(snapshot.getBudgetActual()))
                .append('\n');
        text.append("  Outstanding:       ").append(Money.format(snapshot.getOutstandingMoney()))
                .append('\n');
        text.append("  Return:            ")
                .append(snapshot.getRoi().getReturnVerdict()).append(" - ")
                .append(String.format(java.util.Locale.UK, "%.2fx on ticket sales",
                        snapshot.getRoi().getTicketReturnMultiple())).append('\n');
        text.append('\n');

        text.append("FEEDBACK\n");
        text.append("  Responses:         ").append(snapshot.getFeedbackResponses()).append('\n');
        text.append("  Average rating:    ").append(
                String.format(java.util.Locale.UK, "%.1f", snapshot.getAverageFeedback()))
                .append(" of 5\n");
        text.append("  Would recommend:   ").append(
                String.format(java.util.Locale.UK, "%.0f%%", snapshot.getRecommendationRate() * 100))
                .append('\n');
        text.append("  Promoter score:    ").append(snapshot.getPromoterScore()).append('\n');
        text.append('\n');

        text.append("SUSTAINABILITY\n");
        text.append("  Carbon footprint:  ").append(snapshot.getCarbonLabel()).append('\n');
        text.append("  Recycled:          ").append(snapshot.getRecyclingRate())
                .append("%\n");
        text.append('\n');

        text.append(reconcile(event).render());

        java.nio.file.Path file = java.nio.file.Path.of("event-report-"
                + event.getId() + ".txt");
        java.nio.file.Files.writeString(file, text.toString());
        return file.toString();
    }

    // ---------------------------------------------------------------- summaries

    /** Events grouped by category, for the catalogue. */
    public Map<EventCategory, List<Event>> catalogue() throws SQLException {
        return events.groupByCategory();
    }

    /** Events coming up, for the front screen. */
    public List<Event> upcoming() throws SQLException {
        return events.findFrom(LocalDate.now());
    }

    /** Everything needing attention on an event today. */
    public List<String> attentionItems(String eventId) throws SQLException {
        return metrics.attentionItems(eventId, LocalDate.now());
    }

    /** The arrival curve for an event. */
    public List<TrendPoint> attendanceTrend(String eventId, boolean live) throws SQLException {
        return metrics.attendanceTrend(eventId, live);
    }

    // ---------------------------------------------------------------- transactions

    /**
     * Opens a transaction and takes the write lock.
     *
     * <p>The lock is held until the transaction commits or rolls back, so a dashboard
     * refreshing every few seconds cannot read half of a sale. Without it a reader can
     * land between the ticket insert and the payment insert and show a ticket with no
     * money against it, which is exactly the kind of wrong figure nobody trusts twice.
     */
    private void begin() throws SQLException {
        transactionLock.lock();
        try {
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
        } catch (SQLException failure) {
            transactionLock.unlock();
            throw failure;
        }
    }

    private void commit() throws SQLException {
        try {
            if (!connection.getAutoCommit()) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } finally {
            transactionLock.unlock();
        }
    }

    private void rollback() {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException cannotRollBack) {
            // The original failure is the one worth reporting. A rollback that also
            // fails usually means the connection is already broken, and saying so
            // would only bury the real cause.
        } finally {
            // Released even when the rollback failed, or every later write would queue
            // behind a lock nobody will ever drop.
            transactionLock.unlock();
        }
    }

    /**
     * The lock guarding the whole connection.
     *
     * <p>Exposed so the panels can hold it across a refresh. It is the same lock the
     * write transactions take, which is what stops a read from interleaving with one.
     */
    public Lock getReadLock() {
        return transactionLock;
    }

    /** Restores autocommit. Called when the window closes. */
    public void resetAutoCommit() throws SQLException {
        transactionLock.lock();
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } finally {
            transactionLock.unlock();
        }
    }

    @Override
    public void close() {
        try {
            resetAutoCommit();
        } catch (SQLException notIdle) {
            // Closing anyway: the caller has already decided the connection is done with.
        }
    }
}
