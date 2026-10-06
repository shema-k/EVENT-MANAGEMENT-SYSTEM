package com.eventsuite.data;

import com.eventsuite.finance.BudgetCategory;
import com.eventsuite.finance.BudgetLine;
import com.eventsuite.finance.Invoice;
import com.eventsuite.finance.InvoiceStatus;
import com.eventsuite.finance.Money;
import com.eventsuite.finance.Payment;
import com.eventsuite.finance.PaymentMethod;
import com.eventsuite.finance.PaymentStatus;
import com.eventsuite.finance.TransactionDirection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores and reads the money: payments, invoices and budget lines.
 *
 * <p>These three are kept together because reconciliation reads all of them at once
 * and needs them to agree. Kept apart as tables because they answer different
 * questions: a payment says money moved, an invoice says money was asked for, and a
 * budget line says what was allowed. Collapsing them would destroy the distinction
 * between a receivable and a receipt, which is the distinction the whole module rests
 * on.
 *
 * <p>Invoice lines are stored as one text column rather than a child table. They are
 * only ever read as part of their invoice and never filtered on, so a second table
 * would add a join and an ordering rule for no query that exists. They are written in
 * a fixed format with a separator that cannot appear in a description, which is what
 * makes splitting them back safe.
 */
public final class FinanceStore {

    /** The unit separator used between invoice line fields. */
    private static final String FIELD = "\u001F";

    /** The record separator used between invoice lines. */
    private static final String RECORD = "\u001E";

    private static final String PAYMENT_COLUMNS =
            "id, event_id, reference, counterparty, description, direction, method, amount,"
                    + " provider_fee, category, ticket_ref, attendee_id, invoice_id, note, status,"
                    + " raised_at, settled_at, vat_applicable";

    private static final String INVOICE_COLUMNS =
            "id, event_id, number, billed_to, billed_email, address, purchase_order, issued_on,"
                    + " due_on, notes, lines, paid_amount, paid_at, status";

    private final Connection connection;

    public FinanceStore(Connection connection) {
        this.connection = connection;
    }

    // ---------------------------------------------------------------- payments

