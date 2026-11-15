package com.triplor.identity.verification;

import java.time.Instant;
import java.util.UUID;

import com.triplor.identity.account.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A user's request to be verified. It is reviewed exactly once: approved or rejected. */
@Entity
@Table(name = "verification_requests")
public class VerificationRequest extends BaseEntity {

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "document_media_id")
    private UUID documentMediaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column
    private String reason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VerificationRequest() {
        // for JPA
    }

    public VerificationRequest(UUID id, UUID userId, UUID documentMediaId, Instant now) {
        super(id);
        this.userId = userId;
        this.documentMediaId = documentMediaId;
        this.status = Status.PENDING;
        this.createdAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDocumentMediaId() {
        return documentMediaId;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void approve(UUID adminId, Instant now) {
        review(Status.APPROVED, null, adminId, now);
    }

    public void reject(String reason, UUID adminId, Instant now) {
        review(Status.REJECTED, reason, adminId, now);
    }

    private void review(Status outcome, String reason, UUID adminId, Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("Verification request " + getId() + " has already been reviewed");
        }
        this.status = outcome;
        this.reason = reason;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
    }
}
