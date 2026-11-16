package com.triplor.adventure.place;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * Type-ahead search. {@code pattern} is a lower-case LIKE pattern such as {@code 'jaip%'},
     * already escaped by the caller. The expression matches the trigram index on
     * {@code lower(ascii_name)}; the biggest cities come first.
     */
    @Query(value = """
            SELECT *
              FROM places
             WHERE lower(ascii_name) LIKE :pattern ESCAPE '\\'
             ORDER BY population DESC, id
             LIMIT :limit
            """, nativeQuery = true)
    List<Place> searchByNamePrefix(@Param("pattern") String pattern, @Param("limit") int limit);
}