    /** Inserts a payment. */
    public void insertPayment(Payment payment) throws SQLException {
        Sql.update(connection,
                "INSERT INTO payments (" + PAYMENT_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, payment.getId());
                    Sql.setText(statement, 2, payment.getEventId());
                    Sql.setText(statement, 3, payment.getReference());
                    Sql.setText(statement, 4, payment.getCounterparty());
                    Sql.setText(statement, 5, payment.getDescription());
                    Sql.setText(statement, 6, payment.getDirection().name());
                    Sql.setText(statement, 7, payment.getMethod().name());
                    Sql.setMoney(statement, 8, payment.getAmount());
                    Sql.setMoney(statement, 9, payment.getProviderFee());
                    Sql.setText(statement, 10, payment.getCategory());
                    Sql.setText(statement, 11, payment.getTicketReference());
                    Sql.setText(statement, 12, payment.getAttendeeId());
                    Sql.setText(statement, 13, payment.getInvoiceId());
                    Sql.setText(statement, 14, payment.getNote());
                    Sql.setText(statement, 15, payment.getStatus().name());
                    Sql.setInstant(statement, 16, payment.getRaisedAt());
                    Sql.setInstant(statement, 17, payment.getSettledAt());
                    Sql.setFlag(statement, 18, payment.isVatApplicable());
                });
    }

    /** Rewrites a payment. */
    public void updatePayment(Payment payment) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE payments SET reference=?, counterparty=?, description=?, direction=?,"
                        + " method=?, amount=?, provider_fee=?, category=?, ticket_ref=?,"
                        + " attendee_id=?, invoice_id=?, note=?, status=?, raised_at=?,"
                        + " settled_at=?, vat_applicable=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, payment.getReference());
                    Sql.setText(statement, 2, payment.getCounterparty());
                    Sql.setText(statement, 3, payment.getDescription());
                    Sql.setText(statement, 4, payment.getDirection().name());
                    Sql.setText(statement, 5, payment.getMethod().name());
                    Sql.setMoney(statement, 6, payment.getAmount());
                    Sql.setMoney(statement, 7, payment.getProviderFee());
                    Sql.setText(statement, 8, payment.getCategory());
                    Sql.setText(statement, 9, payment.getTicketReference());
                    Sql.setText(statement, 10, payment.getAttendeeId());
                    Sql.setText(statement, 11, payment.getInvoiceId());
                    Sql.setText(statement, 12, payment.getNote());
                    Sql.setText(statement, 13, payment.getStatus().name());
                    Sql.setInstant(statement, 14, payment.getRaisedAt());
                    Sql.setInstant(statement, 15, payment.getSettledAt());
                    Sql.setFlag(statement, 16, payment.isVatApplicable());
                    Sql.setText(statement, 17, payment.getId());
                });
        if (changed == 0) {
            throw new SQLException("No payment to update with id " + payment.getId());
        }
    }
    /**
     * The payment recorded against a ticket, if there is one.
     *
     * <p>Used to find a sale twice over without duplicating it — the reconciliation
     * needs to know whether a ticket reference already carries money.
     */
    public Payment findPaymentForTicket(String ticketReference) throws SQLException {
        if (ticketReference == null || ticketReference.isBlank()) {
            return null;
        }
        return Sql.queryOne(connection,
                "SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE ticket_ref=? ORDER BY raised_at",
                statement -> Sql.setText(statement, 1, ticketReference),
                FinanceStore::readPayment);
    }

    /** Every payment on an event, newest first. */
    public List<Payment> findPayments(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE event_id=?"
                        + " ORDER BY COALESCE(settled_at, raised_at) DESC",
                statement -> Sql.setText(statement, 1, eventId), FinanceStore::readPayment);
    }
    /** Payments on an event going one way. */
    public List<Payment> findPaymentsByDirection(String eventId, TransactionDirection direction)
            throws SQLException {
        return Sql.query(connection,
                "SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE event_id=? AND direction=?"
                        + " ORDER BY COALESCE(settled_at, raised_at) DESC",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, direction.name());
                }, FinanceStore::readPayment);
    }

    /**
     * Money actually kept, coming in.
     *
     * <p>Refunds are subtracted. A refund is recorded as a separate row rather than by
     * changing the original sale, so the sale is still there and still settled — which
     * is right, because it did happen and the day's takings should be explainable. But
     * adding the two together would count money that was handed back as money that
     * was kept, so the reversals are taken off here.
     *
     * <p>This is the figure the dashboards use as revenue. A part-refunded payment
     * contributes only what was kept, and a wholly refunded one contributes nothing.
     */
    public BigDecimal sumSettledInflow(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT COALESCE(SUM(CASE WHEN status IN ('SETTLED','PART_REFUNDED')"
                        + " THEN amount - provider_fee ELSE -(amount - provider_fee) END), 0)"
                        + " FROM payments WHERE event_id=? AND direction='INFLOW'"
                        + " AND status IN ('SETTLED','PART_REFUNDED','REFUNDED')",
                statement -> Sql.setText(statement, 1, eventId));
    }
    /**
     * Money taken at face value, before processing fees and before refunds.
     *
     * <p>This is the figure that can be compared against what the tickets say was
     * charged. Net of fees it would differ from the ticket prices by exactly the
     * processing cost, and a reconciliation would report that as money missing.
     */
    public BigDecimal sumGrossInflow(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT COALESCE(SUM(amount), 0) FROM payments"
                        + " WHERE event_id=? AND direction='INFLOW'"
                        + " AND status IN ('SETTLED','PART_REFUNDED')",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Settled money going out on an event. */
    public BigDecimal sumSettledOutflow(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT SUM(amount) FROM payments"
                        + " WHERE event_id=? AND direction='OUTFLOW' AND status='SETTLED'",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Money promised but not yet received. */
    public BigDecimal sumOutstandingInflow(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT SUM(amount) FROM payments"
                        + " WHERE event_id=? AND direction='INFLOW' AND status='PENDING'",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** What processors kept across an event's sales. */
    public BigDecimal sumProviderFees(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT SUM(provider_fee) FROM payments WHERE event_id=? AND direction='INFLOW'",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /**
     * Ticket takings before processor fees, with refunds taken off.
     *
     * <p>Gross in the sense of "what the tickets were worth", not "what was collected":
     * this feeds the average ticket price, and averaging in a ticket somebody gave back
     * would understate what the event actually charges.
     */
    public BigDecimal sumGrossTicketRevenue(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT COALESCE(SUM(CASE WHEN status IN ('SETTLED','PART_REFUNDED')"
                        + " THEN amount ELSE -(amount - provider_fee) END), 0) FROM payments"
                        + " WHERE event_id=? AND direction='INFLOW'"
                        + " AND status IN ('SETTLED','PART_REFUNDED','REFUNDED')"
                        + " AND category='Tickets'",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Non-ticket money in: sponsorship, exhibitors, grants, net of anything returned. */
    public BigDecimal sumOtherInflow(String eventId) throws SQLException {
        return Sql.sum(connection,
                "SELECT COALESCE(SUM(CASE WHEN status IN ('SETTLED','PART_REFUNDED')"
                        + " THEN amount - provider_fee ELSE -(amount - provider_fee) END), 0) FROM payments"
                        + " WHERE event_id=? AND direction='INFLOW'"
                        + " AND status IN ('SETTLED','PART_REFUNDED','REFUNDED')"
                        + " AND (category<>'Tickets' OR category IS NULL)",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** What has been spent through a budget heading. */
    public BigDecimal sumOutflowByCategory(String eventId, BudgetCategory category)
            throws SQLException {
        return Sql.sum(connection,
                "SELECT SUM(amount) FROM payments WHERE event_id=? AND direction='OUTFLOW'"
                        + " AND status='SETTLED' AND category=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, category.name());
                });
    }
    /**
     * How many payments cannot be matched to a ticket or an invoice.
     *
     * <p>Cash sales are the normal reason, and they are legitimate. The count matters
     * because a growing number of unmatched payments usually means something is not
     * being recorded rather than that a lot of cash changed hands.
     */
    public int countUnmatchedPayments(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM payments WHERE event_id=?"
                        + " AND (ticket_ref IS NULL OR ticket_ref='')"
                        + " AND (invoice_id IS NULL OR invoice_id='')"
                        + " AND method<>'CASH'",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /**
     * Ticket takings over time, cumulative.
     *
     * <p>Ordered by settlement date and accumulated in the query, so the revenue trend
     * on a dashboard is one pass rather than a read of every payment.
     */
    public List<com.eventsuite.analytics.TrendPoint> revenueTrend(String eventId)
            throws SQLException {
        List<com.eventsuite.analytics.TrendPoint> points = new ArrayList<>();
        List<Payment> settled = new ArrayList<>();
        for (Payment payment : findPaymentsByDirection(eventId, TransactionDirection.INFLOW)) {
            if (payment.isCleared()) {
                settled.add(payment);
            }
        }
        // Oldest first, so the running total reads left to right.
        settled.sort(java.util.Comparator.comparing(Payment::getRaisedAt));
        BigDecimal running = Money.ZERO;
        for (Payment payment : settled) {
            running = Money.sum(running, payment.getNetAmount());
            points.add(new com.eventsuite.analytics.TrendPoint(
                    payment.getRaisedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                    running.doubleValue()));
        }
        return points;
    }

    /** Money taken by method, for the payments-by-method chart. */
    public BigDecimal sumInflowByMethod(String eventId, PaymentMethod method) throws SQLException {
        return Sql.sum(connection,
                "SELECT COALESCE(SUM(CASE WHEN status IN ('SETTLED','PART_REFUNDED')"
                        + " THEN amount - provider_fee ELSE -(amount - provider_fee) END), 0) FROM payments"
                        + " WHERE event_id=?"
                        + " AND direction='INFLOW' AND method=?"
                        + " AND status IN ('SETTLED','PART_REFUNDED','REFUNDED')",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, method.name());
                });
    }

    // ---------------------------------------------------------------- invoices

    /** Inserts an invoice. */
    public void insertInvoice(Invoice invoice) throws SQLException {
        Sql.update(connection,
                "INSERT INTO invoices (" + INVOICE_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, invoice.getId());
                    Sql.setText(statement, 2, invoice.getEventId());
                    Sql.setText(statement, 3, invoice.getNumber());
                    Sql.setText(statement, 4, invoice.getBilledTo());
                    Sql.setText(statement, 5, invoice.getBilledEmail());
                    Sql.setText(statement, 6, invoice.getBillingAddress());
                    Sql.setText(statement, 7, invoice.getPurchaseOrder());
                    Sql.setDate(statement, 8, invoice.getIssuedOn());
                    Sql.setDate(statement, 9, invoice.getDueOn());
                    Sql.setText(statement, 10, invoice.getNotes());
                    Sql.setText(statement, 11, encodeLines(invoice));
                    Sql.setMoney(statement, 12, invoice.getPaidAmount());
                    Sql.setInstant(statement, 13, invoice.getPaidAt());
                    Sql.setText(statement, 14, invoice.getStatus().name());
                });
    }

    /** Rewrites an invoice. */
    public void updateInvoice(Invoice invoice) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE invoices SET number=?, billed_to=?, billed_email=?, address=?,"
                        + " purchase_order=?, issued_on=?, due_on=?, notes=?, lines=?,"
                        + " paid_amount=?, paid_at=?, status=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, invoice.getNumber());
                    Sql.setText(statement, 2, invoice.getBilledTo());
                    Sql.setText(statement, 3, invoice.getBilledEmail());
                    Sql.setText(statement, 4, invoice.getBillingAddress());
                    Sql.setText(statement, 5, invoice.getPurchaseOrder());
                    Sql.setDate(statement, 6, invoice.getIssuedOn());
                    Sql.setDate(statement, 7, invoice.getDueOn());
                    Sql.setText(statement, 8, invoice.getNotes());
                    Sql.setText(statement, 9, encodeLines(invoice));
                    Sql.setMoney(statement, 10, invoice.getPaidAmount());
                    Sql.setInstant(statement, 11, invoice.getPaidAt());
                    Sql.setText(statement, 12, invoice.getStatus().name());
                    Sql.setText(statement, 13, invoice.getId());
                });
        if (changed == 0) {
            throw new SQLException("No invoice to update with id " + invoice.getId());
        }
    }

    /** One invoice, or null. */
    public Invoice findInvoice(String invoiceId) throws SQLException {
        return Sql.queryOne(connection,
                "SELECT " + INVOICE_COLUMNS + " FROM invoices WHERE id=?",
                statement -> Sql.setText(statement, 1, invoiceId), FinanceStore::readInvoice);
    }

    /** Every invoice on an event. */
    public List<Invoice> findInvoices(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + INVOICE_COLUMNS + " FROM invoices WHERE event_id=? ORDER BY issued_on",
                statement -> Sql.setText(statement, 1, eventId), FinanceStore::readInvoice);
    }
    /** Invoices on an event whose due date has passed with money outstanding. */
    public List<Invoice> findOverdueInvoices(String eventId, LocalDate today) throws SQLException {
        return Sql.query(connection,
                "SELECT " + INVOICE_COLUMNS + " FROM invoices WHERE event_id=?"
                        + " AND status IN ('ISSUED','PART_PAID','OVERDUE') AND due_on<?"
                        + " ORDER BY due_on",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setDate(statement, 2, today);
                }, FinanceStore::readInvoice);
    }

    /** The highest invoice number issued this year, for numbering the next one. */
    public int lastInvoiceSequence(String eventId, int year) throws SQLException {
        String prefix = "INV-" + year + "-";
        List<Invoice> thisYear = Sql.query(connection,
                "SELECT " + INVOICE_COLUMNS + " FROM invoices WHERE event_id=? AND number LIKE ?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, prefix + "%");
                }, FinanceStore::readInvoice);
        int highest = 0;
        for (Invoice invoice : thisYear) {
            String number = invoice.getNumber();
            if (number.startsWith(prefix)) {
                try {
                    highest = Math.max(highest, Integer.parseInt(number.substring(prefix.length())));
                } catch (NumberFormatException unusualNumber) {
                    // A hand-typed number such as INV-2026-ADV is not in the sequence.
                    // It is left alone rather than treated as the highest.
                }
            }
        }
        return highest;
    }

    /** Money invoiced but not paid, across an event's invoices. */
    public BigDecimal sumInvoiceOutstanding(String eventId) throws SQLException {
        BigDecimal total = Money.ZERO;
        for (Invoice invoice : findInvoices(eventId)) {
            if (invoice.getStatus() != InvoiceStatus.VOID) {
                total = Money.sum(total, invoice.getOutstanding());
            }
        }
        return total;
    }

    /** How many invoices an event has in a given state. */
    public int countInvoicesByStatus(String eventId, InvoiceStatus status) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM invoices WHERE event_id=? AND status=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, status.name());
                });
    }

    // ---------------------------------------------------------------- budget

    /**
     * Saves a budget line, inserting or updating as needed.
     *
     * <p>The primary key is the event and category together rather than a generated
     * id. There is one heading per category per event by definition, so making that
     * the key means a second write for the same heading updates it instead of
     * quietly creating a duplicate that splits the figure in two.
     */
    public void saveBudgetLine(BudgetLine line) throws SQLException {
        String key = line.getEventId() + ":" + line.getCategory().name();
        boolean exists = Sql.count(connection,
                "SELECT COUNT(*) FROM budget_lines WHERE id=?", statement -> Sql.setText(statement, 1, key)) > 0;
        if (exists) {
            Sql.update(connection,
                    "UPDATE budget_lines SET planned=?, committed=?, actual=?, note=?"
                            + " WHERE id=?",
                    statement -> {
                        Sql.setMoney(statement, 1, line.getPlanned());
                        Sql.setMoney(statement, 2, line.getCommitted());
                        Sql.setMoney(statement, 3, line.getActual());
                        Sql.setText(statement, 4, line.getNote());
                        Sql.setText(statement, 5, key);
                    });
        } else {
            Sql.update(connection,
                    "INSERT INTO budget_lines (id, event_id, category, planned, committed,"
                            + " actual, note) VALUES (?,?,?,?,?,?,?)",
                    statement -> {
                        Sql.setText(statement, 1, key);
                        Sql.setText(statement, 2, line.getEventId());
                        Sql.setText(statement, 3, line.getCategory().name());
                        Sql.setMoney(statement, 4, line.getPlanned());
                        Sql.setMoney(statement, 5, line.getCommitted());
                        Sql.setMoney(statement, 6, line.getActual());
                        Sql.setText(statement, 7, line.getNote());
                    });
        }
    }

    /** One budget heading, or null. */
    public BudgetLine findBudgetLine(String eventId, BudgetCategory category) throws SQLException {
        return Sql.queryOne(connection,
                "SELECT id, event_id, category, planned, committed, actual, note"
                        + " FROM budget_lines WHERE id=?",
                statement -> Sql.setText(statement, 1, eventId + ":" + category.name()),
                FinanceStore::readBudgetLine);
    }

    /** Every budget heading on an event. */
    public List<BudgetLine> findBudgetLines(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT id, event_id, category, planned, committed, actual, note"
                        + " FROM budget_lines WHERE event_id=? ORDER BY category",
                statement -> Sql.setText(statement, 1, eventId), FinanceStore::readBudgetLine);
    }

    /** Records a commitment against a heading without changing its allocation. */
    public void commitTo(String eventId, BudgetCategory category, BigDecimal amount)
            throws SQLException {
        BudgetLine line = findBudgetLine(eventId, category);
        if (line == null) {
            line = BudgetLine.plannedFor(eventId + ":" + category.name(), eventId, category,
                    Money.ZERO, "");
        }
        line.commit(amount);
        saveBudgetLine(line);
    }

    /**
     * Records spend against a heading.
     *
     * <p>The payment is the record of truth and the budget line is a running total,
     * so the two are updated together in the caller's transaction. If the payment
     * save fails the budget change rolls back with it.
     */
    public void spendAgainst(String eventId, BudgetCategory category, BigDecimal amount)
            throws SQLException {
        BudgetLine line = findBudgetLine(eventId, category);
        if (line == null) {
            line = BudgetLine.plannedFor(eventId + ":" + category.name(), eventId, category,
                    Money.ZERO, "");
        }
        line.spend(amount);
        saveBudgetLine(line);
    }

    /** Total planned across an event's headings. */
    public BigDecimal sumBudgetPlanned(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(planned) FROM budget_lines WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Total committed across an event's headings. */
    public BigDecimal sumBudgetCommitted(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(committed) FROM budget_lines WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Total spent across an event's headings. */
    public BigDecimal sumBudgetActual(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(actual) FROM budget_lines WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many of an event's headings have gone over their allocation. */
    public int countOverspentHeadings(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM budget_lines WHERE event_id=? AND committed+actual>planned",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Headings that have gone over, for the alert list. */
    public List<BudgetLine> findOverspentHeadings(String eventId) throws SQLException {
        List<BudgetLine> all = findBudgetLines(eventId);
        List<BudgetLine> overspent = new ArrayList<>();
        for (BudgetLine line : all) {
            if (line.isOverspent()) {
                overspent.add(line);
            }
        }
        return overspent;
    }

    // ---------------------------------------------------------------- line encoding

    /**
     * Packs an invoice's lines into one column.
     *
     * <p>Field and record separators are the ASCII control characters, which cannot
     * be typed into a description field. That is what makes the format safe rather
     * than merely convenient — a pipe-separated format would break on a description
     * containing a pipe, which descriptions do.
     */
    private static String encodeLines(Invoice invoice) {
        StringBuilder packed = new StringBuilder();
        for (Invoice.Line line : invoice.getLines()) {
            if (packed.length() > 0) {
                packed.append(RECORD);
            }
            packed.append(line.getDescription())
                    .append(FIELD)
                    .append(line.getQuantity().toPlainString())
                    .append(FIELD)
                    .append(line.getUnitPrice().toPlainString())
                    .append(FIELD)
                    .append(line.getTaxRate().toPlainString())
                    .append(FIELD)
                    .append(line.getCategory() == null ? "" : line.getCategory().name());
        }
        return packed.toString();
    }

    private static List<Invoice.Line> decodeLines(String packed) {
        List<Invoice.Line> lines = new ArrayList<>();
        if (packed == null || packed.isEmpty()) {
            return lines;
        }
        for (String record : packed.split(RECORD, -1)) {
            if (record.isEmpty()) {
                continue;
            }
            String[] parts = record.split(FIELD, -1);
            if (parts.length < 5) {
                // A record too short to be a line is skipped rather than throwing.
                // Corruption in one line should not make the whole invoice unreadable.
                continue;
            }
            BudgetCategory category = parts[4].isBlank() ? null
                    : BudgetCategory.fromLabel(parts[4]);
            lines.add(new Invoice.Line(parts[0], parse(parts[1]), parse(parts[2]),
                    parse(parts[3]), category));
        }
        return lines;
    }

    private static BigDecimal parse(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException corrupt) {
            return Money.ZERO;
        }
    }

    // ---------------------------------------------------------------- mapping

    private static Payment readPayment(java.sql.ResultSet results) throws SQLException {
        Payment payment = new Payment(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "reference"),
                Sql.text(results, "counterparty"),
                Sql.text(results, "description"),
                TransactionDirection.fromLabel(Sql.text(results, "direction")),
                PaymentMethod.fromLabel(Sql.text(results, "method")),
                Sql.money(results, "amount"),
                Sql.money(results, "provider_fee"),
                Sql.text(results, "category"),
                Sql.text(results, "ticket_ref"),
                Sql.text(results, "attendee_id"),
                Sql.text(results, "invoice_id"),
                Sql.text(results, "note"),
                results.getTimestamp("raised_at") == null ? java.time.Instant.now()
                        : results.getTimestamp("raised_at").toInstant(),
                results.getTimestamp("settled_at") == null ? null
                        : results.getTimestamp("settled_at").toInstant(),
                Sql.flag(results, "vat_applicable"),
                PaymentStatus.fromLabel(Sql.text(results, "status")));
        return payment;
    }

    private static Invoice readInvoice(java.sql.ResultSet results) throws SQLException {
        return new Invoice(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "number"),
                Sql.text(results, "billed_to"),
                Sql.text(results, "billed_email"),
                Sql.text(results, "address"),
                Sql.text(results, "purchase_order"),
                Sql.date(results, "issued_on"),
                Sql.date(results, "due_on"),
                Sql.text(results, "notes"),
                decodeLines(Sql.text(results, "lines")),
                Sql.money(results, "paid_amount"),
                results.getTimestamp("paid_at") == null ? null
                        : results.getTimestamp("paid_at").toInstant(),
                InvoiceStatus.fromLabel(Sql.text(results, "status")));
    }

    private static BudgetLine readBudgetLine(java.sql.ResultSet results) throws SQLException {
        return new BudgetLine(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                BudgetCategory.fromLabel(Sql.text(results, "category")),
                Sql.money(results, "planned"),
                Sql.money(results, "committed"),
                Sql.money(results, "actual"),
                Sql.text(results, "note"));
    }
}
