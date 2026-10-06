package com.eventsuite.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The tables, and the reasoning behind them.
 *
 * <p>The shape is a plain relational one: every table carries the id of the event it
 * belongs to, so one file holds every registered event and a query for "the tickets
 * for this conference" is a filtered scan rather than a join across a catalogue. The
 * catalogue question — "what have we got coming up" — is answered by
 * {@code events} alone, without touching any of the detail tables at all, which is
 * what keeps the list screen fast no matter how much history has built up.
 *
 * <p>Each aggregate is stored once. A ticket's tier name is copied onto the ticket
 * rather than looked up, because the tier may later be renamed and the ticket that
 * was sold at the old price must still say what it said on the day. This is the
 * deliberate denormalisation of a historical record: what happened is kept as it was,
 * and the current state of the world lives in the tables beside it.
 *
 * <p>Foreign keys are declared but not cascaded. Deleting an event is deliberately a
 * refusal rather than a wipe — see {@code EventStore} — so the constraints here are a
 * safety net for a bug, not a design feature.
 */
public final class Schema {

    /**
     * Every table, ordered so that children come before parents.
     *
     * <p>{@code Database.truncateAll} walks this list backwards, and the tests rely on
     * that order to avoid a foreign key complaint part way through.
     */
    public static final String[] TABLES = {
            "automation_log",
            "workflow_rules",
            "sustainability",
            "feedback",
            "engagement",
            "check_ins",
            "resource_bookings",
            "resource_items",
            "campaigns",
            "discount_codes",
            "budget_lines",
            "invoices",
            "payments",
            "registrations",
            "attendees",
            "speakers",
            "ticket_types",
            "tickets",
            "venues",
            "events",
    };

    private Schema() {
    }

