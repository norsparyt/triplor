package com.triplor.adventure.trip;

import static com.triplor.adventure.AdventureApi.body;
import static com.triplor.adventure.AdventureApi.inDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What the database guarantees by itself, whatever the application does. These statements go
 * straight to PostgreSQL, the way a bug or a manual fix would.
 */
class AdventureSchemaIntegrationTest extends AdventureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID adventureId;

    @BeforeEach
    void createAnAdventure() throws Exception {
        String body = body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "Forts and food", 3);
        adventureId = UUID.fromString(new AdventureApi(mvc).createdId(UUID.randomUUID(), body));
    }

    @Test
    void theDatabaseRefusesToOversellATrip() {
        assertThatThrownBy(() -> jdbc.update("update adventures set seats_taken = 4 where id = ?", adventureId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("adventures_seats_chk");
        assertThatThrownBy(() -> jdbc.update("update adventures set seats_taken = -1 where id = ?", adventureId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesUnknownStylesAndBackwardDates() {
        assertThatThrownBy(() -> jdbc.update("update adventures set styles = '{SURFING}' where id = ?", adventureId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("adventures_styles_chk");
        // Refused twice over: the generated trip_dates range cannot be built from backward
        // dates, and PostgreSQL computes it before it gets to adventures_dates_chk.
        assertThatThrownBy(() -> jdbc.update("update adventures set end_date = start_date - 1 where id = ?", adventureId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void locationAndTripDatesAreComputedByTheDatabase() {
        Map<String, Object> row = jdbc.queryForMap("""
                select ST_X(location::geometry) as lng,
                       ST_Y(location::geometry) as lat,
                       ST_SRID(location::geometry) as srid,
                       trip_dates = daterange(start_date, end_date, '[]') as dates_match,
                       upper(trip_dates) - lower(trip_dates) as days
                  from adventures where id = ?
                """, adventureId);

        assertThat(row.get("lng")).isEqualTo(75.78781);
        assertThat(row.get("lat")).isEqualTo(26.91962);
        assertThat(row.get("srid")).isEqualTo(4326);
        assertThat(row.get("dates_match")).isEqualTo(true);
        // Both the first and the last day are part of the trip.
        assertThat(row.get("days")).isEqualTo(5);
    }

    @Test
    void anAdventureMustPointAtAKnownPlace() {
        assertThatThrownBy(() -> jdbc.update("update adventures set place_id = 999 where id = ?", adventureId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
