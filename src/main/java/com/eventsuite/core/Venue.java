package com.eventsuite.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A place an event happens, held apart from the event itself.
 *
 * <p>Venues are shared. The same hall hosts the conference in March and the
 * awards dinner in November, and the things worth knowing about it — how many it
 * holds, whether it has a lectern, what it costs — stay the same both times. So
 * the venue is a record in its own right and an event points at one, rather than
 * the address being retyped into every event.
 *
 * <p>Rooms are held here too, because a venue's layout is a property of the
 * building. Booking "Main Hall" is a different operation from booking "the
 * building", and speakers and equipment are allocated to rooms.
 */
public final class Venue {

    private final String id;
    private final String name;
    private final String address;
    private final String city;
    private final int capacity;
    private final int rooms;
    private final boolean stepFreeAccess;
    private final boolean hasParking;
    private final boolean internetAccess;
    private final String contactName;
    private final String contactEmail;
    private final String contactPhone;
    private final String notes;
    private final List<String> facilities;
    private final List<String> roomNames;

    public Venue(String id,
                 String name,
                 String address,
                 String city,
                 int capacity,
                 int rooms,
                 boolean stepFreeAccess,
                 boolean hasParking,
                 boolean internetAccess,
                 String contactName,
                 String contactEmail,
                 String contactPhone,
                 String notes,
                 List<String> facilities,
                 List<String> roomNames) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.address = address == null ? "" : address;
        this.city = city == null ? "" : city;
        this.capacity = Math.max(0, capacity);
        // A venue always has at least one space, even if nobody has named it yet.
        // Zero rooms would make every allocation invalid, which is not what an
        // unnamed space means.
        this.rooms = Math.max(1, rooms);
        this.stepFreeAccess = stepFreeAccess;
        this.hasParking = hasParking;
        this.internetAccess = internetAccess;
        this.contactName = contactName == null ? "" : contactName;
        this.contactEmail = contactEmail == null ? "" : contactEmail;
        this.contactPhone = contactPhone == null ? "" : contactPhone;
        this.notes = notes == null ? "" : notes;
        this.facilities = unmodifiableDistinct(facilities);
        this.roomNames = unmodifiableDistinct(roomNames);
    }

    private static List<String> unmodifiableDistinct(List<String> source) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (source != null) {
            for (String value : source) {
                if (value != null && !value.isBlank()) {
                    cleaned.add(value.trim());
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(cleaned));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }
    public int getCapacity() {
        return capacity;
    }

    public int getRooms() {
        return rooms;
    }

    public boolean isStepFreeAccess() {
        return stepFreeAccess;
    }

    public boolean hasParking() {
        return hasParking;
    }

    public boolean hasInternetAccess() {
        return internetAccess;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getNotes() {
        return notes;
    }

    /** Lifts, sound system, blackout, catering kitchen and so on. */
    public List<String> getFacilities() {
        return facilities;
    }

    /** The named spaces inside the venue, used when booking speakers and kit. */
    public List<String> getRoomNames() {
        return roomNames;
    }
    @Override
    public String toString() {
        return getName();
    }
}
