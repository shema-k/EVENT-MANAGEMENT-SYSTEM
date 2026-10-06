package com.eventsuite.analytics;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One person's answers about an event, after the event.
 *
 * <p>Feedback is collected as ratings out of five per topic plus free text, because
 * a single overall number hides the thing that actually went wrong. Somebody who
 * rates the content highly and the venue badly has told you exactly where the
 * problem is, and collapsing that into one score loses it.
 *
 * <p>The free text is kept, not just the numbers. It is also the part people are
 * tempted to skip, which is a mistake: a hundred ratings of eight with one sentence
 * explaining why is more useful than a hundred ratings and no explanation.
 */
public final class Feedback implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Ratings are on a five point scale. */
    public static final int MAX_RATING = 5;

    /** At or above this, and the respondent would recommend the event. */
    public static final int RECOMMEND_THRESHOLD = 4;

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.UK);

    private final String id;
    private final String eventId;
    private final String attendeeId;
    private final String attendeeName;
    private final FeedbackTopic topic;
    private final int rating;
    private final String comment;
    private final Instant submittedAt;
    private final String source;

    public Feedback(String id,
                    String eventId,
                    String attendeeId,
                    String attendeeName,
                    FeedbackTopic topic,
                    int rating,
                    String comment,
                    Instant submittedAt,
                    String source) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.attendeeId = attendeeId == null ? "" : attendeeId;
        this.attendeeName = attendeeName == null ? "" : attendeeName;
        this.topic = topic == null ? FeedbackTopic.OVERALL : topic;
        if (rating < 1 || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                    "Rating must be between 1 and " + MAX_RATING + ", but was " + rating);
        }
        this.rating = rating;
        this.comment = comment == null ? "" : comment.trim();
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.source = source == null ? "" : source;
    }
    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAttendeeId() {
        return attendeeId;
    }

    /** Who answered, or empty when the response was anonymous. */
    public String getAttendeeName() {
        return attendeeName;
    }

    public FeedbackTopic getTopic() {
        return topic;
    }

    /** One to five. */
    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    /** How the response was collected: email, app, paper, desk. */
    public String getSource() {
        return source;
    }
    /** Whether the respondent would recommend the event, judged on the overall score. */
    public boolean wouldRecommend() {
        return topic == FeedbackTopic.OVERALL && rating >= RECOMMEND_THRESHOLD;
    }
    /**
     * Whether this comment is worth putting in front of the organisers.
     *
     * <p>Deliberately simple: a low rating with a real sentence, or a high one. A
     * four out of five with a paragraph about parking is worth reading too, so the
     * test is length rather than sentiment.
     */
    public boolean isActionable() {
        return getComment().length() >= 20;
    }

    /** What this rating means, in words. */
    public String getRatingLabel() {
        return switch (rating) {
            case 5 -> "Excellent";
            case 4 -> "Good";
            case 3 -> "Mixed";
            case 2 -> "Poor";
            default -> "Very poor";
        };
    }

    /** A line for the feedback list. */
    public String getDisplayLine() {
        StringBuilder line = new StringBuilder(topic.getLabel());
        line.append("  ").append(rating).append("/").append(MAX_RATING)
                .append(" ").append(getRatingLabel());
        if (!attendeeName.isEmpty()) {
            line.append("  - ").append(attendeeName);
        }
        if (!getComment().isEmpty()) {
            String comment = getComment().length() > 60
                    ? getComment().substring(0, 57) + "..." : getComment();
            line.append("  ").append(comment);
        }
        return line.toString();
    }

    public String getWhenLabel() {
        return WHEN.format(LocalDateTime.ofInstant(submittedAt, ZoneId.systemDefault()));
    }

    /**
     * The average rating for a set of responses.
     *
     * @return zero when there is nothing to average, rather than throwing
     */
    public static double averageRating(java.util.Collection<Feedback> responses) {
        if (responses == null || responses.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Feedback response : responses) {
            total += response.getRating();
        }
        return (double) total / responses.size();
    }

    /**
     * The share of respondents who would recommend the event, as a fraction of one.
     *
     * <p>Judged only on overall ratings. A comment praising the speakers does not
     * make somebody a promoter, so counting topic ratings would inflate this and
     * make the figure worth less.
     */
    public static double recommendationRate(java.util.Collection<Feedback> responses) {
        if (responses == null) {
            return 0;
        }
        int asked = 0;
        int yes = 0;
        for (Feedback response : responses) {
            if (response.getTopic() == FeedbackTopic.OVERALL) {
                asked++;
                if (response.wouldRecommend()) {
                    yes++;
                }
            }
        }
        return asked == 0 ? 0 : (double) yes / asked;
    }

    /**
     * The net promoter style score, from minus one hundred to plus one hundred.
     *
     * <p>Promoters score five, detractors score one to two, and everybody between
     * is passive and counted as neither. The gap is the score. Only overall ratings
     * count, for the same reason as the recommendation rate.
     */
    public static int promoterScore(java.util.Collection<Feedback> responses) {
        if (responses == null) {
            return 0;
        }
        int promoters = 0;
        int detractors = 0;
        for (Feedback response : responses) {
            if (response.getTopic() != FeedbackTopic.OVERALL) {
                continue;
            }
            if (response.getRating() >= 5) {
                promoters++;
            } else if (response.getRating() <= 2) {
                detractors++;
            }
        }
        if (promoters + detractors == 0) {
            return 0;
        }
        return (int) Math.round(100.0 * (promoters - detractors) / responses.size());
    }
    @Override
    public String toString() {
        return getDisplayLine();
    }
}
