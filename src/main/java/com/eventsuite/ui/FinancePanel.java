package com.eventsuite.ui;

import com.eventsuite.analytics.EventMetrics;
import com.eventsuite.analytics.SustainabilityMetric;
import com.eventsuite.core.Event;
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * The money: what came in, what went out, and the budget in between.
 *
 * <p>The order on screen is the order of the question. Takings first, because that is
 * the headline. Then costs, because that is what has to be compared against them. Then
 * the budget, which is a plan rather than a fact and is kept visually separate so
 * nobody reads a projection as though it were money.
 *
 * <p>Committed and spent are shown as two columns rather than one "spent" figure. The
 * difference between them is the whole reason a budget screen is useful: two thirds of
 * a production budget promised to suppliers six weeks out looks like a catastrophe if it
 * is reported as spend, and looks healthy if it is not reported at all.
 */
public final class FinancePanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private final Charts.Bars costBreakdown = new Charts.Bars();
    private final Charts.Bars methodBreakdown = new Charts.Bars();
    private final Charts.Bars budgetVariance = new Charts.Bars();

    private JPanel tileArea;
    private JPanel budgetArea;
    private JTable paymentTable;
    private TableData paymentData;

    public FinancePanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Finance", "Choose an event in the header to see its money.",
                    Ui.emptyState("No event selected.",
                            "Takings, costs and budget all belong to one event."));
            return;
        }

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);
        budgetArea = new JPanel();
        budgetArea.setOpaque(false);
        budgetArea.setLayout(new BoxLayout(budgetArea, BoxLayout.Y_AXIS));
        paymentData = TableData.with("When", "Reference", "Counterparty", "Direction",
                "Method", "Amount", "Status").withMoney(5).withStatus(6);
        paymentTable = tableOf(paymentData);

        JPanel charts = new JPanel(new GridLayout(0, 3, 14, 0));
        charts.setOpaque(false);
        charts.add(Ui.describedCard("Costs by heading",
                "What was actually paid out.", costBreakdown));
        charts.add(Ui.describedCard("Income by method",
                "How the money arrived.", methodBreakdown));
        charts.add(Ui.describedCard("Budget against plan",
                "Planned for each heading.", budgetVariance));

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Record a cost", this::showExpenseForm),
                        Ui.button("Issue an invoice", this::showInvoiceForm),
                        Ui.button("Change a budget", this::showBudgetForm),
                        Ui.button("Refresh", this::reload)),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                charts,
                Ui.gap(14),
                Ui.describedCard("Budget",
                        "Planned, promised and paid, heading by heading.", budgetArea),
                Ui.gap(14),
                Ui.describedCard("Ledger", "Every movement of money, in and out.",
                        Ui.scroll(paymentTable)));
        screen("Finance", event.getName() + "  \u00b7  " + event.getDateRange(), body);
        reload();
    }

    @Override
    protected void reload() {
        Event event = currentEvent();
        if (event == null || tileArea == null) {
            return;
        }
        try {
                            fillTiles(event);
                            fillBudget(event);
                            fillCharts(event);
                            fillLedger(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the figures", failure.getMessage());
        }
    }

    private void fillTiles(Event event) throws SQLException {
        EventMetrics metrics = suite().metrics().forEvent(event.getId());
        DashboardPanel.clear(tileArea);
        BigDecimal profit = metrics.getRoi().getProfit();

        tileArea.add(Ui.tile("Taken",
                Money.compact(metrics.getGrossRevenue()),
                Money.compact(metrics.getProviderFees()) + " in processing fees"));
        tileArea.add(Ui.tile("Other income",
                Money.compact(metrics.getOtherRevenue()),
                "Sponsorship, exhibitors and grants"));
        tileArea.add(Ui.tile("Paid out",
                Money.compact(metrics.getBudgetActual()),
                Money.compact(metrics.getBudgetCommitted()) + " committed but unpaid"));
        tileArea.add(Ui.tile("Position",
                Money.compact(profit),
                metrics.getOutstandingMoney().signum() > 0
                        ? Money.compact(metrics.getOutstandingMoney()) + " still owed"
                        : metrics.getRoi().getReturnVerdict(),
                profit.signum() > 0 ? Theme.current().success()
                        : profit.signum() < 0 ? Theme.current().danger()
                        : Theme.current().muted()));
    }

    /**
     * The budget table.
     *
     * <p>Built as rows rather than as a Swing table because each row carries three bars
     * and a health line, which a cell renderer would make unreadable.
     */
    private void fillBudget(Event event) throws SQLException {
        DashboardPanel.clear(budgetArea);
        List<BudgetLine> lines = suite().finance().findBudgetLines(event.getId());
        if (lines.isEmpty()) {
            budgetArea.add(Ui.emptyState("No budget set.",
                    "Use Change a budget to allocate a total across the headings."));
            return;
        }
        for (BudgetLine line : lines) {
            budgetArea.add(budgetRow(line));
            budgetArea.add(Box.createVerticalStrut(9));
        }
    }

    private JComponent budgetRow(BudgetLine line) {
        JPanel row = new JPanel(new BorderLayout(14, 6));
        row.setBackground(Theme.current().field());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 120));

        JPanel facts = new JPanel();
        facts.setOpaque(false);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
        JPanel title = Ui.row(Ui.bodyBold(line.getCategory().getLabel()),
                Ui.health(line.getHealthLabel(), line.isOverspent() || line.isNearLimit()));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(title);
        facts.add(Box.createVerticalStrut(7));
        facts.add(Ui.bar("Planned " + Money.format(line.getPlanned())
                        + "  \u00b7  committed " + Money.format(line.getCommitted())
                        + "  \u00b7  paid " + Money.format(line.getActual()),
                line.getUsedPercent().intValue(),
                line.isOverspent() ? Theme.current().danger()
                        : line.isNearLimit() ? Theme.current().warning()
                        : Theme.current().success()));
        row.add(facts, BorderLayout.CENTER);
        return row;
    }

    private void fillCharts(Event event) throws SQLException {
        java.util.Map<String, Double> costs = new java.util.LinkedHashMap<>();
        java.util.Map<String, Double> methods = new java.util.LinkedHashMap<>();
        java.util.Map<String, Double> planned = new java.util.LinkedHashMap<>();

        for (BudgetCategory category : BudgetCategory.values()) {
            BigDecimal paid = suite().finance().sumOutflowByCategory(event.getId(), category);
            if (paid.signum() > 0) {
                costs.put(category.getLabel(), paid.doubleValue());
            }
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            BigDecimal taken = suite().finance().sumInflowByMethod(event.getId(), method);
            if (taken.signum() > 0) {
                methods.put(method.getLabel(), taken.doubleValue());
            }
        }
        for (BudgetLine line : suite().finance().findBudgetLines(event.getId())) {
            if (line.getPlanned().signum() > 0) {
                planned.put(line.getCategory().getLabel(), line.getPlanned().doubleValue());
            }
        }
        costBreakdown.with(costs).measuredIn("\u00a3 paid")
                .monochrome(Theme.current().warning());
        methodBreakdown.with(methods).measuredIn("\u00a3 taken");
        budgetVariance.with(planned).measuredIn("\u00a3 planned")
                .monochrome(Theme.current().info());
    }

    private void fillLedger(Event event) throws SQLException {
        paymentData.rowsAsList().clear();
        for (Payment payment : suite().finance().findPayments(event.getId())) {
            paymentData.add(payment.getWhenLabel(), payment.getReference(),
                    payment.getCounterparty().isEmpty() ? payment.getDescription()
                            : payment.getCounterparty(),
                    payment.getDirection().getLabel(), payment.getMethod().getLabel(),
                    Money.format(payment.getAmount()), payment.getStatus().getLabel());
        }
        paymentTable.repaint();
        setSubtitle(suite().finance().findPayments(event.getId()).size()
                + " payment(s) recorded");
    }

    // ---------------------------------------------------------------- forms

    /** Records a bill being paid, and moves the budget heading with it. */
    private void showExpenseForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField payee = Ui.field("");
        JTextField description = Ui.field("");
        JTextField amount = Ui.field("0");
        JComboBox<BudgetCategory> category =
                Ui.enumCombo(BudgetCategory.class, BudgetCategory.VENUE);
        JComboBox<PaymentMethod> method = Ui.enumCombo(PaymentMethod.class, PaymentMethod.BANK_TRANSFER);
        TaxHolder vat = new TaxHolder(false);

        JPanel form = Ui.column(
                Ui.titledGroup("Who is being paid", payee),
                Ui.gap(8),
                Ui.titledGroup("What for", description),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Amount", amount),
                        Ui.titledGroup("Paid by", method)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Budget heading", category), vat.component()));
        if (Ui.formDialog(this, "Record a cost", form, "Record it", "Cancel") == null) {
            return;
        }
        try {
                            BigDecimal value = Money.of(amount.getText());
                            if (value.signum() <= 0) {
                                Ui.dialog(this, "Amount not accepted",
                                        "A cost is a positive amount. Enter 0 only if you are recording"
                                                + " something already paid for elsewhere.");
                                return;
                            }
                            suite().recordExpense(event, payee.getText().trim(), description.getText().trim(),
                                    value, (PaymentMethod) method.getSelectedItem(),
                                    (BudgetCategory) category.getSelectedItem(), "", vat.isChecked());
                            setSubtitle("Recorded " + Money.format(value) + " to " + payee.getText());
                            reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not record the cost", failure.getMessage());
        }
    }

    /**
     * Issues an invoice.
     *
     * <p>One description and one amount. An invoice with several lines is entered as
     * several invoices, which keeps the arithmetic that reconciles them honest — a
     * mistake in one line of a multi-line invoice is very hard to see.
     */
    private void showInvoiceForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField billedTo = Ui.field("");
        JTextField email = Ui.field("");
        JTextField description = Ui.field("Sponsorship");
        JTextField amount = Ui.field("0");
        JTextField tax = Ui.field("20");
        JTextField issuedOn = Ui.field(LocalDate.now().toString());
        JTextField dueOn = Ui.field(LocalDate.now().plusDays(30).toString());
        JTextField order = Ui.field("");
        JComboBox<BudgetCategory> category =
                Ui.enumCombo(BudgetCategory.class, BudgetCategory.MARKETING);

        JPanel form = Ui.column(
                Ui.titledGroup("Billed to", billedTo),
                Ui.gap(8),
                Ui.titledGroup("Their email", email),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("For", description), Ui.titledGroup("Amount", amount)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Tax percent", tax),
                        Ui.titledGroup("Their reference", order)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Issued", issuedOn),
                        Ui.titledGroup("Due", dueOn)),
                Ui.gap(8),
                Ui.titledGroup("Budget heading", category));
        if (Ui.formDialog(this, "Issue an invoice", form, "Issue it", "Cancel") == null) {
            return;
        }
        try {
                            if (billedTo.getText().isBlank()) {
                                Ui.dialog(this, "Who is it billed to", "An invoice needs a name on it.");
                                return;
                            }
                            BigDecimal value = Money.of(amount.getText());
                            if (value.signum() <= 0) {
                                Ui.dialog(this, "Amount not accepted", "Enter an amount above zero.");
                                return;
                            }
                            LocalDate issued = LocalDate.parse(issuedOn.getText().trim());
                            LocalDate due = LocalDate.parse(dueOn.getText().trim());
                            Invoice invoice = suite().issueInvoice(event, billedTo.getText().trim(),
                                    email.getText().trim(), description.getText().trim(), value,
                                    Money.of(tax.getText()),
                                    (BudgetCategory) category.getSelectedItem(), issued, due,
                                    order.getText().trim());
                            setSubtitle("Issued " + invoice.getNumber() + " for "
                                    + Money.format(invoice.getTotal()));
                            reload();
                            } catch (IllegalArgumentException badInput) {
                            Ui.dialog(this, "Invoice not accepted", badInput.getMessage());
                            } catch (java.time.format.DateTimeParseException badDate) {
                            Ui.dialog(this, "Date not understood",
                                    "Use the form 2026-04-14. Issued and due must both be dates.");
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not issue the invoice", failure.getMessage());
        }
    }

    private void showBudgetForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JComboBox<BudgetCategory> category =
                Ui.enumCombo(BudgetCategory.class, BudgetCategory.VENUE);
        JTextField planned = Ui.field("0");
        JTextField total = Ui.field(String.valueOf(event.getBudgetPlanned().toPlainString()));

        JPanel form = Ui.column(
                Ui.titledGroup("Heading", category),
                Ui.gap(8),
                Ui.titledGroup("How much is allowed for it", planned),
                Ui.gap(10),
                Ui.titledGroup("Or set a whole-event total and divide it up",
                        Ui.row(total, Ui.smallButton("Divide it up", () -> {
                            BigDecimal whole = Money.of(total.getText());
                            BigDecimal share =
                                    ((BudgetCategory) category.getSelectedItem())
                                            .suggestedFor(whole);
                            planned.setText(share.toPlainString());
                        }))));
        if (Ui.formDialog(this, "Change the budget", form, "Save", "Cancel") == null) {
            return;
        }
        try {
                            suite().setBudgetAllocation(event, (BudgetCategory) category.getSelectedItem(),
                                    Money.of(planned.getText()));
                            setSubtitle("Budget updated.");
                            reload();
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not save the budget", failure.getMessage());
        }
    }

    /** Carries a checkbox's value in and out of a form. */
    private static final class TaxHolder {
        private final javax.swing.JCheckBox box;

        TaxHolder(boolean selected) {
            this.box = Ui.check("This figure includes tax", selected);
        }

        javax.swing.JCheckBox component() {
            return box;
        }

        boolean isChecked() {
            return box.isSelected();
        }
    }
}
