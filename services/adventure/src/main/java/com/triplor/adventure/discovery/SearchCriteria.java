package com.triplor.adventure.discovery;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.triplor.adventure.trip.TravelStyle;
import com.triplor.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A search request after defaults and validation: every field is present and within range.
 * Built by {@link #of}, a plain function, so the rules can be unit-tested without HTTP.
 */
public record SearchCriteria(double lat, double lng, double radiusKm, LocalDate from, LocalDate to,
        List<String> styles, Cursor cursor, int limit) {

    public static final double DEFAULT_RADIUS_KM = 50;
    public static final double MAX_RADIUS_KM = 500;
    public static final int DEFAULT_WINDOW_DAYS = 90;
    public static final int MAX_WINDOW_DAYS = 366;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    /**
     * @param todayUtc today's date in UTC, the default for {@code from}
     * @param styles style names as the client sent them; null or empty means "any style"
     */
    public static SearchCriteria of(Double lat, Double lng, Double radiusKm, LocalDate from, LocalDate to,
            Collection<String> styles, String cursor, Integer limit, LocalDate todayUtc) {
        if (lat == null || lng == null) {
            throw invalid("lat and lng are required");
        }
        if (!(lat >= -90 && lat <= 90)) {
            throw invalid("lat must be between -90 and 90");
        }
        if (!(lng >= -180 && lng <= 180)) {
            throw invalid("lng must be between -180 and 180");
        }
        double radius = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        if (!(radius > 0 && radius <= MAX_RADIUS_KM)) {
            throw invalid("radiusKm must be greater than 0 and at most " + (int) MAX_RADIUS_KM);
        }
        LocalDate fromDate = from == null ? todayUtc : from;
        LocalDate toDate = to == null ? fromDate.plusDays(DEFAULT_WINDOW_DAYS) : to;
        if (toDate.isBefore(fromDate)) {
            throw invalid("to must not be before from");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_WINDOW_DAYS) {
            throw invalid("from and to can be at most " + MAX_WINDOW_DAYS + " days apart");
        }
        int pageSize = limit == null ? DEFAULT_LIMIT : limit;
        if (pageSize < 1) {
            throw invalid("limit must be at least 1");
        }
        return new SearchCriteria(lat, lng, radius, fromDate, toDate, parseStyles(styles),
                cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor.trim()),
                Math.min(pageSize, MAX_LIMIT));
    }

    public double radiusMeters() {
        return radiusKm * 1000;
    }

    public boolean hasStyles() {
        return !styles.isEmpty();
    }

    private static List<String> parseStyles(Collection<String> styles) {
        if (styles == null) {
            return List.of();
        }
        return styles.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> {
                    try {
                        return TravelStyle.valueOf(name.toUpperCase(Locale.ROOT)).name();
                    } catch (IllegalArgumentException e) {
                        throw invalid("Unknown style: " + name);
                    }
                })
                .distinct()
                .sorted()
                .toList();
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }
}
