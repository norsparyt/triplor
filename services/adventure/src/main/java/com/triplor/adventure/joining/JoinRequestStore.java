package com.triplor.adventure.joining;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.triplor.adventure.trip.AdventureStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * All SQL for join requests, seats and members.
 *
 * <p>The write statements carry their preconditions in the WHERE clause, so checking and
 * writing are one atomic step. Each returns the number of rows changed; 0 means the
 * precondition did not hold. Nothing here uses the ORM: these rows change under concurrency,
 * and each change must be exactly the statement written below.
 */
@Repository
public class JoinRequestStore {

    /** How long a request waits for the organizer before it counts as expired. */
    static final String TIME_TO_DECIDE = "72 hours";

    /** What accept and create need to know about the adventure. */
    public record AdventureState(UUID id, UUID organizerId, AdventureStatus status, int capacity, int seatsTaken,
            LocalDate startDate) {
    }

    public record Member(UUID userId, String role, Instant joinedAt) {
    }

    private static final String REQUEST_COLUMNS = """
            r.id, r.adventure_id, r.requester_id, r.message,
            CASE WHEN r.status = 'PENDING' AND r.expires_at <= now() THEN 'EXPIRED' ELSE r.status END AS status,
            r.expires_at, r.decided_at, r.created_at
            """;

    private static final String ADVENTURE_STATE =
            "SELECT id, organizer_id, status, capacity, seats_taken, start_date FROM adventures WHERE id = :id";

    private final JdbcClient jdbc;

    public JoinRequestStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- adventures and seats ----

    public Optional<AdventureState> findAdventure(UUID adventureId) {
        return jdbc.sql(ADVENTURE_STATE).param("id", adventureId).query(JoinRequestStore::toAdventureState).optional();
    }

    /**
     * Reads the adventure and holds a shared lock on its row until the transaction ends. Any
     * number of travellers can hold it at once, but a transaction that wants to change the row
     * (accept taking a seat, cancel) has to wait for them, and they for it. A request can
     * therefore not be inserted "at the same time" as a cancel: one of the two goes first.
     */
    public Optional<AdventureState> findAdventureForShare(UUID adventureId) {
        return jdbc.sql(ADVENTURE_STATE + " FOR SHARE")
                .param("id", adventureId)
                .query(JoinRequestStore::toAdventureState)
                .optional();
    }

    /**
     * Takes one seat if one is left. This is the statement the whole design rests on.
     *
     * <p>The check ({@code seats_taken < capacity}) and the increment are a single UPDATE. When
     * two transactions run it for the same adventure, the second waits for the first to commit
     * and then evaluates the WHERE clause again on the new row, so it sees the seat the first
     * one took. The last seat can only be taken once. No application lock is involved.
     *
     * <p>It also locks the adventure row for the rest of the transaction, which is why accept
     * runs it first: every transaction that touches both tables locks the adventure, then
     * request rows, and so they cannot deadlock.
     */
    public int takeSeat(UUID adventureId) {
        return jdbc.sql("""
                        UPDATE adventures
                           SET seats_taken = seats_taken + 1,
                               status      = CASE WHEN seats_taken + 1 >= capacity THEN 'FULL' ELSE status END,
                               updated_at  = now()
                         WHERE id = :id AND status = 'OPEN' AND seats_taken < capacity
                        """)
                .param("id", adventureId)
                .update();
    }

    // ---- join requests: writes ----

    /**
     * Marks a pending request that has run out of time as expired, so that the "one active
     * request" index no longer counts it and the traveller can ask again. The scheduled job
     * that expires requests in bulk arrives in Phase 2; until then this is done when needed.
     */
    public int expireStale(UUID adventureId, UUID requesterId) {
        return jdbc.sql("""
                        UPDATE join_requests
                           SET status = 'EXPIRED', decided_at = now()
                         WHERE adventure_id = :adventureId AND requester_id = :requesterId
                           AND status = 'PENDING' AND expires_at <= now()
                        """)
                .param("adventureId", adventureId)
                .param("requesterId", requesterId)
                .update();
    }

    /**
     * Inserts a pending request. Returns 0 if the traveller already has a pending or accepted
     * request for this adventure: the partial unique index is the arbiter, so two simultaneous
     * attempts cannot both get in. {@code ON CONFLICT DO NOTHING} reports that as "no row"
     * instead of an error that would abort the transaction.
     */
    public int insert(UUID id, UUID adventureId, UUID requesterId, String message) {
        return jdbc.sql("""
                        INSERT INTO join_requests (id, adventure_id, requester_id, message, expires_at)
                        VALUES (:id, :adventureId, :requesterId, :message, now() + CAST(:timeToDecide AS interval))
                        ON CONFLICT (adventure_id, requester_id) WHERE status IN ('PENDING', 'ACCEPTED') DO NOTHING
                        """)
                .param("id", id)
                .param("adventureId", adventureId)
                .param("requesterId", requesterId)
                .param("message", message)
                .param("timeToDecide", TIME_TO_DECIDE)
                .update();
    }

