package com.eventsuite.analytics;

import java.time.LocalDate;
import java.util.Locale;

/**
 * What kind of feedback was asked for.
 *
 * <p>The categories differ in what they are for. A satisfaction rating tells you
 * whether to run the event again. A recommendation score tells you whether to put
 * it in front of somebody else. A speaker rating tells you who to invite back. They
 * answer different questions, so they are kept apart and averaged separately.
 */
public enum FeedbackTopic {

    OVERALL("The event overall", 5),
    CONTENT("Quality of content", 5),
    SPEAKERS("Quality of speakers", 5),
    VENUE("Venue and facilities", 4),
    ORGANISATION("How well it was organised", 5),
    REGISTRATION("Registration and booking", 4),
    VALUE("Value for money", 5),
    NETWORKING("Networking opportunities", 4),
    CATERING("Food and drink", 3),
    ACCESSIBILITY("Accessibility", 5);

    private final String label;
    private final int importance;

    FeedbackTopic(String label, int importance) {
        this.label = label;
        this.importance = importance;
    }

    public String getLabel() {
        return label;
    }
    public static FeedbackTopic fromLabel(String text) {
        if (text != null) {
            String needle = text.trim().replace(" ", "").replace("_", "");
            for (FeedbackTopic topic : values()) {
                if (topic.name().replace("_", "").equalsIgnoreCase(needle)) {
                    return topic;
                }
                if (topic.label.replace(" ", "").equalsIgnoreCase(needle)) {
                    return topic;
                }
            }
        }
        return OVERALL;
    }

    @Override
    public String toString() {
        return label;
    }
}
