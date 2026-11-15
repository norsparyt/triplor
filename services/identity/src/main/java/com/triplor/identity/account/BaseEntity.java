package com.triplor.identity.account;

import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base for entities whose UUID is assigned by the application, not the database.
 *
 * <p>Spring Data decides between INSERT and UPDATE by asking whether an entity is new. With an
 * id that is already set it would guess "not new" and run a SELECT first. Implementing
 * {@link Persistable} tells it the truth: new until it has been saved or loaded.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected BaseEntity() {
        // for JPA
    }

    protected BaseEntity(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
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
}
