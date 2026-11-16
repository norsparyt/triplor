package com.triplor.adventure.trip;

import java.time.LocalDate;
import java.util.UUID;

import com.triplor.adventure.place.Place;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Every change to an existing adventure, each as a single SQL statement.
 *
 * <p>The statements carry their own preconditions in the WHERE clause, so the check and the
 * write are one atomic step: no other transaction can slip in between them. Each method returns
 * the number of rows changed; 0 means a precondition did not hold and the caller works out which.
 *
 * <p>These statements bypass Hibernate. An {@link Adventure} loaded earlier in the same
 * transaction is stale afterwards and must be reloaded.
 */
@Repository
public class AdventureWriter {

    /** The fields of an edit. A null field keeps the stored value. */
    public record Changes(Place place, LocalDate startDate, LocalDate endDate, String[] styles,
            String description, Integer capacity) {
    }

    private final JdbcClient jdbc;

    public AdventureWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Applies an edit if the row still has the version the caller saw. This is optimistic
     * locking written out by hand: no lock is held while the user is editing, and a concurrent
     * change is detected at write time by the version no longer matching.
     *
     * <p>The casts tell PostgreSQL the type of a parameter that is null.
     */
    public int edit(UUID id, long expectedVersion, Changes changes) {
        Place place = changes.place();
        return jdbc.sql("""
                        UPDATE adventures
                           SET place_id    = COALESCE(CAST(:placeId AS bigint), place_id),
                               lat         = COALESCE(CAST(:lat AS double precision), lat),
                               lng         = COALESCE(CAST(:lng AS double precision), lng),
                               start_date  = COALESCE(CAST(:startDate AS date), start_date),
                               end_date    = COALESCE(CAST(:endDate AS date), end_date),
                               styles      = COALESCE(CAST(:styles AS text[]), styles),
                               description = COALESCE(CAST(:description AS text), description),
                               capacity    = COALESCE(CAST(:capacity AS int), capacity),
                               status      = CASE WHEN seats_taken >= COALESCE(CAST(:capacity AS int), capacity)
                                                  THEN 'FULL' ELSE 'OPEN' END,
                               version     = version + 1,
                               updated_at  = now()
                         WHERE id = :id
                           AND version = :expectedVersion
                           AND status IN ('OPEN', 'FULL')
                           AND seats_taken <= COALESCE(CAST(:capacity AS int), capacity)
                        """)
                .param("placeId", place == null ? null : place.getId())
                .param("lat", place == null ? null : place.getLat())
                .param("lng", place == null ? null : place.getLng())
                .param("startDate", changes.startDate())
                .param("endDate", changes.endDate())
                .param("styles", changes.styles())
                .param("description", changes.description())
                .param("capacity", changes.capacity())
                .param("id", id)
                .param("expectedVersion", expectedVersion)
                .update();
    }

    /** Cancels an adventure that is still open or full. Returns 0 if it was already cancelled or is completed. */
    public int cancel(UUID id) {
        return jdbc.sql("""
                        UPDATE adventures
                           SET status = 'CANCELLED', version = version + 1, updated_at = now()
                         WHERE id = :id
                           AND status IN ('OPEN', 'FULL')
                        """)
                .param("id", id)
                .update();
    }
}
