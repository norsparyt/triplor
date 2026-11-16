package com.triplor.adventure.joining;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Size;

public final class JoinRequestDtos {

    static final int MAX_MESSAGE_LENGTH = 300;

    private JoinRequestDtos() {
    }

    /** The body is optional altogether; a request may be sent without a message. */
    public record CreateJoinRequest(@Size(max = MAX_MESSAGE_LENGTH) String message) {
    }

    public record JoinRequestResponse(UUID id, UUID adventureId, UUID requesterId, String message, String status,
            Instant expiresAt, Instant decidedAt, Instant createdAt) {

        static JoinRequestResponse of(JoinRequest request) {
            return new JoinRequestResponse(
                    request.id(), request.adventureId(), request.requesterId(), request.message(),
                    request.status().name(), request.expiresAt(), request.decidedAt(), request.createdAt());
        }
    }

    public record MemberResponse(UUID userId, String role, Instant joinedAt) {
    }
}
