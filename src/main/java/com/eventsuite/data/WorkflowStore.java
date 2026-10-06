package com.eventsuite.data;

import com.eventsuite.workflow.AutomationLog;
import com.eventsuite.workflow.ConditionField;
import com.eventsuite.workflow.ConditionOperator;
import com.eventsuite.workflow.WorkflowAction;
import com.eventsuite.workflow.WorkflowRule;
import com.eventsuite.workflow.WorkflowTrigger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Stores and reads the automation rules and their log.
 *
 * <p>Rules are kept with an empty event id when they apply to every event, rather than
 * being copied per event. An organiser who has decided that every event should warn
 * them when tickets sell out has said it once, and copying the rule into each new
 * event would mean changing their mind eleven times over eleven events.
 *
 * <p>The log is capped rather than trimmed on read. An automation run happens every
 * few seconds while a dashboard is open, and an unbounded log would grow faster than
 * anything else in the file. Keeping the most recent few hundred is enough to explain
 * anything that has just happened, which is all it is ever for.
 */
public final class WorkflowStore {

    /**
     * How many log entries are kept per event.
     *
     * <p>Large enough that a whole day of polling is retained, small enough that the
     * table never becomes the reason the file is slow.
     */
    public static final int LOG_LIMIT = 500;

    private static final String RULE_COLUMNS =
            "id, event_id, name, trigger_name, field_name, operator_name, threshold,"
                    + " action_name, parameter, enabled, times_run, last_run";

    private static final String LOG_COLUMNS =
            "id, rule_id, rule_name, event_id, action_name, outcome, field_name, actual,"
                    + " threshold, at_time, detail";

    private final Connection connection;

    public WorkflowStore(Connection connection) {
        this.connection = connection;
    }

    // ---------------------------------------------------------------- rules

    /** Inserts a rule. */
    public void insertRule(WorkflowRule rule) throws SQLException {
        Sql.update(connection,
                "INSERT INTO workflow_rules (" + RULE_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                statement -> bindRule(statement, rule, true));
    }

    /** Rewrites a rule, including how many times it has fired. */
    public void updateRule(WorkflowRule rule) throws SQLException {
        int changed = Sql.update(connection,
                "UPDATE workflow_rules SET name=?, trigger_name=?, field_name=?,"
                        + " operator_name=?, threshold=?, action_name=?, parameter=?, enabled=?,"
                        + " times_run=?, last_run=? WHERE id=?",
                statement -> {
                    Sql.setText(statement, 1, rule.getName());
                    Sql.setText(statement, 2, rule.getTrigger().name());
                    Sql.setText(statement, 3, rule.getField().name());
                    Sql.setText(statement, 4, rule.getOperator().name());
                    Sql.setMoney(statement, 5, rule.getThreshold());
                    Sql.setText(statement, 6, rule.getAction().name());
                    Sql.setText(statement, 7, rule.getParameter());
                    Sql.setFlag(statement, 8, rule.isEnabled());
                    Sql.setInt(statement, 9, rule.getTimesRun());
                    Sql.setInstant(statement, 10, rule.getLastRunAt());
                    Sql.setText(statement, 11, rule.getId());
                });
        if (changed == 0) {
            throw new SQLException("No workflow rule to update with id " + rule.getId());
        }
    }

    private void bindRule(java.sql.PreparedStatement statement, WorkflowRule rule,
                          boolean includeEvent) throws SQLException {
        // One binding for both statements. The update simply never assigns column two,
        // because the event id is not the thing being edited: a rule belongs to the
        // event it was created against, and moving it between events would change
        // which events it fires for, which is a delete and a create rather than an edit.
        Sql.setText(statement, 1, rule.getId());
        Sql.setText(statement, 2, rule.getEventId());
        Sql.setText(statement, 3, rule.getName());
        Sql.setText(statement, 4, rule.getTrigger().name());
        Sql.setText(statement, 5, rule.getField().name());
        Sql.setText(statement, 6, rule.getOperator().name());
        Sql.setMoney(statement, 7, rule.getThreshold());
        Sql.setText(statement, 8, rule.getAction().name());
        Sql.setText(statement, 9, rule.getParameter());
        Sql.setFlag(statement, 10, rule.isEnabled());
        Sql.setInt(statement, 11, rule.getTimesRun());
        Sql.setInstant(statement, 12, rule.getLastRunAt());
    }
    /**
     * Every rule, event-wide ones first.
     *
     * <p>Ordered so the general rules appear above the event-specific ones in the list,
     * which matches the order they are likely to want to read them in.
     */
    public List<WorkflowRule> findAllRules() throws SQLException {
        return Sql.query(connection,
                "SELECT " + RULE_COLUMNS
                        + " FROM workflow_rules ORDER BY CASE WHEN event_id='' THEN 0 ELSE 1 END,"
                        + " name",
                null, WorkflowStore::readRule);
    }