    public int markAccepted(UUID requestId) {
        return decide(requestId, JoinRequestStatus.ACCEPTED);
    }

    public int markRejected(UUID requestId) {
        return decide(requestId, JoinRequestStatus.REJECTED);
    }

    public int markWithdrawn(UUID requestId) {
        return decide(requestId, JoinRequestStatus.WITHDRAWN);
    }

    /** Moves a request out of PENDING, only if it is still pending and has not run out of time. */
    private int decide(UUID requestId, JoinRequestStatus newStatus) {
        return jdbc.sql("""
                        UPDATE join_requests
                           SET status = :status, decided_at = now()
                         WHERE id = :id AND status = 'PENDING' AND expires_at > now()
                        """)
                .param("status", newStatus.name())
                .param("id", requestId)
                .update();
    }

    // ---- join requests: reads ----

    public Optional<JoinRequest> find(UUID requestId) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + " FROM join_requests r WHERE r.id = :id")
                .param("id", requestId)
                .query(JoinRequestStore::toJoinRequest)
                .optional();
    }

    /** Requests on one adventure, oldest first: the order the organizer should look at them in. */
    public List<JoinRequest> findByAdventure(UUID adventureId, JoinRequestStatus status, int limit) {
        return list("r.adventure_id = :scope", adventureId, status, "created_at, id", limit);
    }

    /** Requests on every adventure the user organizes, newest first. */
    public List<JoinRequest> findIncoming(UUID organizerId, JoinRequestStatus status, int limit) {
        return list("r.adventure_id IN (SELECT a.id FROM adventures a WHERE a.organizer_id = :scope)",
                organizerId, status, "created_at DESC, id", limit);
    }

    /** Requests the user has made, newest first. */
    public List<JoinRequest> findOutgoing(UUID requesterId, JoinRequestStatus status, int limit) {
        return list("r.requester_id = :scope", requesterId, status, "created_at DESC, id", limit);
    }

    /** The status filter is applied to the effective status, hence the outer query. */
    private List<JoinRequest> list(String scopeCondition, UUID scope, JoinRequestStatus status, String order, int limit) {
        String sql = "SELECT * FROM (SELECT " + REQUEST_COLUMNS + " FROM join_requests r WHERE " + scopeCondition + ") x"
                + " WHERE (CAST(:status AS text) IS NULL OR x.status = :status)"
                + " ORDER BY " + order + " LIMIT :limit";
        return jdbc.sql(sql)
                .param("scope", scope)
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .query(JoinRequestStore::toJoinRequest)
                .list();
    }

    // ---- members ----

    /** Adds a member. Safe to repeat. */
    public void addMember(UUID adventureId, UUID userId) {
        jdbc.sql("""
                        INSERT INTO adventure_members (adventure_id, user_id, role) VALUES (:adventureId, :userId, 'MEMBER')
                        ON CONFLICT DO NOTHING
                        """)
                .param("adventureId", adventureId)
                .param("userId", userId)
                .update();
    }

    /** The organizer first, then members in the order they joined. */
    public List<Member> findMembers(UUID adventureId) {
        return jdbc.sql("""
                        SELECT user_id, role, joined_at FROM adventure_members
                         WHERE adventure_id = :adventureId
                         ORDER BY (role = 'ORGANIZER') DESC, joined_at, user_id
                        """)
                .param("adventureId", adventureId)
                .query((rs, rowNum) -> new Member(
                        rs.getObject("user_id", UUID.class), rs.getString("role"), instant(rs, "joined_at")))
                .list();
    }

    private static AdventureState toAdventureState(ResultSet rs, int rowNum) throws SQLException {
        return new AdventureState(
                rs.getObject("id", UUID.class),
                rs.getObject("organizer_id", UUID.class),
                AdventureStatus.valueOf(rs.getString("status")),
                rs.getInt("capacity"),
                rs.getInt("seats_taken"),
                rs.getObject("start_date", LocalDate.class));
    }

    private static JoinRequest toJoinRequest(ResultSet rs, int rowNum) throws SQLException {
        return new JoinRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("adventure_id", UUID.class),
                rs.getObject("requester_id", UUID.class),
                rs.getString("message"),
                JoinRequestStatus.valueOf(rs.getString("status")),
                instant(rs, "expires_at"),
                instant(rs, "decided_at"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
