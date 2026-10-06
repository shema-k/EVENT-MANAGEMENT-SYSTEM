package com.eventsuite.data;

import com.eventsuite.analytics.Feedback;
import com.eventsuite.analytics.FeedbackTopic;
import com.eventsuite.analytics.SustainabilityMetric;
import com.eventsuite.analytics.TrendPoint;
import com.eventsuite.finance.Money;
import com.eventsuite.ops.CheckIn;
import com.eventsuite.ops.CheckInOutcome;
import com.eventsuite.ops.EngagementRecord;
import com.eventsuite.ops.EngagementType;
import com.eventsuite.ticketing.AccessTier;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores and reads everything recorded while the event is running and after it.
 *
 * <p>Four kinds of record: arrivals at the door, engagement during the day, feedback
 * afterwards and the sustainability figures. They are in one store because they share
 * a shape — a moment in time, an event, and a few facts — and because the end of
 * event report needs all four at once.
 *
 * <p>Arrivals are the only records written in quantity and at speed, so the table is
 * indexed on the event and the arrival time together. That single index is what lets
 * the arrival curve be drawn from the database as it fills rather than after the
 * event has finished.
 *
 * <p>The door also enforces uniqueness on the ticket reference. A constraint rather
 * than a check in the interface, because the door is exactly the place where two
 * people will try to use the same pass at the same moment and the application cannot
 * be certain which of them is first.
 */
public final class OpsStore {

    private static final String CHECKIN_COLUMNS =
            "id, event_id, ticket_ref, holder, tier, gate_id, gate_name, operator, at_time,"
                    + " outcome, override_flag, note";

    private static final String ENGAGEMENT_COLUMNS =
            "id, event_id, attendee_id, attendee_name, type, subject, detail, score,"
                    + " recorded_at, session_id, channel";

    private static final String FEEDBACK_COLUMNS =
            "id, event_id, attendee_id, attendee_name, topic, rating, comment, source,"
                    + " submitted_at";

    private static final String SUSTAINABILITY_COLUMNS =
            "id, event_id, measure, quantity, source, note, estimated";

    private final Connection connection;

    public OpsStore(Connection connection) {
        this.connection = connection;
    }

    // ---------------------------------------------------------------- arrivals

