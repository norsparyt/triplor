package com.triplor.adventure.joining;

import java.time.Instant;
import java.util.UUID;

/**
 * A row of {@code join_requests} as read from the database.
 *
 * <p>{@code status} is the effective status: a pending request whose time has run out is
 * reported as EXPIRED even before a job has updated the row.
 */
public record JoinRequest(UUID id, UUID adventureId, UUID requesterId, String message, JoinRequestStatus status,
        Instant expiresAt, Instant decidedAt, Instant createdAt) {
}
