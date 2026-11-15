package com.triplor.identity.verification;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class VerificationDtos {

    private VerificationDtos() {
    }

    /** Body of a verification request. The document is optional until media upload exists (Phase 3). */
    public record SubmitVerificationRequest(UUID documentMediaId) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** What the requesting user sees. */
    public record VerificationResponse(UUID id, String status, String reason, Instant createdAt, Instant reviewedAt) {
    }

    /** What an admin sees in the review queue: the request plus who is asking. */
    public record VerificationReviewItem(
            UUID id,
            UUID userId,
            String email,
            String displayName,
            UUID documentMediaId,
            String status,
            String reason,
            Instant createdAt,
            Instant reviewedAt,
            UUID reviewedBy) {
    }
}
