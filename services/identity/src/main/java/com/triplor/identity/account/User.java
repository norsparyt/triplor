package com.triplor.identity.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A login account: credentials, role and status. Public profile data lives in {@link Profile}. */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    public enum Role {
        USER, ADMIN;

        /** Role names as they appear in a token. An admin is also a user. */
        public List<String> tokenRoles() {
            return this == ADMIN ? List.of("USER", "ADMIN") : List.of("USER");
        }
    }

    public enum Status {
        ACTIVE, SUSPENDED
    }

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // for JPA
    }

    public User(UUID id, String email, String passwordHash, Role role, Instant now) {
        super(id);
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isSuspended() {
        return status == Status.SUSPENDED;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
