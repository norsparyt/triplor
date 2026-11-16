package com.triplor.adventure.trip;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.triplor.adventure.place.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * A trip that an organizer has posted.
 *
 * <p>This entity is used to insert a row and to read it, never to update it: it is
 * {@code @Immutable}, so Hibernate will not write changes back. Every later change (edit,
 * cancel, taking a seat) is one explicit SQL statement in {@link AdventureWriter}. The row
 * carries a seat counter, and an ordinary entity save would write the whole row from memory,
 * which could put back a stale count.
 *
 * <p>{@code seatsTaken}, {@code status}, {@code version} and the timestamps are never set from
 * Java: they are left out of the INSERT ({@code insertable = false}), so the column defaults
 * apply. A newly saved object therefore does not hold them yet; the service reads the row back
 * after inserting it. The {@code location} and {@code trip_dates} columns are computed by the
 * database and are not mapped at all; only native queries use them.
 */
@Entity
@Immutable
@Table(name = "adventures")
public class Adventure implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Travel style names, stored as a PostgreSQL {@code text[]}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] styles;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "seats_taken", nullable = false, insertable = false, updatable = false)
    private int seatsTaken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, insertable = false, updatable = false)
    private AdventureStatus status;

    @Column(nullable = false, insertable = false, updatable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected Adventure() {
        // for JPA
    }

    public Adventure(UUID id, UUID organizerId, Place place, LocalDate startDate, LocalDate endDate,
            String[] styles, String description, int capacity) {
        this.id = id;
        this.organizerId = organizerId;
        this.place = place;
        this.lat = place.getLat();
        this.lng = place.getLng();
        this.startDate = startDate;
        this.endDate = endDate;
        this.styles = styles.clone();
        this.description = description;
        this.capacity = capacity;
    }

    @Override
    public UUID getId() {
        return id;
    }

    /** New until saved or loaded, so that {@code save()} inserts without a SELECT first. */
    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public Place getPlace() {
        return place;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String[] getStyles() {
        return styles.clone();
    }

    public String getDescription() {
        return description;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    public AdventureStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isOrganizedBy(UUID userId) {
        return organizerId.equals(userId);
    }
}