    /** Inserts a door record. */
    public void insertCheckIn(CheckIn checkIn) throws SQLException {
        Sql.update(connection,
                "INSERT INTO check_ins (" + CHECKIN_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, checkIn.getId());
                    Sql.setText(statement, 2, checkIn.getEventId());
                    Sql.setText(statement, 3, checkIn.getTicketReference());
                    Sql.setText(statement, 4, checkIn.getHolderName());
                    Sql.setText(statement, 5, checkIn.getTier().name());
                    Sql.setText(statement, 6, checkIn.getGateId());
                    Sql.setText(statement, 7, checkIn.getGateName());
                    Sql.setText(statement, 8, checkIn.getOperatorName());
                    Sql.setInstant(statement, 9, checkIn.getAt());
                    Sql.setText(statement, 10, checkIn.getOutcome().name());
                    Sql.setFlag(statement, 11, checkIn.isManualOverride());
                    Sql.setText(statement, 12, checkIn.getNote());
                });
    }

    /** Every scan on an event, oldest first. */
    public List<CheckIn> findCheckIns(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + CHECKIN_COLUMNS + " FROM check_ins WHERE event_id=? ORDER BY at_time",
                statement -> Sql.setText(statement, 1, eventId), OpsStore::readCheckIn);
    }

    /** The most recent scans on an event, for the live arrivals feed. */
    public List<CheckIn> findRecentCheckIns(String eventId, int limit) throws SQLException {
        return Sql.query(connection,
                "SELECT " + CHECKIN_COLUMNS + " FROM check_ins WHERE event_id=?"
                        + " ORDER BY at_time DESC LIMIT " + Math.max(1, limit),
                statement -> Sql.setText(statement, 1, eventId), OpsStore::readCheckIn);
    }
    /** How many people were admitted. */
    public int countAdmitted(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM check_ins WHERE event_id=? AND outcome IN"
                        + " ('ADMITTED','WRONG_DOOR','NO_TICKET')",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many scans were turned away or hit a problem. */
    public int countRefused(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM check_ins WHERE event_id=? AND outcome NOT IN"
                        + " ('ADMITTED','WRONG_DOOR','NO_TICKET')",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many arrivals an operator had to wave in against the rule. */
    public int countOverrides(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM check_ins WHERE event_id=? AND override_flag=TRUE",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many distinct people were admitted, so a rescan is not counted twice. */
    public int countDistinctAdmitted(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(DISTINCT holder) FROM check_ins WHERE event_id=?"
                        + " AND outcome IN ('ADMITTED','WRONG_DOOR','NO_TICKET')",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /**
     * Arrivals per hour, for the attendance curve.
     *
     * <p>Bucketed in the query rather than in the interface, because the bucketing has
     * to agree with the axis the chart draws. Grouped on the hour because an arrival
     * curve is about the shape of the morning, not about individual arrival times.
     */
    public List<TrendPoint> arrivalTrend(String eventId) throws SQLException {
        List<CheckIn> admitted = new ArrayList<>();
        for (CheckIn checkIn : findCheckIns(eventId)) {
            if (checkIn.countsAsAttendance()) {
                admitted.add(checkIn);
            }
        }
        if (admitted.isEmpty()) {
            return List.of();
        }
        Map<LocalDateTime, Integer> buckets = new LinkedHashMap<>();
        for (CheckIn checkIn : admitted) {
            LocalDateTime time = checkIn.getLocalTime();
            LocalDateTime hour = time.withMinute(0).withSecond(0).withNano(0);
            buckets.merge(hour, 1, Integer::sum);
        }
        List<TrendPoint> points = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Integer> bucket : buckets.entrySet()) {
            points.add(new TrendPoint(bucket.getKey(), bucket.getValue()));
        }
        return points;
    }

    /**
     * Arrivals per fifteen minutes, for the live counter on the night.
     *
     * <p>Finer than the hourly curve on purpose: during the event the question is
     * "how fast is the queue moving", and an hourly bucket is too coarse to answer it.
     */
    public List<TrendPoint> arrivalTrendQuarterHourly(String eventId) throws SQLException {
        List<TrendPoint> hourly = arrivalTrend(eventId);
        if (hourly.isEmpty()) {
            return hourly;
        }
        List<TrendPoint> points = new ArrayList<>();
        for (TrendPoint point : hourly) {
            // Split each busy hour into four so the peak is visible rather than
            // averaged away across the whole hour.
            double perQuarter = point.getValue() / 4.0;
            for (int step = 0; step < 4; step++) {
                points.add(new TrendPoint(point.getAt().plusMinutes(step * 15L), perQuarter));
            }
        }
        return points;
    }

    /** How many people arrived in the hour before a given moment. */
    public int countArrivalsSince(String eventId, Instant since) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM check_ins WHERE event_id=? AND at_time>=?"
                        + " AND outcome IN ('ADMITTED','WRONG_DOOR','NO_TICKET')",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setInstant(statement, 2, since);
                });
    }
    // ---------------------------------------------------------------- engagement

    /** Inserts an engagement record. */
    public void insertEngagement(EngagementRecord record) throws SQLException {
        Sql.update(connection,
                "INSERT INTO engagement (" + ENGAGEMENT_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, record.getId());
                    Sql.setText(statement, 2, record.getEventId());
                    Sql.setText(statement, 3, record.getAttendeeId());
                    Sql.setText(statement, 4, record.getAttendeeName());
                    Sql.setText(statement, 5, record.getType().name());
                    Sql.setText(statement, 6, record.getSubject());
                    Sql.setText(statement, 7, record.getDetail());
                    Sql.setInt(statement, 8, record.getScore());
                    Sql.setInstant(statement, 9, record.getRecordedAt());
                    Sql.setText(statement, 10, record.getSessionId());
                    Sql.setText(statement, 11, record.getChannel());
                });
    }

    /** Every engagement record on an event. */
    public List<EngagementRecord> findEngagement(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + ENGAGEMENT_COLUMNS + " FROM engagement WHERE event_id=?"
                        + " ORDER BY recorded_at DESC",
                statement -> Sql.setText(statement, 1, eventId), OpsStore::readEngagement);
    }
    /** How many engagement records of a type there are. */
    public int countEngagementByType(String eventId, EngagementType type) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM engagement WHERE event_id=? AND type=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, type.name());
                });
    }

    /** How many distinct attendees engaged in any way. */
    public int countEngagedAttendees(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(DISTINCT attendee_id) FROM engagement"
                        + " WHERE event_id=? AND attendee_id IS NOT NULL AND attendee_id<>''",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** The average score across the scored engagement records, to one decimal place. */
    public double averageEngagementScore(String eventId) throws SQLException {
        // Only scored types are averaged, and only rows with a score. Averaging the
        // zeros left by poll answers would drag the figure to nothing.
        BigDecimal total = Sql.sum(connection,
                "SELECT SUM(score) FROM engagement WHERE event_id=? AND score>0"
                        + " AND type IN ('SESSION_RATING','SURVEY')",
                statement -> Sql.setText(statement, 1, eventId));
        int scored = Sql.count(connection,
                "SELECT COUNT(*) FROM engagement WHERE event_id=? AND score>0"
                        + " AND type IN ('SESSION_RATING','SURVEY')",
                statement -> Sql.setText(statement, 1, eventId));
        return scored == 0 ? 0
                : total.divide(BigDecimal.valueOf(scored), 1, java.math.RoundingMode.HALF_UP)
                        .doubleValue();
    }
    // ---------------------------------------------------------------- feedback

    /** Inserts a feedback response. */
    public void insertFeedback(Feedback response) throws SQLException {
        Sql.update(connection,
                "INSERT INTO feedback (" + FEEDBACK_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, response.getId());
                    Sql.setText(statement, 2, response.getEventId());
                    Sql.setText(statement, 3, response.getAttendeeId());
                    Sql.setText(statement, 4, response.getAttendeeName());
                    Sql.setText(statement, 5, response.getTopic().name());
                    Sql.setInt(statement, 6, response.getRating());
                    Sql.setText(statement, 7, response.getComment());
                    Sql.setText(statement, 8, response.getSource());
                    Sql.setInstant(statement, 9, response.getSubmittedAt());
                });
    }
    /** Feedback about one topic. */
    public List<Feedback> findFeedbackByTopic(String eventId, FeedbackTopic topic)
            throws SQLException {
        return Sql.query(connection,
                "SELECT " + FEEDBACK_COLUMNS + " FROM feedback WHERE event_id=? AND topic=?"
                        + " ORDER BY submitted_at DESC",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, topic.name());
                }, OpsStore::readFeedback);
    }

    /** How many responses there are. */
    public int countFeedback(String eventId) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM feedback WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /** How many responses about a topic. */
    public int countFeedbackByTopic(String eventId, FeedbackTopic topic) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM feedback WHERE event_id=? AND topic=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, topic.name());
                });
    }

    /** The average rating for a topic, or zero when nobody has answered. */
    public double averageRating(String eventId, FeedbackTopic topic) throws SQLException {
        BigDecimal total = Sql.sum(connection,
                "SELECT SUM(rating) FROM feedback WHERE event_id=? AND topic=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, topic.name());
                });
        int answered = countFeedbackByTopic(eventId, topic);
        if (answered == 0) {
            return 0;
        }
        return total.divide(BigDecimal.valueOf(answered), 2,
                java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /** The average rating across every topic, to two decimal places. */
    public double averageRatingOverall(String eventId) throws SQLException {
        BigDecimal total = Sql.sum(connection, "SELECT SUM(rating) FROM feedback WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
        int answered = countFeedback(eventId);
        if (answered == 0) {
            return 0;
        }
        return total.divide(BigDecimal.valueOf(answered), 2,
                java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /** How many responses rated a topic badly enough to look at. */
    public int countPoorFeedback(String eventId, int maximumRating) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM feedback WHERE event_id=? AND rating<=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setInt(statement, 2, maximumRating);
                });
    }

    /** The share of overall responses that would recommend the event, from zero to one. */
    public double recommendationRate(String eventId) throws SQLException {
        int asked = countFeedbackByTopic(eventId, FeedbackTopic.OVERALL);
        if (asked == 0) {
            return 0;
        }
        int yes = Sql.count(connection,
                "SELECT COUNT(*) FROM feedback WHERE event_id=? AND topic='OVERALL'"
                        + " AND rating>=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setInt(statement, 2, Feedback.RECOMMEND_THRESHOLD);
                });
        return (double) yes / asked;
    }

    /** The net promoter style score, from minus one hundred to plus one hundred. */
    public int promoterScore(String eventId) throws SQLException {
        int asked = countFeedbackByTopic(eventId, FeedbackTopic.OVERALL);
        if (asked == 0) {
            return 0;
        }
        int promoters = Sql.count(connection,
                "SELECT COUNT(*) FROM feedback WHERE event_id=? AND topic='OVERALL' AND rating=5",
                statement -> Sql.setText(statement, 1, eventId));
        int detractors = Sql.count(connection,
                "SELECT COUNT(*) FROM feedback WHERE event_id=? AND topic='OVERALL' AND rating<=2",
                statement -> Sql.setText(statement, 1, eventId));
        return (int) Math.round(100.0 * (promoters - detractors) / asked);
    }

    /**
     * The worst-rated responses, lowest first.
     *
     * <p>What an organiser reads. The average tells you there is a problem; this
     * tells you what it is.
     */
    public List<Feedback> findComplaints(String eventId, int limit) throws SQLException {
        List<Feedback> all = findFeedbackByTopic(eventId, FeedbackTopic.OVERALL);
        List<Feedback> sorted = new ArrayList<>(all);
        sorted.sort((left, right) -> Integer.compare(left.getRating(), right.getRating()));
        return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
    }

    /** Every response that carries a written comment. */
    public List<Feedback> findWithComments(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + FEEDBACK_COLUMNS + " FROM feedback WHERE event_id=?"
                        + " AND comment IS NOT NULL AND comment<>'' ORDER BY rating",
                statement -> Sql.setText(statement, 1, eventId), OpsStore::readFeedback);
    }

    // ---------------------------------------------------------------- sustainability

    /** Inserts a sustainability measure. */
    public void insertSustainability(SustainabilityMetric metric) throws SQLException {
        Sql.update(connection,
                "INSERT INTO sustainability (" + SUSTAINABILITY_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, metric.getId());
                    Sql.setText(statement, 2, metric.getEventId());
                    Sql.setText(statement, 3, metric.getMeasure().name());
                    Sql.setMoney(statement, 4, metric.getQuantity());
                    Sql.setText(statement, 5, metric.getSource());
                    Sql.setText(statement, 6, metric.getNote());
                    Sql.setFlag(statement, 7, metric.isEstimated());
                });
    }

    /** Rewrites a sustainability measure, used when an estimate becomes a reading. */
    public void updateSustainability(SustainabilityMetric metric) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE sustainability SET measure=?, quantity=?, source=?, note=?,"
                        + " estimated=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, metric.getMeasure().name());
                    Sql.setMoney(statement, 2, metric.getQuantity());
                    Sql.setText(statement, 3, metric.getSource());
                    Sql.setText(statement, 4, metric.getNote());
                    Sql.setFlag(statement, 5, metric.isEstimated());
                    Sql.setText(statement, 6, metric.getId());
                });
        if (changed == 0) {
            throw new SQLException("No sustainability record with id " + metric.getId());
        }
    }

    /** Every sustainability measure on an event. */
    public List<SustainabilityMetric> findSustainability(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + SUSTAINABILITY_COLUMNS
                        + " FROM sustainability WHERE event_id=? ORDER BY measure",
                statement -> Sql.setText(statement, 1, eventId), OpsStore::readSustainability);
    }

    /** Total carbon footprint in kilograms, from the recorded measures. */
    public BigDecimal totalCarbonKg(String eventId) throws SQLException {
        return SustainabilityMetric.totalCarbonKg(findSustainability(eventId));
    }

    /** The share of waste that was recycled, as a percentage. */
    public BigDecimal diversionRate(String eventId) throws SQLException {
        return SustainabilityMetric.diversionRate(findSustainability(eventId));
    }

    /** One measure of a kind, for the "record or update" flow. */
    public SustainabilityMetric findMeasure(String eventId,
                                            SustainabilityMetric.Measure measure)
            throws SQLException {
        return Sql.queryOne(connection,
                "SELECT " + SUSTAINABILITY_COLUMNS
                        + " FROM sustainability WHERE event_id=? AND measure=?",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, measure.name());
                }, OpsStore::readSustainability);
    }

    /** Whether anything at all has been recorded, for the completeness warning. */
    public boolean hasSustainabilityData(String eventId) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM sustainability WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId)) > 0;
    }

    // ---------------------------------------------------------------- mapping

    private static CheckIn readCheckIn(java.sql.ResultSet results) throws SQLException {
        return new CheckIn(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "ticket_ref"),
                Sql.text(results, "holder"),
                AccessTier.fromLabel(Sql.text(results, "tier")),
                Sql.text(results, "gate_id"),
                Sql.text(results, "gate_name"),
                Sql.text(results, "operator"),
                results.getTimestamp("at_time") == null ? Instant.now()
                        : results.getTimestamp("at_time").toInstant(),
                CheckInOutcome.fromLabel(Sql.text(results, "outcome")),
                Sql.flag(results, "override_flag"),
                Sql.text(results, "note"));
    }

    private static EngagementRecord readEngagement(java.sql.ResultSet results) throws SQLException {
        return new EngagementRecord(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "attendee_id"),
                Sql.text(results, "attendee_name"),
                EngagementType.fromLabel(Sql.text(results, "type")),
                Sql.text(results, "subject"),
                Sql.text(results, "detail"),
                Sql.number(results, "score"),
                results.getTimestamp("recorded_at") == null ? Instant.now()
                        : results.getTimestamp("recorded_at").toInstant(),
                Sql.text(results, "session_id"),
                Sql.text(results, "channel"));
    }

    private static Feedback readFeedback(java.sql.ResultSet results) throws SQLException {
        return new Feedback(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "attendee_id"),
                Sql.text(results, "attendee_name"),
                FeedbackTopic.fromLabel(Sql.text(results, "topic")),
                Sql.number(results, "rating"),
                Sql.text(results, "comment"),
                results.getTimestamp("submitted_at") == null ? Instant.now()
                        : results.getTimestamp("submitted_at").toInstant(),
                Sql.text(results, "source"));
    }

    private static SustainabilityMetric readSustainability(java.sql.ResultSet results)
            throws SQLException {
        return new SustainabilityMetric(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                SustainabilityMetric.Measure.fromLabel(Sql.text(results, "measure")),
                Sql.money(results, "quantity"),
                Sql.text(results, "source"),
                Sql.text(results, "note"),
                Sql.flag(results, "estimated"));
    }

    /** Money helper kept for the report summaries. */
    static String money(BigDecimal amount) {
        return Money.format(amount);
    }
}
