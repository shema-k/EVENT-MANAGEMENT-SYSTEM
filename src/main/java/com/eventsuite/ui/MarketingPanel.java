package com.eventsuite.ui;

import com.eventsuite.core.Event;
import com.eventsuite.marketing.Campaign;
import com.eventsuite.marketing.DiscountCode;
import com.eventsuite.marketing.MarketingChannel;
import com.eventsuite.service.EventSuite;
import com.eventsuite.ticketing.Registration;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * Getting people in: campaigns, what they cost, and what they brought back.
 *
 * <p>The measure on this screen is registrations per pound, not reach. Reach is
 * something you buy and is therefore not evidence of anything; registrations are what
 * the money was for. Every campaign shows its cost per registration next to what that
 * channel usually costs, so a figure can be judged against something rather than in
 * isolation.
 *
 * <p>Discount codes sit on the same screen as campaigns deliberately. A code is a
 * campaign that is paid for out of the take rather than out of the budget, and looking
 * at them together is the only way to see a promotion costing a third of the revenue.
 */
public final class MarketingPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private final Charts.Bars costPerRegistration = new Charts.Bars();
    private final Charts.Bars spendByChannel = new Charts.Bars();
    private final Charts.Donut registrationSources = new Charts.Donut();

    private JPanel tileArea;
    private JPanel campaignArea;
    private JTable discountTable;
    private TableData discountData;

    public MarketingPanel(EventSuite suite) {
        super(suite);
    }

    @Override
    protected void build() {
        Event event = currentEvent();
        if (event == null) {
            screen("Marketing", "Choose an event in the header to see its promotion.",
                    Ui.emptyState("No event selected.",
                            "Campaigns and discount codes belong to an event."));
            return;
        }

        tileArea = new JPanel(new GridLayout(0, 4, 12, 12));
        tileArea.setOpaque(false);
        campaignArea = new JPanel();
        campaignArea.setOpaque(false);
        campaignArea.setLayout(new BoxLayout(campaignArea, BoxLayout.Y_AXIS));
        discountData = TableData.with("Code", "Kind", "Value", "Used", "Limit", "Valid until",
                "Note").withStatus(4);
        discountTable = tableOf(discountData);

        JPanel charts = new JPanel(new GridLayout(0, 3, 14, 0));
        charts.setOpaque(false);
        charts.add(Ui.describedCard("Cost per registration",
                "By campaign, against what that channel usually costs.", costPerRegistration));
        charts.add(Ui.describedCard("Spend by channel", "Where the money went.", spendByChannel));
        charts.add(Ui.describedCard("Where registrations came from",
                "By the source recorded against them.", registrationSources));

        JPanel body = Ui.column(
                Ui.row(Ui.primary("Add a campaign", this::showCampaignForm),
                        Ui.button("Record what a campaign achieved", this::showResultsForm),
                        Ui.button("Add a discount code", this::showDiscountForm),
                        Ui.button("Refresh", this::reload)),
                Ui.gap(14),
                tileArea,
                Ui.gap(14),
                charts,
                Ui.gap(14),
                Ui.describedCard("Campaigns", "Planned, running and finished.",
                        campaignArea),
                Ui.gap(14),
                Ui.describedCard("Discount codes",
                        "Money given away once people arrive.", Ui.scroll(discountTable)));
        screen("Marketing", event.getName() + "  \u00b7  " + event.getDateRange(), body);
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
                            fillCampaigns(event);
                            fillDiscounts(event);
                            fillCharts(event);
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not load the marketing figures", failure.getMessage());
        }
    }

    private void fillTiles(Event event) throws SQLException {
        DashboardPanel.clear(tileArea);
        BigDecimal spend = suite().marketing().totalSpend(eventId(event));
        BigDecimal budget = suite().marketing().totalBudgeted(eventId(event));
        int campaigns = suite().marketing().countCampaigns(eventId(event));
        int registrations = suite().marketing().totalAttributedRegistrations(eventId(event));
        BigDecimal perRegistration = suite().marketing().costPerRegistration(eventId(event));
        BigDecimal discountGiven = suite().tickets().sumDiscountGiven(eventId(event));

        tileArea.add(Ui.tile("Spent",
                MoneyLabel.compact(spend),
                budget.signum() > 0 ? "of " + MoneyLabel.compact(budget) + " allocated"
                        : "nothing allocated"));
        tileArea.add(Ui.tile("Campaigns",
                String.valueOf(campaigns),
                suite().marketing().countCompletedCampaigns(eventId(event)) + " have run"));
        tileArea.add(Ui.tile("Cost per registration",
                registrations == 0 ? "\u2014" : MoneyLabel.compact(perRegistration),
                registrations == 0 ? "nothing attributed yet"
                        : registrations + " attributed",
                registrations == 0 ? Theme.current().muted()
                        : Theme.current().text()));
        tileArea.add(Ui.tile("Given away",
                MoneyLabel.compact(discountGiven),
                discountGiven.signum() > 0 ? "in discount codes" : "no codes used",
                discountGiven.signum() > 0 ? Theme.current().warning()
                        : Theme.current().text()));
    }

    private String eventId(Event event) {
        return event.getId();
    }

    /** A short name for money, so a tile does not overflow. */
    private static final class MoneyLabel {
        private MoneyLabel() {
        }

        static String compact(BigDecimal amount) {
            return com.eventsuite.finance.Money.compact(amount);
        }
    }

    private void fillCampaigns(Event event) throws SQLException {
        DashboardPanel.clear(campaignArea);
        List<Campaign> campaigns = suite().marketing().findCampaigns(eventId(event));
        if (campaigns.isEmpty()) {
            campaignArea.add(Ui.emptyState("No campaigns yet.",
                    "A campaign is one push on one channel, with what it cost and what it"
                            + " brought back."));
            return;
        }
        for (Campaign campaign : campaigns) {
            campaignArea.add(campaignRow(campaign));
            campaignArea.add(Box.createVerticalStrut(8));
        }
    }

    /**
     * One campaign.
     *
     * <p>Shows the verdict explicitly. "Efficient" or "not worth repeating" is the
     * thing worth recording, because in a year's time nobody will remember which of
     * six campaigns worked and the numbers alone do not say it.
     */
    private JComponent campaignRow(Campaign campaign) {
        JPanel row = new JPanel(new BorderLayout(14, 6));
        row.setBackground(Theme.current().field());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 140));

        JPanel facts = new JPanel();
        facts.setOpaque(false);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));

        JPanel title = Ui.row(Ui.bodyBold(campaign.getName()),
                Ui.status(campaign.getStage().getLabel()),
                Ui.muted(campaign.getChannel().getLabel()));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(title);
        facts.add(Box.createVerticalStrut(6));

        facts.add(Ui.bar("Spent " + com.eventsuite.finance.Money.format(campaign.getSpent())
                        + " of " + com.eventsuite.finance.Money.format(campaign.getBudgeted()),
                campaign.getBudgetUsedPercent().intValue(),
                campaign.isOverBudget() ? Theme.current().danger() : Theme.current().accent()));

        if (campaign.producedRegistrations()) {
            JLabel results = Ui.muted(campaign.getRegistrations() + " registrations from "
                    + campaign.getReach() + " reached, at "
                    + com.eventsuite.finance.Money.format(campaign.getCostPerRegistration())
                    + " each  \u00b7  " + verdictFor(campaign));
            results.setAlignmentX(Component.LEFT_ALIGNMENT);
            facts.add(Box.createVerticalStrut(6));
            facts.add(results);
        }
        row.add(facts, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(Ui.status(verdictFor(campaign)));
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    /**
     * Whether a campaign is worth repeating.
     *
     * <p>Judged against what that channel normally costs rather than a fixed
     * threshold, because the channels differ by two orders of magnitude and one number
     * cannot serve all of them.
     */
    private String verdictFor(Campaign campaign) {
        if (!campaign.producedRegistrations()) {
            return "Brought nobody yet";
        }
        return campaign.isEfficient() ? "Worth repeating" : "Too expensive";
    }

    private void fillDiscounts(Event event) throws SQLException {
        discountData.rowsAsList().clear();
        for (DiscountCode code : suite().marketing().findDiscounts(eventId(event))) {
            discountData.add(code.getCode(), code.getKind().getLabel(),
                    code.getKind() == DiscountCode.Kind.PERCENT
                            ? code.getValue().stripTrailingZeros().toPlainString() + "%"
                            : com.eventsuite.finance.Money.format(code.getValue()),
                    String.valueOf(code.getTimesUsed()),
                    code.isUnlimited() ? "unlimited" : String.valueOf(code.getUsageLimit()),
                    code.getValidUntil() == null ? "no end date"
                            : code.getValidUntil().toString(),
                    code.getNote());
        }
        discountTable.repaint();
    }

    private void fillCharts(Event event) throws SQLException {
        Map<String, Double> perRegistration = new LinkedHashMap<>();
        Map<String, Double> byChannel = new LinkedHashMap<>();
        Map<String, Double> sources = new LinkedHashMap<>();

        for (Campaign campaign : suite().marketing().findCampaigns(eventId(event))) {
            if (campaign.producedRegistrations()) {
                perRegistration.put(Ui.ellipsise(campaign.getName(), 18),
                        campaign.getCostPerRegistration().doubleValue());
            }
            double existing = byChannel.getOrDefault(campaign.getChannel().getLabel(), 0.0);
            byChannel.put(campaign.getChannel().getLabel(),
                    existing + campaign.getSpent().doubleValue());
        }
        for (Registration registration : suite().people().findRegistrations(eventId(event))) {
            String source = registration.getReferralSource().isEmpty() ? "Unattributed"
                    : registration.getReferralSource();
            sources.merge(source, 1.0, Double::sum);
        }

        costPerRegistration.with(perRegistration).measuredIn("\u00a3 each")
                .monochrome(Theme.current().success());
        spendByChannel.with(byChannel).measuredIn("\u00a3 spent");
        registrationSources.with(sources).centredOn(
                String.valueOf(suite().people().countRegistrations(eventId(event))));
    }

    // ---------------------------------------------------------------- forms

    private void showCampaignForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField name = Ui.field("");
        JComboBox<MarketingChannel> channel =
                Ui.enumCombo(MarketingChannel.class, MarketingChannel.PAID_SOCIAL);
        JTextField budget = Ui.field("500");
        JTextField startsOn = Ui.field(LocalDate.now().toString());
        JTextField endsOn = Ui.field(event.getStartDate().toString());
        JTextField audience = Ui.field("");
        JTextField message = Ui.field("");

        JPanel form = Ui.column(
                Ui.titledGroup("What is the campaign", name),
                Ui.gap(8),
                Ui.titledGroup("Channel", channel),
                Ui.gap(8),
                Ui.titledGroup("Budget", budget),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Starts", startsOn),
                        Ui.titledGroup("Ends", endsOn)),
                Ui.gap(8),
                Ui.titledGroup("Who is it aimed at", audience),
                Ui.gap(8),
                Ui.titledGroup("The message", message));
        if (Ui.formDialog(this, "Add a campaign", form, "Add it", "Cancel") == null) {
            return;
        }
        if (name.getText().isBlank()) {
            Ui.dialog(this, "Name required", "Give the campaign a name you will recognise.");
            return;
        }
        try {
                            suite().addCampaign(eventId(event), name.getText().trim(),
                                    (MarketingChannel) channel.getSelectedItem(),
                                    com.eventsuite.finance.Money.of(budget.getText()),
                                    LocalDate.parse(startsOn.getText().trim()),
                                    LocalDate.parse(endsOn.getText().trim()),
                                    audience.getText().trim(), message.getText().trim());
                            setSubtitle("Campaign " + name.getText().trim() + " added.");
                            reload();
                            } catch (java.time.format.DateTimeParseException badDate) {
                            Ui.dialog(this, "Date not understood", "Use the form 2026-04-14.");
                            } catch (IllegalArgumentException badInput) {
                            Ui.dialog(this, "Campaign not accepted", badInput.getMessage());
        } catch (SQLException failure) {
            Ui.dialog(this, "Could not add the campaign", failure.getMessage());
        }
    }

    private void showResultsForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        List<Campaign> campaigns;
        try {
                    campaigns = suite().marketing().findCampaigns(eventId(event));
                    } catch (SQLException failure) {
                    Ui.dialog(this, "Could not read the campaigns", failure.getMessage());
                    return;
                    }
                    Campaign chosen = Ui.choose(this, "Record results",
                        "Which campaign produced these numbers?", campaigns);
                    if (chosen == null) {
                    return;
                    }
                    JTextField spent = Ui.field(chosen.getSpent().toPlainString());
                    JTextField reach = Ui.field("0");
                    JTextField clicks = Ui.field("0");
                    JTextField registrations = Ui.field("0");

                    JPanel form = Ui.column(
                        Ui.titledGroup("Spent", spent),
                        Ui.gap(8),
                        Ui.row(Ui.titledGroup("People reached", reach),
                                Ui.titledGroup("Clicked through", clicks)),
                        Ui.gap(8),
                        Ui.titledGroup("Registered as a result", registrations));
                    if (Ui.formDialog(this, "What did " + chosen.getName() + " achieve?", form,
                        "Record it", "Cancel") == null) {
                    return;
                    }
                    try {
                                            suite().recordCampaignResults(chosen,
                                                com.eventsuite.finance.Money.of(spent.getText()),
                                                Integer.parseInt(reach.getText().trim()),
                                                Integer.parseInt(clicks.getText().trim()),
                                                Integer.parseInt(registrations.getText().trim()));
                                            setSubtitle("Recorded results for " + chosen.getName() + ": "
                                                + verdictFor(chosen) + ".");
                                            reload();
                                            } catch (NumberFormatException notANumber) {
                                            Ui.dialog(this, "Figures not understood", "Use whole numbers for reach, clicks"
                                                + " and registrations.");
                    } catch (SQLException failure) {
                        Ui.dialog(this, "Could not record the results", failure.getMessage());
                    }
    }

    private void showDiscountForm() {
        Event event = currentEvent();
        if (event == null) {
            return;
        }
        JTextField code = Ui.field("");
        JComboBox<DiscountCode.Kind> kind = Ui.enumCombo(DiscountCode.Kind.class,
                DiscountCode.Kind.PERCENT);
        JTextField value = Ui.field("10");
        JTextField limit = Ui.field("50");
        JTextField until = Ui.field(event.getStartDate().toString());
        JTextField note = Ui.field("");

        JPanel form = Ui.column(
                Ui.titledGroup("Code", code),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Kind", kind), Ui.titledGroup("Value", value)),
                Ui.gap(8),
                Ui.row(Ui.titledGroup("Uses (0 is unlimited)", limit),
                        Ui.titledGroup("Valid until", until)),
                Ui.gap(8),
                Ui.titledGroup("Why it exists", note));
        if (Ui.formDialog(this, "Add a discount code", form, "Add it", "Cancel") == null) {
            return;
        }
        try {
            suite().addDiscount(eventId(event), code.getText().trim().toUpperCase(
                            java.util.Locale.ROOT),
                    (DiscountCode.Kind) kind.getSelectedItem(),
                    com.eventsuite.finance.Money.of(value.getText()),
                    Integer.parseInt(limit.getText().trim()),
                    LocalDate.now(), LocalDate.parse(until.getText().trim()), "",
                    note.getText().trim());
            setSubtitle("Discount code added.");
        } catch (java.time.format.DateTimeParseException badDate) {
            Ui.dialog(this, "Date not understood", "Use the form 2026-04-14.");
        } catch (NumberFormatException notANumber) {
            Ui.dialog(this, "Uses not understood", "Put a whole number, or 0 for no limit.");
        } catch (IllegalArgumentException | SQLException failure) {
            Ui.dialog(this, "Code not accepted", failure.getMessage());
        }
    }
}
