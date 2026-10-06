package com.eventsuite.ops;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Something an attendee did, or was asked, during the event.
 *
 * <p>The record is intentionally generic: a poll answer, a session rating, a
 * survey score and a connection made all take the same shape, because they all
 * answer the same question — did this person take part, and what did they think.
 * Modelling them separately would mean four different tables and four different
 * ways of being wrong on the engagement dashboard.
 *
 * <p>The score is out of ten when the type carries one. Zero means no score rather
 * than a score of zero, which is why {@link #getAverageScore()} ignores records
 * without one rather than dragging an average to zero.
 */
public final class EngagementRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Scores are collected on a ten point scale, so ten is the ceiling. */
    public static final int MAX_SCORE = 10;

    private final String id;
    private final String eventId;
    private final String attendeeId;
    private final String attendeeName;
    private final EngagementType type;
    private final String subject;
    private final String detail;
    private final int score;
    private final Instant recordedAt;
    private final String sessionId;
    private final String channel;

    public EngagementRecord(String id,
                            String eventId,
                            String attendeeId,
                            String attendeeName,
                            EngagementType type,
                            String subject,
                            String detail,
                            int score,
                            Instant recordedAt,
                            String sessionId,
                            String channel) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.attendeeId = attendeeId == null ? "" : attendeeId;
        this.attendeeName = attendeeName == null ? "" : attendeeName;
        this.type = type == null ? EngagementType.SURVEY : type;
        this.subject = subject == null ? "" : subject;
        this.detail = detail == null ? "" : detail;
        if (score < 0 || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "Score must be between 0 and " + MAX_SCORE + ", but was " + score);
        }
        this.score = score;
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.sessionId = sessionId == null ? "" : sessionId;
        this.channel = channel == null ? "" : channel;
    }
    /** An unscored record, such as a poll answer or a question asked. */
    public static EngagementRecord plain(String id, String eventId, String attendeeId,
                                        String attendeeName, EngagementType type,
                                        String subject, String detail, Instant at) {
        return new EngagementRecord(id, eventId, attendeeId, attendeeName, type, subject, detail,
                0, at, "", "");
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    /** Which attendee it belongs to, empty for an anonymous response. */
    public String getAttendeeId() {
        return attendeeId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public EngagementType getType() {
        return type;
    }

    /** What it is about: a session title, a speaker, "the event". */
    public String getSubject() {
        return subject;
    }

    /** Free text: the comment, the answer, the question. */
    public String getDetail() {
        return detail;
    }

    /** Out of ten, or zero when this type carries no score. */
    public int getScore() {
        return score;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    /** The session this belongs to, where relevant. */
    public String getSessionId() {
        return sessionId;
    }

    /** Where it came from: "app", "slide", "email", "desk". */
    public String getChannel() {
        return channel;
    }

    /** Whether this record carries a score worth averaging. */
    public boolean hasScore() {
        return type.isScored() && score > 0;
    }

    /** Whether this record counts towards the satisfaction figure. */
    public boolean countsTowardsSatisfaction() {
        return type.countsTowardsSatisfaction() && hasScore();
    }
    /**
     * The label used for a score of this value.
     *
     * <p>Deliberately a band rather than a word per number: eight different labels
     * for eight different numbers invents precision that a ten point scale does not
     * carry.
     */
    public String getScoreLabel() {
        if (!hasScore()) {
            return "No score";
        }
        if (score >= 9) {
            return "Delighted";
        }
        if (score >= 7) {
            return "Positive";
        }
        if (score >= 5) {
            return "Neutral";
        }
        return "Disappointed";
    }

    public LocalDateTime getLocalTime() {
        return LocalDateTime.ofInstant(recordedAt, ZoneId.systemDefault());
    }

    /** Whether this record matches a typed search. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return subject.toLowerCase(Locale.ROOT).contains(needle)
                || detail.toLowerCase(Locale.ROOT).contains(needle)
                || attendeeName.toLowerCase(Locale.ROOT).contains(needle)
                || type.getLabel().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** A line for the engagement feed. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(type.getLabel());
        if (!subject.isEmpty()) {
            line.append("  ").append(subject);
        }
        if (hasScore()) {
            line.append("  ").append(score).append("/").append(MAX_SCORE)
                    .append(" ").append(getScoreLabel());
        }
        if (!attendeeName.isEmpty()) {
            line.append("  - ").append(attendeeName);
        }
        return line.toString();
    }
    @Override
    public String toString() {
        return getDisplayLine();
    }
}
