package com.triplor.adventure.trip;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import com.triplor.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The business rules for an adventure's fields, as plain functions with no framework or
 * database, so they can be unit-tested directly. The database repeats the hard limits as check
 * constraints; these functions exist to give the caller a clear message first.
 */
public final class AdventureRules {

    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 20;
    public static final int MAX_DESCRIPTION_LENGTH = 300;
    public static final int MAX_STYLES = TravelStyle.values().length;

    /** The longest trip that can be posted, counting both the first and the last day. */
    public static final int MAX_TRIP_DAYS = 90;

    /** How far ahead a trip may start. */
    public static final int MAX_DAYS_AHEAD = 365;

    private AdventureRules() {
    }

    /**
     * Checks a trip's dates.
     *
     * @param todayUtc today's date in UTC
     * @param startDateIsNew whether the start date is being set now (a create, or an edit that
     *        changes it). An unchanged start date may be in the past, so the organizer of a
     *        trip that has begun can still correct its description.
     */
    public static void checkDates(LocalDate startDate, LocalDate endDate, LocalDate todayUtc, boolean startDateIsNew) {
        if (endDate.isBefore(startDate)) {
            throw invalid("endDate must not be before startDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_TRIP_DAYS) {
            throw invalid("A trip can be at most " + MAX_TRIP_DAYS + " days long");
        }
        if (startDateIsNew) {
            if (startDate.isBefore(earliestStartDate(todayUtc))) {
                throw invalid("startDate must not be in the past");
            }
            if (startDate.isAfter(todayUtc.plusDays(MAX_DAYS_AHEAD))) {
                throw invalid("startDate must be within " + MAX_DAYS_AHEAD + " days from today");
            }
        }
    }

    /**
     * The earliest start date accepted today. The server keeps time in UTC, but a traveller
     * west of UTC can still be on the previous calendar day, and "today" must be valid for them.
     */
    static LocalDate earliestStartDate(LocalDate todayUtc) {
        return todayUtc.minusDays(1);
    }

    public static void checkCapacity(int capacity) {
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw invalid("capacity must be between " + MIN_CAPACITY + " and " + MAX_CAPACITY);
        }
    }

    /** Returns the style names without duplicates and sorted, which is how they are stored and hashed. */
    public static String[] normalizeStyles(Collection<TravelStyle> styles) {
        List<String> names = styles.stream().map(Enum::name).distinct().sorted().toList();
        if (names.isEmpty()) {
            throw invalid("styles must contain at least one style");
        }
        return names.toArray(String[]::new);
    }

    /** Returns the description trimmed. */
    public static String normalizeDescription(String description) {
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            throw invalid("description must not be blank");
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid("description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }
}
