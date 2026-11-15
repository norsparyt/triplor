package com.triplor.identity.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * A user's public profile. Its primary key is the user's id, so there is exactly one per user.
 *
 * <p>Only the fields milestone M1 needs are mapped. The remaining columns (bio, date of birth,
 * styles and so on) are mapped in M2 with the profile endpoints.
 */
@Entity
@Table(name = "profiles")
public class Profile implements Persistable<UUID> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected Profile() {
        // for JPA
    }

    public Profile(UUID userId, String displayName, Instant now) {
        this.userId = userId;
        this.displayName = displayName;
        this.updatedAt = now;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