    /** Creates every table if it is not already there. */
    public static void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String ddl : DEFINITIONS) {
                // IF NOT EXISTS rather than checking first: it is one round trip
                // either way, and this way there is no window where two openings race.
                statement.execute(ddl);
            }
        }
    }
    private static final String[] DEFINITIONS = {

            // ---------------------------------------------------------------- catalogue

            """
            CREATE TABLE IF NOT EXISTS events (
                id            VARCHAR(64) PRIMARY KEY,
                name          VARCHAR(200) NOT NULL,
                category      VARCHAR(40)  NOT NULL,
                status        VARCHAR(24)  NOT NULL,
                venue_id      VARCHAR(64),
                venue_name    VARCHAR(160),
                start_date    DATE         NOT NULL,
                end_date      DATE         NOT NULL,
                doors_open    TIME,
                start_time    TIME,
                end_time      TIME,
                organiser     VARCHAR(160),
                description   CLOB,
                brand_primary VARCHAR(9),
                brand_accent  VARCHAR(9),
                website_url   VARCHAR(300),
                capacity      INT          NOT NULL,
                budget_planned DECIMAL(12,2),
                social_handle VARCHAR(120),
                tags          VARCHAR(400),
                published     BOOLEAN      DEFAULT FALSE,
                created_at    TIMESTAMP    NOT NULL
            )
            """,

            // An index on the three columns the catalogue sorts and filters by. The
            // list screen opens on "status then date" and filters by category, and
            // without these it reads the whole table.
            "CREATE INDEX IF NOT EXISTS ix_events_status ON events (status)",
            "CREATE INDEX IF NOT EXISTS ix_events_category ON events (category)",
            "CREATE INDEX IF NOT EXISTS ix_events_start ON events (start_date)",

            """
            CREATE TABLE IF NOT EXISTS venues (
                id           VARCHAR(64) PRIMARY KEY,
                name         VARCHAR(160) NOT NULL,
                address      VARCHAR(300),
                city         VARCHAR(120),
                capacity     INT          NOT NULL,
                rooms        INT          NOT NULL,
                step_free    BOOLEAN      DEFAULT FALSE,
                parking      BOOLEAN      DEFAULT FALSE,
                internet     BOOLEAN      DEFAULT TRUE,
                contact_name VARCHAR(160),
                contact_email VARCHAR(200),
                contact_phone VARCHAR(60),
                notes        CLOB,
                facilities   VARCHAR(400),
                room_names   VARCHAR(400)
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_venues_name ON venues (name)",

            // ---------------------------------------------------------------- ticketing

            """
            CREATE TABLE IF NOT EXISTS ticket_types (
                id          VARCHAR(64) PRIMARY KEY,
                event_id    VARCHAR(64)  NOT NULL,
                name        VARCHAR(120) NOT NULL,
                description CLOB,
                price       DECIMAL(12,2) NOT NULL,
                quantity    INT          NOT NULL,
                access_tier VARCHAR(24)  NOT NULL,
                sales_open  DATE,
                sales_close DATE,
                refundable  BOOLEAN      DEFAULT TRUE,
                sort_order  INT          DEFAULT 0
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_types_event ON ticket_types (event_id)",

            """
            CREATE TABLE IF NOT EXISTS tickets (
                reference      VARCHAR(40) PRIMARY KEY,
                event_id       VARCHAR(64)  NOT NULL,
                type_id        VARCHAR(64),
                attendee_id    VARCHAR(64)  NOT NULL,
                attendee_name  VARCHAR(160) NOT NULL,
                attendee_email VARCHAR(200),
                tier_name      VARCHAR(120) NOT NULL,
                access_tier    VARCHAR(24)  NOT NULL,
                price_paid     DECIMAL(12,2) NOT NULL,
                discount_code  VARCHAR(40),
                status         VARCHAR(20)  NOT NULL,
                issued_at      TIMESTAMP    NOT NULL,
                hold_expires   TIMESTAMP,
                checked_in_at  TIMESTAMP,
                gate           VARCHAR(80),
                checked_in_by  VARCHAR(120),
                seat_label     VARCHAR(40),
                notes          CLOB
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_tickets_event ON tickets (event_id)",
            "CREATE INDEX IF NOT EXISTS ix_tickets_attendee ON tickets (attendee_id)",
            // The door searches by reference, and the door list is sorted by arrival.
            "CREATE INDEX IF NOT EXISTS ix_tickets_checkin ON tickets (event_id, checked_in_at)",

            """
            CREATE TABLE IF NOT EXISTS registrations (
                id            VARCHAR(64) PRIMARY KEY,
                event_id      VARCHAR(64)  NOT NULL,
                attendee_id   VARCHAR(64)  NOT NULL,
                attendee_name VARCHAR(160) NOT NULL,
                attendee_email VARCHAR(200),
                ticket_ref    VARCHAR(40),
                referral      VARCHAR(120),
                fee           DECIMAL(12,2) NOT NULL,
                fee_paid      DECIMAL(12,2) NOT NULL,
                status        VARCHAR(20)  NOT NULL,
                waitlist_pos  VARCHAR(20),
                registered_at TIMESTAMP    NOT NULL
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_registrations_event ON registrations (event_id)",
            "CREATE INDEX IF NOT EXISTS ix_registrations_attendee ON registrations (attendee_id)",

            // ---------------------------------------------------------------- people

            """
            CREATE TABLE IF NOT EXISTS attendees (
                id             VARCHAR(64) PRIMARY KEY,
                first_name     VARCHAR(80)  NOT NULL,
                last_name      VARCHAR(80),
                email          VARCHAR(200) NOT NULL,
                phone          VARCHAR(60),
                organisation   VARCHAR(160),
                job_title      VARCHAR(120),
                dietary        CLOB,
                accessibility  CLOB,
                consent_marketing BOOLEAN   DEFAULT FALSE
            )
            """,

            // One row per person, so the same attendee cannot be registered twice.
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_attendees_email ON attendees (email)",

            """
            CREATE TABLE IF NOT EXISTS speakers (
                id           VARCHAR(64) PRIMARY KEY,
                event_id     VARCHAR(64)  NOT NULL,
                first_name   VARCHAR(80)  NOT NULL,
                last_name    VARCHAR(80),
                email        VARCHAR(200),
                phone        VARCHAR(60),
                organisation VARCHAR(160),
                job_title    VARCHAR(120),
                biography    CLOB,
                session_title VARCHAR(200),
                session_abstract CLOB,
                room_name    VARCHAR(120),
                session_start TIMESTAMP,
                session_end  TIMESTAMP,
                fee          DECIMAL(12,2) NOT NULL,
                travel_budget DECIMAL(12,2) NOT NULL,
                travel_notes CLOB,
                dietary      CLOB,
                accessibility CLOB,
                needs_hotel  BOOLEAN      DEFAULT FALSE,
                hotel_notes  CLOB,
                photo_path   VARCHAR(300),
                status       VARCHAR(20)  NOT NULL
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_speakers_event ON speakers (event_id, status)",

            """
            CREATE TABLE IF NOT EXISTS resource_items (
                id            VARCHAR(64) PRIMARY KEY,
                name          VARCHAR(160) NOT NULL,
                kind          VARCHAR(24)  NOT NULL,
                quantity      INT          NOT NULL,
                unit_cost     DECIMAL(12,2) NOT NULL,
                replacement   DECIMAL(12,2) NOT NULL,
                supplier      VARCHAR(160),
                notes         CLOB,
                hired         BOOLEAN      DEFAULT FALSE
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS resource_bookings (
                id          VARCHAR(64) PRIMARY KEY,
                event_id    VARCHAR(64)  NOT NULL,
                resource_id VARCHAR(64)  NOT NULL,
                quantity    INT          NOT NULL,
                from_at     TIMESTAMP,
                until_at    TIMESTAMP,
                purpose     VARCHAR(160),
                notes       CLOB,
                cost        DECIMAL(12,2) NOT NULL,
                returned    BOOLEAN      DEFAULT FALSE,
                damaged     BOOLEAN      DEFAULT FALSE
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_bookings_resource ON resource_bookings (resource_id)",

            // ---------------------------------------------------------------- finance

            """
            CREATE TABLE IF NOT EXISTS payments (
                id            VARCHAR(64) PRIMARY KEY,
                event_id      VARCHAR(64)  NOT NULL,
                reference     VARCHAR(40)  NOT NULL,
                counterparty  VARCHAR(160),
                description   CLOB,
                direction     VARCHAR(10)  NOT NULL,
                method        VARCHAR(20)  NOT NULL,
                amount        DECIMAL(12,2) NOT NULL,
                provider_fee  DECIMAL(12,2) NOT NULL,
                category      VARCHAR(60),
                ticket_ref    VARCHAR(40),
                attendee_id   VARCHAR(64),
                invoice_id    VARCHAR(64),
                note          CLOB,
                status        VARCHAR(20)  NOT NULL,
                raised_at     TIMESTAMP    NOT NULL,
                settled_at    TIMESTAMP,
                vat_applicable BOOLEAN     DEFAULT FALSE
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_payments_event ON payments (event_id, status)",
            "CREATE INDEX IF NOT EXISTS ix_payments_ticket ON payments (ticket_ref)",

            """
            CREATE TABLE IF NOT EXISTS invoices (
                id           VARCHAR(64) PRIMARY KEY,
                event_id     VARCHAR(64)  NOT NULL,
                number       VARCHAR(40)  NOT NULL,
                billed_to    VARCHAR(160) NOT NULL,
                billed_email VARCHAR(200),
                address      CLOB,
                purchase_order VARCHAR(60),
                issued_on    DATE         NOT NULL,
                due_on       DATE         NOT NULL,
                notes        CLOB,
                lines        CLOB         NOT NULL,
                paid_amount  DECIMAL(12,2) NOT NULL,
                paid_at      TIMESTAMP,
                status       VARCHAR(20)  NOT NULL
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_invoices_event ON invoices (event_id, status)",
            "CREATE INDEX IF NOT EXISTS ix_invoices_due ON invoices (due_on)",

            """
            CREATE TABLE IF NOT EXISTS budget_lines (
                id        VARCHAR(80) PRIMARY KEY,
                event_id  VARCHAR(64)  NOT NULL,
                category  VARCHAR(40)  NOT NULL,
                planned   DECIMAL(12,2) NOT NULL,
                committed DECIMAL(12,2) NOT NULL,
                actual    DECIMAL(12,2) NOT NULL,
                note      CLOB
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_budget_event ON budget_lines (event_id)",

            // ---------------------------------------------------------------- marketing

            """
            CREATE TABLE IF NOT EXISTS campaigns (
                id           VARCHAR(64) PRIMARY KEY,
                event_id     VARCHAR(64)  NOT NULL,
                name         VARCHAR(160) NOT NULL,
                channel      VARCHAR(28)  NOT NULL,
                budgeted     DECIMAL(12,2) NOT NULL,
                spent        DECIMAL(12,2) NOT NULL,
                reach_count  INT          NOT NULL,
                clicks       INT          NOT NULL,
                registrations INT         NOT NULL,
                starts_on    DATE,
                ends_on      DATE,
                audience     VARCHAR(160),
                message      CLOB,
                asset_notes  CLOB,
                stage        VARCHAR(20)  NOT NULL
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_campaigns_event ON campaigns (event_id, channel)",

            """
            CREATE TABLE IF NOT EXISTS discount_codes (
                id           VARCHAR(64) PRIMARY KEY,
                event_id     VARCHAR(64)  NOT NULL,
                code         VARCHAR(40)  NOT NULL,
                kind         VARCHAR(20)  NOT NULL,
                value_amt    DECIMAL(12,2) NOT NULL,
                usage_limit  INT          NOT NULL,
                times_used   INT          NOT NULL,
                valid_from   DATE,
                valid_until  DATE,
                applies_to   VARCHAR(200),
                note         CLOB
            )
            """,

            "CREATE UNIQUE INDEX IF NOT EXISTS ux_discount_code ON discount_codes (event_id, code)",

            // ---------------------------------------------------------------- on the day

            """
            CREATE TABLE IF NOT EXISTS check_ins (
                id         VARCHAR(64) PRIMARY KEY,
                event_id   VARCHAR(64)  NOT NULL,
                ticket_ref VARCHAR(40),
                holder     VARCHAR(160) NOT NULL,
                tier       VARCHAR(24)  NOT NULL,
                gate_id    VARCHAR(60),
                gate_name  VARCHAR(80),
                operator   VARCHAR(120),
                at_time    TIMESTAMP    NOT NULL,
                outcome    VARCHAR(20)  NOT NULL,
                override_flag BOOLEAN   DEFAULT FALSE,
                note       CLOB
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_checkins_event ON check_ins (event_id, at_time)",
            // Two arrivals of the same ticket at the same instant is a duplicated
            // scan, not two people, and this constraint is what makes the duplicate
            // check in the database rather than only in memory.
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_checkin_once ON check_ins (ticket_ref, event_id)",

            """
            CREATE TABLE IF NOT EXISTS engagement (
                id           VARCHAR(64) PRIMARY KEY,
                event_id     VARCHAR(64)  NOT NULL,
                attendee_id  VARCHAR(64),
                attendee_name VARCHAR(160),
                type         VARCHAR(28)  NOT NULL,
                subject      VARCHAR(200),
                detail       CLOB,
                score        INT          NOT NULL,
                recorded_at  TIMESTAMP    NOT NULL,
                session_id   VARCHAR(64),
                channel      VARCHAR(60)
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_engagement_event ON engagement (event_id, type)",

            """
            CREATE TABLE IF NOT EXISTS feedback (
                id            VARCHAR(64) PRIMARY KEY,
                event_id      VARCHAR(64)  NOT NULL,
                attendee_id   VARCHAR(64),
                attendee_name VARCHAR(160),
                topic         VARCHAR(30)  NOT NULL,
                rating        INT          NOT NULL,
                comment       CLOB,
                source        VARCHAR(40),
                submitted_at  TIMESTAMP    NOT NULL
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_feedback_event ON feedback (event_id, topic)",

            """
            CREATE TABLE IF NOT EXISTS sustainability (
                id        VARCHAR(64) PRIMARY KEY,
                event_id  VARCHAR(64)  NOT NULL,
                measure   VARCHAR(28)  NOT NULL,
                quantity  DECIMAL(14,2) NOT NULL,
                source    VARCHAR(120),
                note      CLOB,
                estimated BOOLEAN      DEFAULT FALSE
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_sustainability_event ON sustainability (event_id)",

            // ---------------------------------------------------------------- automation

            """
            CREATE TABLE IF NOT EXISTS workflow_rules (
                id         VARCHAR(64) PRIMARY KEY,
                event_id   VARCHAR(64),
                name       VARCHAR(160) NOT NULL,
                trigger_name VARCHAR(28) NOT NULL,
                field_name VARCHAR(40)  NOT NULL,
                operator_name VARCHAR(20) NOT NULL,
                threshold  DECIMAL(14,2) NOT NULL,
                action_name VARCHAR(28) NOT NULL,
                parameter  CLOB,
                enabled    BOOLEAN      DEFAULT TRUE,
                times_run  INT          DEFAULT 0,
                last_run   TIMESTAMP
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS automation_log (
                id        VARCHAR(64) PRIMARY KEY,
                rule_id   VARCHAR(64),
                rule_name VARCHAR(160),
                event_id  VARCHAR(64),
                action_name VARCHAR(28) NOT NULL,
                outcome   VARCHAR(28)  NOT NULL,
                field_name VARCHAR(40),
                actual    DECIMAL(14,2),
                threshold DECIMAL(14,2),
                at_time   TIMESTAMP    NOT NULL,
                detail    CLOB
            )
            """,

            "CREATE INDEX IF NOT EXISTS ix_automation_event ON automation_log (event_id, at_time)",
    };
}
