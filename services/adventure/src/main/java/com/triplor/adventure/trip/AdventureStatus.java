package com.triplor.adventure.trip;

public enum AdventureStatus {
    /** Accepting join requests. */
    OPEN,
    /** Every seat is taken. Goes back to OPEN if capacity is raised or a traveller leaves. */
    FULL,
    /** Cancelled by the organizer. Final. */
    CANCELLED,
    /** The trip is over. Final. */
    COMPLETED;

    /** Whether the organizer may still change the trip. */
    public boolean isEditable() {
        return this == OPEN || this == FULL;
    }
}
