package com.eventsuite.core;

/**
 * The kind of event, which is the first split the catalogue shows.
 *
 * <p>Categories exist because "all events" is only useful if it can be narrowed.
 * Someone running a festival and someone running a two-hour webinar have almost
 * nothing in common beyond the word "event", so the category decides which
 * screens care about them: a conference is mostly speakers and sessions, a
 * concert is mostly capacity and stage budget, a webinar has no venue at all.
 *
 * <p>Each category carries the defaults that shape the rest of the system, so
 * creating a "Conference" pre-selects the ticket types, budget headings and
 * checklist that kind of event actually needs. The numbers are starting points,
 * not rules: a 5,000-seat conference is still a conference, and the organiser
 * can override every one of them.
 */
public enum EventCategory {

    CONFERENCE("Conference", "Multi-track talks, sessions and panels", 400, 8,
            new String[]{"Standard Pass", "Premium Pass", "Speaker Pass", "Workshop Add-on"}),

    WORKSHOP("Workshop", "Hands-on sessions in small groups", 25, 3,
            new String[]{"Standard Seat", "Early Bird", "Group Seat"}),

    CONCERT("Concert", "Live music with a staged audience", 5000, 5,
            new String[]{"General Admission", "Front Row", "Balcony", "VIP Lounge"}),

    FESTIVAL("Festival", "Multi-day, multi-stage public programme", 20000, 10,
            new String[]{"Day Pass", "Weekend Pass", "VIP Weekend", "Child Ticket"}),

    SPORTS("Sports", "Competitions, fixtures and tournaments", 15000, 8,
            new String[]{"General Admission", "Terrace", "Club Stand", "Hospitality"}),

    EXHIBITION("Exhibition", "Trade stands, showcases and demos", 1500, 6,
            new String[]{"Day Pass", "Trade Pass", "Exhibitor Pass", "Public Day"}),

    NETWORKING("Networking", "Mixers, meetups and professional socials", 150, 4,
            new String[]{"General Entry", "Member Entry", "Supper Ticket"}),

    TRAINING("Training", "Courses, certifications and coaching", 30, 5,
            new String[]{"Course Seat", "Certification Track", "Team Pass"}),

    WEBINAR("Webinar", "Online-only sessions with no physical venue", 500, 3,
            new String[]{"Free Access", "Paid Access", "Replay Access"}),

    PRODUCT_LAUNCH("Product Launch", "Reveal events and press showcases", 300, 6,
            new String[]{"Invitation", "Press Pass", "Public Ticket", "VIP"}),

    COMMUNITY("Community", "Local gatherings, cultural and social events", 200, 5,
            new String[]{"Free Entry", "Supporter Ticket", "Family Pass"}),

    WEDDING("Wedding", "Private celebrations", 120, 7,
            new String[]{"Ceremony Guest", "Reception Guest", "Wedding Party"}),

    OTHER("Other", "Anything that does not fit the categories above", 100, 4,
            new String[]{"Standard Entry", "Sponsor Ticket", "Complimentary"});

    private final String label;
    private final String description;
    private final int suggestedCapacity;
    private final int suggestedSpeakers;
    private final String[] suggestedTicketTypes;

    EventCategory(String label, String description, int suggestedCapacity,
                  int suggestedSpeakers, String[] suggestedTicketTypes) {
        this.label = label;
        this.description = description;
        this.suggestedCapacity = suggestedCapacity;
        this.suggestedSpeakers = suggestedSpeakers;
        this.suggestedTicketTypes = suggestedTicketTypes;
    }

    /** The name shown on screen. */
    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * The capacity a new event of this kind starts with.
     *
     * <p>Wedding defaults are small and webinar defaults are large because that is
     * the honest expectation, and starting from it saves inventing a number in a
     * hurry.
     */
    public int getSuggestedCapacity() {
        return suggestedCapacity;
    }

    public int getSuggestedSpeakers() {
        return suggestedSpeakers;
    }

    /** Ticket types offered when a new event of this kind is created. */
    public String[] getSuggestedTicketTypes() {
        return suggestedTicketTypes.clone();
    }

    /** How many speakers actually carry a session, and so want a record. */
    public boolean tracksSpeakers() {
        return this == CONFERENCE || this == TRAINING || this == WEBINAR
                || this == PRODUCT_LAUNCH || this == WORKSHOP;
    }

    /** Whether this kind of event happens at a place people travel to. */
    public boolean needsVenue() {
        return this != WEBINAR;
    }
    /**
     * Whether capacity should be multiplied by the number of days.
     *
     * <p>A three-day festival sells roughly three times as many day passes as one
     * day sells, so capping total inventory at the room's capacity would sell out
     * on the opening morning. Conferences are excluded because their capacity is
     * simultaneous attendance, not attendance across the whole run.
     */
    public boolean multipliesCapacityByDays() {
        return this == FESTIVAL || this == EXHIBITION;
    }

    /** The category with this name, ignoring case and spaces, or OTHER. */
    public static EventCategory fromLabel(String text) {
        if (text == null) {
            return OTHER;
        }
        String needle = text.trim().replace(" ", "").replace("_", "");
        for (EventCategory category : values()) {
            if (category.name().replace("_", "").equalsIgnoreCase(needle)) {
                return category;
            }
            if (category.label.replace(" ", "").equalsIgnoreCase(needle)) {
                return category;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
