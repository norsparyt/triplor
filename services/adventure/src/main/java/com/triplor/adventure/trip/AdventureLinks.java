package com.triplor.adventure.trip;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * What an adventure's own endpoints need from the join-request and member tables: the count of
 * people waiting, the viewer's own request, the trips a user has joined, and the two writes
 * that belong to an adventure's life cycle (the organizer's member row, and rejecting pending
 * requests on cancel).
 */
@Repository
public class AdventureLinks {

    /** The viewer's live request on an adventure, if any. */
    public record ViewerRequest(UUID id, String status) {
    }

    private final JdbcClient jdbc;

    public AdventureLinks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Requests still waiting for the organizer. */
    public int pendingRequestCount(UUID adventureId) {
        return jdbc.sql("""
                        SELECT count(*) FROM join_requests
                         WHERE adventure_id = :adventureId AND status = 'PENDING' AND expires_at > now()
                        """)
                .param("adventureId", adventureId)
                .query(Integer.class)
                .single();
    }

    /** The viewer's most recent request on the adventure, with its effective status. */
    public Optional<ViewerRequest> latestRequestOf(UUID adventureId, UUID viewerId) {
        return jdbc.sql("""
                        SELECT id,
                               CASE WHEN status = 'PENDING' AND expires_at <= now() THEN 'EXPIRED' ELSE status END AS status
                          FROM join_requests
                         WHERE adventure_id = :adventureId AND requester_id = :viewerId
                         ORDER BY created_at DESC
                         LIMIT 1
                        """)
                .param("adventureId", adventureId)
                .param("viewerId", viewerId)
                .query((rs, rowNum) -> new ViewerRequest(rs.getObject("id", UUID.class), rs.getString("status")))
                .optional();
    }

    /** Ids of the adventures the user has been accepted on, latest start date first. */
    public List<UUID> joinedAdventureIds(UUID userId, int limit) {
        return jdbc.sql("""
                        SELECT a.id
                          FROM adventure_members m
                          JOIN adventures a ON a.id = m.adventure_id
                         WHERE m.user_id = :userId AND m.role = 'MEMBER'
                         ORDER BY a.start_date DESC, a.id DESC
                         LIMIT :limit
                        """)
                .param("userId", userId)
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    public void addOrganizer(UUID adventureId, UUID organizerId) {
        jdbc.sql("INSERT INTO adventure_members (adventure_id, user_id, role) VALUES (:adventureId, :userId, 'ORGANIZER')")
                .param("adventureId", adventureId)
                .param("userId", organizerId)
                .update();
    }

    /**
     * Rejects every request still pending on an adventure. Called by cancel after it has
     * updated, and thereby locked, the adventure row: adventure first, then requests.
     */
    public int rejectPendingRequests(UUID adventureId) {
        return jdbc.sql("""
                        UPDATE join_requests
                           SET status = 'REJECTED', decided_at = now()
                         WHERE adventure_id = :adventureId AND status = 'PENDING'
                        """)
                .param("adventureId", adventureId)
                .update();
    }
}
