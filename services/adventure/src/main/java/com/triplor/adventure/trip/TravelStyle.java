package com.triplor.adventure.trip;

/**
 * The kinds of trip. The same names are used by the identity service's profiles and by the
 * check constraint on {@code adventures.styles}; a new style needs a migration.
 */
public enum TravelStyle {
    ADVENTURE,
    BACKPACKING,
    RELAXING,
    FOODIE,
    CULTURE,
    HIKING
}
