package com.eventsuite.core;

import com.eventsuite.finance.Money;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One event, and the single record everything else in the system hangs off.
 *
 * <p>An event owns its own name, when and where it happens, who is organising it,
 * how it is branded and how much it is allowed to cost. It does not own tickets,
 * speakers, invoices or check-ins: those are separate records that all carry the
 * event's id, so that deleting a draft event takes its paperwork with it and a
 * completed event's financials can be read long after the event itself is done.
 *
 * <p>The capacity figure is the contract the rest of the system enforces. It is
 * what stops sales, it sizes the check-in plan, and it is the denominator for
 * every attendance percentage on the dashboards. That is why it is validated on
 * the way in rather than discovered to be wrong at the door.
 */
public final class Event implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Used when the organiser types nothing. */
    public static final String UNNAMED = "Untitled event";

    private final String id;
    private final String name;
    private final EventCategory category;
    private final String venueId;
    private final String venueName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalTime doorsOpen;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String organiser;
    private final String description;
    private final String brandPrimary;
    private final String brandAccent;
    private final String websiteUrl;
    private final int capacity;
    private final ArrayList<String> tags;

    private EventStatus status;
    private BigDecimal budgetPlanned;
    private String socialHandle;
    private boolean published;

    /**
     * @param capacity how many people may be admitted in total; must be positive
     */
    public Event(String id,
                 String name,
                 EventCategory category,
                 String venueId,
                 String venueName,
                 LocalDate startDate,
                 LocalDate endDate,
                 LocalTime doorsOpen,
                 LocalTime startTime,
                 LocalTime endTime,
                 String organiser,
                 String description,
                 String brandPrimary,
                 String brandAccent,
                 String websiteUrl,
                 int capacity,
                 List<String> tags,
                 EventStatus status,
                 BigDecimal budgetPlanned,
                 String socialHandle) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = normaliseName(name);
        this.category = category == null ? EventCategory.OTHER : category;
        this.venueId = venueId == null ? "" : venueId;
        this.venueName = venueName == null ? "" : venueName;
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        // A one-day event has no separate end date. Defaulting rather than
        // rejecting means "just the 14th" can be typed without a second field.
        this.endDate = endDate == null || endDate.isBefore(startDate) ? startDate : endDate;
        this.doorsOpen = doorsOpen == null ? LocalTime.of(9, 0) : doorsOpen;
        this.startTime = startTime == null ? LocalTime.of(10, 0) : startTime;
        this.endTime = endTime == null ? LocalTime.of(17, 0) : endTime;
        this.organiser = organiser == null ? "" : organiser;
        this.description = description == null ? "" : description;
        this.brandPrimary = normaliseColour(brandPrimary);
        this.brandAccent = normaliseColour(brandAccent);
        this.websiteUrl = websiteUrl == null ? "" : websiteUrl.trim();
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be at least 1, but was " + capacity + " for " + this.name);
        }
        this.capacity = capacity;
        this.tags = normaliseTags(tags);
        this.status = status == null ? EventStatus.DRAFT : status;
        this.budgetPlanned = Money.of(budgetPlanned);
        this.socialHandle = socialHandle == null ? "" : socialHandle.trim();
        // An event is only on the public website once it is published or further
        // along. Deriving this from the status stops "draft but visible".
        this.published = this.status.isSettled()
                || this.status == EventStatus.PUBLISHED
                || this.status == EventStatus.ON_SALE
                || this.status == EventStatus.SOLD_OUT
                || this.status == EventStatus.IN_PROGRESS;
    }

    private static String normaliseName(String name) {
        return name == null || name.isBlank() ? UNNAMED : name.trim();
    }

    /**
     * Cleans a hex colour into the six-digit form the interface can paint with.
     *
     * <p>An unparseable colour falls back to a default rather than throwing. A
     * branding field should never be the reason an event cannot be saved.
     */
    private static String normaliseColour(String colour) {
        if (colour == null || colour.isBlank()) {
            return "";
        }
        String text = colour.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() == 3) {
            // #abc is a common way of writing a colour; expand it to #aabbcc.
            StringBuilder expanded = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                expanded.append(text.charAt(i)).append(text.charAt(i));
            }
            text = expanded.toString();
        }
        if (text.length() != 6) {
            return "";
        }
        try {
            Integer.parseInt(text, 16);
            return "#" + text.toUpperCase(Locale.ROOT);
        } catch (NumberFormatException notAColour) {
            return "";
        }
    }

    private static ArrayList<String> normaliseTags(List<String> source) {
        ArrayList<String> cleaned = new ArrayList<>();
        if (source != null) {
            for (String tag : source) {
                if (tag != null && !tag.isBlank() && !cleaned.contains(tag.trim())) {
                    cleaned.add(tag.trim());
                }
            }
        }
        return cleaned;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public EventCategory getCategory() {
        return category;
    }

    public String getVenueId() {
        return venueId;
    }

    public String getVenueName() {
        return venueName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalTime getDoorsOpen() {
        return doorsOpen;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getOrganiser() {
        return organiser;
    }

    public String getDescription() {
        return description;
    }

    /** The event's main colour, as {@code #RRGGBB}, or empty to use the theme. */
    public String getBrandPrimary() {
        return brandPrimary;
    }

    public String getBrandAccent() {
        return brandAccent;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public int getCapacity() {
        return capacity;
    }

    /**
     * Free-text labels used to filter the catalogue, such as "corporate".
     *
     * <p>Handed out as an unmodifiable view so the stored list stays a real
     * {@link ArrayList} and therefore actually serialises.
     */
    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public EventStatus getStatus() {
        return status;
    }

    public BigDecimal getBudgetPlanned() {
        return budgetPlanned;
    }

    public String getSocialHandle() {
        return socialHandle;
    }

    public boolean isPublished() {
        return published;
    }

    /** The size band this event falls into, from its capacity. */
    public EventScale getScale() {
        return EventScale.of(capacity);
    }

    /** How many days it runs for. A single-day event returns 1, never 0. */
    public int getDurationDays() {
        return (int) Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    public boolean isMultiDay() {
        return getDurationDays() > 1;
    }

    /**
     * Total tickets that may ever be sold.
     *
     * <p>For a festival or an exhibition this is capacity multiplied by the number
     * of days, because each day is sold separately. For a conference the capacity
     * is simultaneous attendance, so it is not multiplied: three days of talks
     * still hold five hundred people at once.
     */
    public int getSellableCapacity() {
        return category.multipliesCapacityByDays() ? capacity * getDurationDays() : capacity;
    }

    /** The date and time doors open, which is when check-in becomes available. */
    public LocalDateTime getDoorsOpenAt() {
        return LocalDateTime.of(startDate, doorsOpen);
    }

    public LocalDateTime getStartsAt() {
        return LocalDateTime.of(startDate, startTime);
    }

    public LocalDateTime getEndsAt() {
        return LocalDateTime.of(endDate, endTime);
    }

    /** Whether the given moment falls between doors opening and the end. */
    public boolean isHappeningAt(LocalDateTime moment) {
        return !moment.isBefore(getDoorsOpenAt()) && !moment.isAfter(getEndsAt());
    }

    /**
     * Days until doors open. Negative once the date has passed, so a caller that
     * wants "days remaining" can clamp it and a caller that wants "days since"
     * can use the sign.
     */
    public long getDaysUntil(LocalDate today) {
        return ChronoUnit.DAYS.between(today, startDate);
    }
    /** Whether the event can go straight on sale without further checks. */
    public boolean isReadyToPublish() {
        return !getName().equals(UNNAMED) && !getDescription().isBlank()
                && (category.needsVenue() ? !getVenueName().isEmpty() : true);
    }

    /** Everything missing before this event could be announced. */
    public List<String> getPublishingGaps() {
        List<String> gaps = new ArrayList<>();
        if (getName().equals(UNNAMED)) {
            gaps.add("a name");
        }
        if (getDescription().isBlank()) {
            gaps.add("a description");
        }
        if (category.needsVenue() && getVenueName().isBlank()) {
            gaps.add("a venue");
        }
        if (getOrganiser().isBlank()) {
            gaps.add("an organiser");
        }
        return gaps;
    }

    /**
     * Moves the event to a new lifecycle state.
     *
     * @return true when the move happened
     * @throws IllegalStateException when the lifecycle does not allow the move
     */
    public boolean moveTo(EventStatus next) {
        if (status.canMoveTo(next)) {
            status = next;
            published = next.isSettled()
                    || next == EventStatus.PUBLISHED
                    || next == EventStatus.ON_SALE
                    || next == EventStatus.SOLD_OUT
                    || next == EventStatus.IN_PROGRESS;
            return true;
        }
        throw new IllegalStateException(
                "Cannot move '" + getName() + "' from " + status.getLabel()
                        + " to " + (next == null ? "nothing" : next.getLabel()));
    }

    public void setBudgetPlanned(BigDecimal amount) {
        this.budgetPlanned = Money.of(amount);
    }
    /**
     * Records a status directly, for loading from the database and for the tests.
     *
     * <p>{@link #moveTo(EventStatus)} is the rule-checked way to change state.
     * This one bypasses the check on purpose, because a row read back from disk
     * must be able to hold a state that today's rules would not produce; otherwise
     * an old database could not be opened.
     */
    public void restoreStatus(EventStatus restored) {
        this.status = restored == null ? EventStatus.DRAFT : restored;
        this.published = this.status.isSettled()
                || this.status != EventStatus.DRAFT && this.status != EventStatus.PLANNING;
    }

    /** One line for a list: the name, its dates and where it is. */
    public String getDisplayLine() {
        StringBuilder text = new StringBuilder(getName());
        text.append("  |  ").append(getCategory().getLabel());
        if (!getVenueName().isEmpty()) {
            text.append("  |  ").append(getVenueName());
        }
        return text.toString();
    }

    /** The dates as a readable range, collapsing a single day to just the date. */
    public String getDateRange() {
        if (!isMultiDay()) {
            return getStartDate().toString();
        }
        return getStartDate() + " to " + getEndDate();
    }

    @Override
    public String toString() {
        return getName() + " (" + getCategory().getLabel() + ")";
    }
}
