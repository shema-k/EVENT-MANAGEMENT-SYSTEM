package com.eventsuite.data;

import com.eventsuite.marketing.Campaign;
import com.eventsuite.marketing.DiscountCode;
import com.eventsuite.marketing.MarketingChannel;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Stores and reads the marketing record: campaigns and discount codes.
 *
 * <p>Both are about the same question from opposite ends. A campaign is money spent
 * to bring people in; a discount code is money given away once they have arrived.
 * Keeping them in one store is deliberate, because the reconciliation that matters
 * is between them — a code that is doing a lot of work is a campaign's return being
 * quietly spent, and an organiser should see both halves of that in one place.
 */
public final class MarketingStore {

    private static final String CAMPAIGN_COLUMNS =
            "id, event_id, name, channel, budgeted, spent, reach_count, clicks, registrations,"
                    + " starts_on, ends_on, audience, message, asset_notes, stage";

    private static final String DISCOUNT_COLUMNS =
            "id, event_id, code, kind, value_amt, usage_limit, times_used, valid_from,"
                    + " valid_until, applies_to, note";

    private final Connection connection;

    public MarketingStore(Connection connection) {
        this.connection = connection;
    }

    // ---------------------------------------------------------------- campaigns

    /** Inserts a campaign. */
    public void insertCampaign(Campaign campaign) throws SQLException {
        Sql.update(connection,
                "INSERT INTO campaigns (" + CAMPAIGN_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, campaign.getId());
                    Sql.setText(statement, 2, campaign.getEventId());
                    Sql.setText(statement, 3, campaign.getName());
                    Sql.setText(statement, 4, campaign.getChannel().name());
                    Sql.setMoney(statement, 5, campaign.getBudgeted());
                    Sql.setMoney(statement, 6, campaign.getSpent());
                    Sql.setInt(statement, 7, campaign.getReach());
                    Sql.setInt(statement, 8, campaign.getClicks());
                    Sql.setInt(statement, 9, campaign.getRegistrations());
                    Sql.setDate(statement, 10, campaign.getStartsOn());
                    Sql.setDate(statement, 11, campaign.getEndsOn());
                    Sql.setText(statement, 12, campaign.getAudience());
                    Sql.setText(statement, 13, campaign.getMessage());
                    Sql.setText(statement, 14, campaign.getAssetNotes());
                    Sql.setText(statement, 15, campaign.getStage().name());
                });
    }

    /** Rewrites a campaign. */
    public void updateCampaign(Campaign campaign) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE campaigns SET name=?, channel=?, budgeted=?, spent=?, reach_count=?,"
                        + " clicks=?, registrations=?, starts_on=?, ends_on=?, audience=?,"
                        + " message=?, asset_notes=?, stage=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, campaign.getName());
                    Sql.setText(statement, 2, campaign.getChannel().name());
                    Sql.setMoney(statement, 3, campaign.getBudgeted());
                    Sql.setMoney(statement, 4, campaign.getSpent());
                    Sql.setInt(statement, 5, campaign.getReach());
                    Sql.setInt(statement, 6, campaign.getClicks());
                    Sql.setInt(statement, 7, campaign.getRegistrations());
                    Sql.setDate(statement, 8, campaign.getStartsOn());
                    Sql.setDate(statement, 9, campaign.getEndsOn());
                    Sql.setText(statement, 10, campaign.getAudience());
                    Sql.setText(statement, 11, campaign.getMessage());
                    Sql.setText(statement, 12, campaign.getAssetNotes());
                    Sql.setText(statement, 13, campaign.getStage().name());
                    Sql.setText(statement, 14, campaign.getId());
                });
        if (changed == 0) {
            throw new SQLException("No campaign to update with id " + campaign.getId());
        }
    }
    /** Every campaign on an event. */
    public List<Campaign> findCampaigns(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + CAMPAIGN_COLUMNS + " FROM campaigns WHERE event_id=?"
                        + " ORDER BY starts_on, name",
                statement -> Sql.setText(statement, 1, eventId),
                MarketingStore::readCampaign);
    }
    /** Total money spent on marketing an event. */
    public BigDecimal totalSpend(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(spent) FROM campaigns WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Total money allocated to marketing an event. */
    public BigDecimal totalBudgeted(String eventId) throws SQLException {
        return Sql.sum(connection, "SELECT SUM(budgeted) FROM campaigns WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many campaigns an event has. */
    public int countCampaigns(String eventId) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM campaigns WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many campaigns on an event have actually run. */
    public int countCompletedCampaigns(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM campaigns WHERE event_id=?"
                        + " AND stage IN ('RUNNING','FINISHED')",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** Registrations attributed to marketing, across all campaigns. */
    public int totalAttributedRegistrations(String eventId) throws SQLException {
        int total = 0;
        for (Campaign campaign : findCampaigns(eventId)) {
            total += campaign.getRegistrations();
        }
        return total;
    }
    /** Spend per registration across every campaign, or zero when there were none. */
    public BigDecimal costPerRegistration(String eventId) throws SQLException {
        int registrations = totalAttributedRegistrations(eventId);
        if (registrations == 0) {
            return BigDecimal.ZERO;
        }
        return totalSpend(eventId).divide(BigDecimal.valueOf(registrations), 2,
                java.math.RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- discounts

    /** Inserts a discount code. */
    public void insertDiscount(DiscountCode code) throws SQLException {
        Sql.update(connection,
                "INSERT INTO discount_codes (" + DISCOUNT_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, code.getId());
                    Sql.setText(statement, 2, code.getEventId());
                    Sql.setText(statement, 3, code.getCode());
                    Sql.setText(statement, 4, code.getKind().name());
                    Sql.setMoney(statement, 5, code.getValue());
                    Sql.setInt(statement, 6, code.getUsageLimit());
                    Sql.setInt(statement, 7, code.getTimesUsed());
                    Sql.setDate(statement, 8, code.getValidFrom());
                    Sql.setDate(statement, 9, code.getValidUntil());
                    Sql.setText(statement, 10, code.getAppliesToTiers());
                    Sql.setText(statement, 11, code.getNote());
                });
    }
    /**
     * Records one more redemption of a code.
     *
     * <p>The increment happens in the statement rather than in Java, so two sales at
     * the same moment cannot both read the same count and each write back count plus
     * one. That would lose a redemption and leave a code usable past its limit.
     */
    public int recordRedemption(String codeId) throws SQLException {
        Sql.update(connection,
                "UPDATE discount_codes SET times_used=times_used+1 WHERE id=?",
                statement -> Sql.setText(statement, 1, codeId));
        return Sql.count(connection,
                "SELECT times_used FROM discount_codes WHERE id=?",
                statement -> Sql.setText(statement, 1, codeId));
    }
    /** The code with this text on an event, or null. */
    public DiscountCode findDiscountByCode(String eventId, String code) throws SQLException {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Sql.queryOne(connection,
                "SELECT " + DISCOUNT_COLUMNS + " FROM discount_codes WHERE event_id=? AND code=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, code.trim().toUpperCase(java.util.Locale.ROOT));
                }, MarketingStore::readDiscount);
    }

    /** Every discount code on an event. */
    public List<DiscountCode> findDiscounts(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + DISCOUNT_COLUMNS + " FROM discount_codes WHERE event_id=?"
                        + " ORDER BY code",
                statement -> Sql.setText(statement, 1, eventId), MarketingStore::readDiscount);
    }
    // ---------------------------------------------------------------- mapping

    private static Campaign readCampaign(java.sql.ResultSet results) throws SQLException {
        // The column order here follows the constructor, not the table: the table
        // groups the money together for readability, the constructor puts the schedule
        // in with the description.
        return new Campaign(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "name"),
                MarketingChannel.fromLabel(Sql.text(results, "channel")),
                Sql.money(results, "budgeted"),
                Sql.date(results, "starts_on"),
                Sql.date(results, "ends_on"),
                Sql.text(results, "audience"),
                Sql.text(results, "message"),
                Sql.text(results, "asset_notes"),
                Campaign.Stage.fromLabel(Sql.text(results, "stage")),
                Sql.money(results, "spent"),
                Sql.number(results, "reach_count"),
                Sql.number(results, "clicks"),
                Sql.number(results, "registrations"));
    }

    private static DiscountCode readDiscount(java.sql.ResultSet results) throws SQLException {
        DiscountCode code = new DiscountCode(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "code"),
                DiscountCode.Kind.fromLabel(Sql.text(results, "kind")),
                Sql.money(results, "value_amt"),
                Sql.number(results, "usage_limit"),
                Sql.date(results, "valid_from"),
                Sql.date(results, "valid_until"),
                Sql.text(results, "applies_to"),
                Sql.text(results, "note"));
        // The redemption count is restored rather than reconstructed. A code that has
        // been used forty times must still read as exhausted after being reloaded, or
        // a limit would silently reset every time the application was reopened.
        code.restoreUses(Sql.number(results, "times_used"));
        return code;
    }
}
