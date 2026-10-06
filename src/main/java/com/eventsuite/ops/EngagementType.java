package com.eventsuite.ops;

/**
 * Something an attendee did while they were there, or was asked.
 *
 * <p>Engagement is the part of running an event that has no ticket attached. A
 * question answered in a poll, a session rated afterwards, a conversation had in a
 * networking app, a survey returned: each is a small record, and together they are
 * the only way to know whether the content was any good. Sales figures say how many
 * people came; this says what it was like.
 */
public enum EngagementType {

    /** A poll or quiz answered in the room. */
    POLL_VOTE("Poll vote", true),

    /** A question asked at a session and voted on by the room. */
    SESSION_QUESTION("Session question", false),

    /** A rating left for a session, a speaker or the event. */
    SESSION_RATING("Session rating", true),

    /** A survey returned, with a score out of ten. */
    SURVEY("Survey", true),

    /** A connection made through the networking tools. */
    CONNECTION("Connection made", true),

    /** A message, question or request sent to the organisers. */
    MESSAGE("Message", false),

    /** A question and its answer, tracked for later to publish. */
    ASKED_AND_ANSWERED("Q&A", false),

    /** Attendance at a particular session, for popularity ranking. */
    SESSION_ATTENDANCE("Session attendance", false);

    private final String label;
    private final boolean scored;

    EngagementType(String label, boolean scored) {
        this.label = label;
        this.scored = scored;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Whether records of this type carry a score out of ten.
     *
     * <p>The distinction decides what the dashboards can average. Averaging a score
     * over polls and survey returns is meaningful; averaging it over questions asked
     * is not, because a question has no opinion attached to it.
     */
    public boolean isScored() {
        return scored;
    }

    /** Whether this type contributes to the overall satisfaction figure. */
    public boolean countsTowardsSatisfaction() {
        return this == SESSION_RATING || this == SURVEY;
    }
    public static EngagementType fromLabel(String text) {
        if (text == null) {
            return SURVEY;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (EngagementType type : values()) {
            if (type.name().replace("_", "").equalsIgnoreCase(needle)) {
                return type;
            }
            if (type.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return type;
            }
        }
        return SURVEY;
    }

    @Override
    public String toString() {
        return label;
    }
}
