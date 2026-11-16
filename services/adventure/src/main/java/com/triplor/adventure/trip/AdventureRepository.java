package com.triplor.adventure.trip;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Inserts and reads only. Changes to an existing row go through {@link AdventureWriter}.
 *
 * <p>The entity graph makes each finder load the place in the same query (a join), so mapping
 * a list of adventures does not run one extra query per row.
 */
public interface AdventureRepository extends JpaRepository<Adventure, UUID> {

    @EntityGraph(attributePaths = "place")
    Optional<Adventure> findWithPlaceById(UUID id);

    @EntityGraph(attributePaths = "place")
    List<Adventure> findWithPlaceByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "place")
    List<Adventure> findByOrganizerIdOrderByStartDateDescIdDesc(UUID organizerId, Limit limit);
}
