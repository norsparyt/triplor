package com.triplor.identity.account;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * A user's public profile. Its primary key is the user's id, so there is exactly one per user.
 *
 * <p>This is an ordinary managed entity: load it, call a setter-style method, and Hibernate
 * writes the change at commit (dirty checking). No explicit save is needed for updates.
 */
@Entity
@Table(name = "profiles")
public class Profile implements Persistable<UUID> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column
    private String bio;

    @Column(name = "home_city")
    private String homeCity;

    /** Travel style names, stored as a PostgreSQL {@code text[]}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] styles = new String[0];

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

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getBio() {
        return bio;
    }

    public String getHomeCity() {
        return homeCity;
    }

    public String[] getStyles() {
        return styles.clone();
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public void changeDisplayName(String displayName, Instant now) {
        this.displayName = displayName;
        this.updatedAt = now;
    }

    public void changeDateOfBirth(LocalDate dateOfBirth, Instant now) {
        this.dateOfBirth = dateOfBirth;
        this.updatedAt = now;
    }

    public void changeBio(String bio, Instant now) {
        this.bio = bio;
        this.updatedAt = now;
    }

    public void changeHomeCity(String homeCity, Instant now) {
        this.homeCity = homeCity;
        this.updatedAt = now;
    }

    public void changeStyles(String[] styles, Instant now) {
        this.styles = styles.clone();
        this.updatedAt = now;
    }

    /** Marks the user as verified. Keeps the original time if called again. */
    public void markVerified(Instant now) {
        if (this.verifiedAt == null) {
            this.verifiedAt = now;
            this.updatedAt = now;
        }
    }
}