    /** Rules applying to one event, including the event-wide ones. */
    public List<WorkflowRule> findRulesForEvent(String eventId) throws SQLException {
        return Sql.query(connection,
                "SELECT " + RULE_COLUMNS
                        + " FROM workflow_rules WHERE event_id='' OR event_id=?"
                        + " ORDER BY name",
                statement -> Sql.setText(statement, 1, eventId), WorkflowStore::readRule);
    }
    /** Removes a rule. Returns true when one was found. */
    public boolean deleteRule(String ruleId) throws SQLException {
        return Sql.delete(connection, "DELETE FROM workflow_rules WHERE id=?",
                statement -> Sql.setText(statement, 1, ruleId)) > 0;
    }
    /**
     * Whether an identical event-wide rule already exists.
     *
     * <p>Used when adding a starter set, so applying them twice does not leave two
     * copies of the same rule both firing.
     */
    public boolean hasEquivalentRule(WorkflowRule rule) throws SQLException {
        return Sql.count(connection,
                "SELECT COUNT(*) FROM workflow_rules WHERE event_id=? AND trigger_name=?"
                        + " AND field_name=? AND action_name=?",
                statement -> {
                    Sql.setText(statement, 1, rule.getEventId());
                    Sql.setText(statement, 2, rule.getTrigger().name());
                    Sql.setText(statement, 3, rule.getField().name());
                    Sql.setText(statement, 4, rule.getAction().name());
                }) > 0;
    }

    // ---------------------------------------------------------------- log

    /** Appends a log entry. */
    public void insertLog(AutomationLog entry) throws SQLException {
        Sql.update(connection,
                "INSERT INTO automation_log (" + LOG_COLUMNS + ")"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                statement -> {
                    Sql.setText(statement, 1, entry.getId());
                    Sql.setText(statement, 2, entry.getRuleId());
                    Sql.setText(statement, 3, entry.getRuleName());
                    Sql.setText(statement, 4, entry.getEventId());
                    Sql.setText(statement, 5, entry.getAction().name());
                    Sql.setText(statement, 6, entry.getOutcome().name());
                    Sql.setText(statement, 7, entry.getField() == null ? null
                            : entry.getField().name());
                    Sql.setMoney(statement, 8, entry.getActualValue());
                    Sql.setMoney(statement, 9, entry.getThreshold());
                    Sql.setInstant(statement, 10, entry.getAt());
                    Sql.setText(statement, 11, entry.getDetail());
                });
    }

    /** The most recent log entries for an event. */
    public List<AutomationLog> findLog(String eventId, int limit) throws SQLException {
        return Sql.query(connection,
                "SELECT " + LOG_COLUMNS + " FROM automation_log WHERE event_id=?"
                        + " ORDER BY at_time DESC LIMIT " + Math.max(1, limit),
                statement -> Sql.setText(statement, 1, eventId), WorkflowStore::readLog);
    }
    /** Log entries that did something, rather than rules that decided not to. */
    public List<AutomationLog> findActionedLog(String eventId, int limit) throws SQLException {
        return Sql.query(connection,
                "SELECT " + LOG_COLUMNS + " FROM automation_log WHERE event_id=?"
                        + " AND outcome='FIRED' ORDER BY at_time DESC LIMIT "
                        + Math.max(1, limit),
                statement -> Sql.setText(statement, 1, eventId), WorkflowStore::readLog);
    }

    /** How many log entries an event has. */
    public int countLog(String eventId) throws SQLException {
        return Sql.count(connection, "SELECT COUNT(*) FROM automation_log WHERE event_id=?",
                statement -> Sql.setText(statement, 1, eventId));
    }

    /**
     * Drops the oldest log entries for an event, keeping the most recent {@link #LOG_LIMIT}.
     *
     * <p>Called after a run rather than before it, so the table never grows unbounded
     * while the application is left running overnight with a dashboard open.
     */
    public int trimLog(String eventId) throws SQLException {
        return Sql.delete(connection,
                "DELETE FROM automation_log WHERE event_id=? AND id NOT IN"
                        + " (SELECT id FROM automation_log WHERE event_id=?"
                        + "  ORDER BY at_time DESC LIMIT " + LOG_LIMIT + ")",
                statement -> {
                    Sql.setText(statement, 1, eventId);
                    Sql.setText(statement, 2, eventId);
                });
    }

    // ---------------------------------------------------------------- mapping

    private static WorkflowRule readRule(java.sql.ResultSet results) throws SQLException {
        WorkflowRule rule = new WorkflowRule(
                Sql.text(results, "id"),
                Sql.text(results, "event_id"),
                Sql.text(results, "name"),
                WorkflowTrigger.fromLabel(Sql.text(results, "trigger_name")),
                ConditionField.fromLabel(Sql.text(results, "field_name")),
                ConditionOperator.fromLabel(Sql.text(results, "operator_name")),
                Sql.money(results, "threshold"),
                WorkflowAction.fromLabel(Sql.text(results, "action_name")),
                Sql.text(results, "parameter"),
                Sql.flag(results, "enabled"),
                Sql.number(results, "times_run"),
                Sql.instant(results, "last_run"));
        return rule;
    }

    private static AutomationLog readLog(java.sql.ResultSet results) throws SQLException {
        String fieldName = Sql.nullableText(results, "field_name");
        return new AutomationLog(
                Sql.text(results, "id"),
                Sql.text(results, "rule_id"),
                Sql.text(results, "rule_name"),
                Sql.text(results, "event_id"),
                WorkflowAction.fromLabel(Sql.text(results, "action_name")),
                AutomationLog.Outcome.fromLabel(Sql.text(results, "outcome")),
                fieldName == null ? null : ConditionField.fromLabel(fieldName),
                Sql.money(results, "actual"),
                Sql.money(results, "threshold"),
                results.getTimestamp("at_time") == null ? java.time.Instant.now()
                        : results.getTimestamp("at_time").toInstant(),
                Sql.text(results, "detail"));
    }
}
