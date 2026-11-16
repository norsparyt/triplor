package com.triplor.adventure.trip;

import static com.triplor.adventure.AdventureApi.body;
import static com.triplor.adventure.AdventureApi.inDays;
import static com.triplor.adventure.AdventureApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import com.triplor.testsupport.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class AdventureCrudIntegrationTest extends AdventureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    AdventureApi api;
    UUID asha;
    UUID ravi;

    @BeforeEach
    void setUp() {
        api = new AdventureApi(mvc);
        asha = UUID.randomUUID();
        ravi = UUID.randomUUID();
    }

    @Test
    void createReturnsTheAdventureWithItsLocationAndETag() throws Exception {
        String body = body(JAIPUR, inDays(30), inDays(34), "[\"FOODIE\", \"CULTURE\", \"FOODIE\"]", "  Forts and food  ", 3);

        api.create(asha, newKey(), body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.organizerId").value(asha.toString()))
                .andExpect(jsonPath("$.place.id").value(JAIPUR))
                .andExpect(jsonPath("$.place.name").value("Jaipur"))
                .andExpect(jsonPath("$.place.countryCode").value("IN"))
                .andExpect(jsonPath("$.startDate").value(inDays(30).toString()))
                .andExpect(jsonPath("$.endDate").value(inDays(34).toString()))
                // Stored without duplicates, sorted, and with the text trimmed.
                .andExpect(jsonPath("$.styles").value(contains("CULTURE", "FOODIE")))
                .andExpect(jsonPath("$.description").value("Forts and food"))
                .andExpect(jsonPath("$.capacity").value(3))
                // Filled in by the database, not by the application.
                .andExpect(jsonPath("$.seatsTaken").value(0))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void anyoneWithATokenCanReadAnAdventure() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food"));

        api.get(ravi, id)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.place.name").value("Jaipur"));
        mvc.perform(MockMvcRequestBuilders.get("/adventures/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.unverifiedUser(UUID.randomUUID())))
                .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/adventures/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownAdventureIsNotFound() throws Exception {
        String unknown = UUID.randomUUID().toString();

        api.get(asha, unknown).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        api.edit(asha, unknown, "\"0\"", "{}").andExpect(status().isNotFound());
        api.cancel(asha, unknown).andExpect(status().isNotFound());
    }

    @Test
    void createRejectsBrokenBusinessRules() throws Exception {
        expectInvalid(body(JAIPUR, inDays(10), inDays(9), "[\"HIKING\"]", "Ends before it starts", 2));
        expectInvalid(body(JAIPUR, inDays(-5), inDays(2), "[\"HIKING\"]", "Starts in the past", 2));
        expectInvalid(body(JAIPUR, inDays(10), inDays(120), "[\"HIKING\"]", "Far too long", 2));
        expectInvalid(body(JAIPUR, inDays(500), inDays(503), "[\"HIKING\"]", "Too far ahead", 2));
        expectInvalid(body(JAIPUR, inDays(10), inDays(12), "[]", "No styles", 2));
        expectInvalid(body(JAIPUR, inDays(10), inDays(12), "[\"HIKING\"]", "No seats", 0));
        expectInvalid(body(JAIPUR, inDays(10), inDays(12), "[\"HIKING\"]", "Too many seats", 21));
        expectInvalid(body(JAIPUR, inDays(10), inDays(12), "[\"HIKING\"]", "   ", 2));
        expectInvalid(body(999, inDays(10), inDays(12), "[\"HIKING\"]", "No such place", 2));
        // An unknown style cannot even be read as a request.
        api.create(asha, newKey(), body(JAIPUR, inDays(10), inDays(12), "[\"SURFING\"]", "Unknown style", 2))
                .andExpect(status().isBadRequest());

        assertThat(countOrganizedBy(asha)).isZero();
    }

    @Test
    void aOneDayTripStartingTodayIsAllowed() throws Exception {
        api.create(asha, newKey(), body(JAIPUR, inDays(0), inDays(0), "[\"FOODIE\"]", "Day trip", 1))
                .andExpect(status().isCreated());
    }

    @Test
    void editChangesOnlyTheFieldsSentAndBumpsTheVersion() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food"));

        api.edit(asha, id, "\"0\"", "{\"description\": \"Forts, food and a hot-air balloon\", \"capacity\": 5}")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.description").value("Forts, food and a hot-air balloon"))
                .andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.version").value(1))
                // Untouched fields keep their values.
                .andExpect(jsonPath("$.place.name").value("Jaipur"))
                .andExpect(jsonPath("$.startDate").value(inDays(30).toString()))
                .andExpect(jsonPath("$.styles").value(contains("CULTURE", "FOODIE")));

        api.get(ravi, id)
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.capacity").value(5));
    }

    @Test
    void editCanMoveTheTripToAnotherPlaceAndOtherDates() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Somewhere nice"));

        api.edit(asha, id, "\"0\"", """
                        {"placeId": %d, "startDate": "%s", "endDate": "%s", "styles": ["HIKING"]}
                        """.formatted(MANALI, inDays(60), inDays(66)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.id").value(MANALI))
                .andExpect(jsonPath("$.place.name").value("Manāli"))
                .andExpect(jsonPath("$.startDate").value(inDays(60).toString()))
                .andExpect(jsonPath("$.endDate").value(inDays(66).toString()))
                .andExpect(jsonPath("$.styles").value(contains("HIKING")));

        // The copied coordinates, and with them the generated location, follow the new place.
        Double latitude = jdbc.queryForObject(
                "select ST_Y(location::geometry) from adventures where id = ?", Double.class, UUID.fromString(id));
        assertThat(latitude).isEqualTo(32.2574);
    }

    @Test
    void editValidatesTheDatesAsTheyWillBeAfterTheChange() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food")); // day 30 to day 34

        // Only the end date is sent, and it falls before the stored start date.
        api.edit(asha, id, "\"0\"", "{\"endDate\": \"%s\"}".formatted(inDays(20)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.edit(asha, id, "\"0\"", "{\"placeId\": 999}").andExpect(status().isBadRequest());
        api.edit(asha, id, "\"0\"", "{\"capacity\": 0}").andExpect(status().isBadRequest());

        api.get(asha, id).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void aStaleIfMatchIsRefused() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food"));
        api.edit(asha, id, "\"0\"", "{\"capacity\": 5}").andExpect(status().isOk());

        // A second client still holds version 0.
        api.edit(asha, id, "\"0\"", "{\"capacity\": 2}")
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        api.edit(asha, id, "not-an-etag", "{\"capacity\": 2}")
                .andExpect(status().isPreconditionFailed());

        api.get(asha, id).andExpect(jsonPath("$.capacity").value(5)).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void ofTwoSimultaneousEditsFromTheSameVersionExactlyOneWins() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> edit = () -> {
                start.await();
                return api.edit(asha, id, "\"0\"", "{\"capacity\": 7}").andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(edit);
            Future<Integer> second = pool.submit(edit);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 412);
        } finally {
            pool.shutdownNow();
        }
        api.get(asha, id).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void onlyTheOrganizerCanEditOrCancel() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food"));

        api.edit(ravi, id, "\"0\"", "{\"capacity\": 5}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        api.cancel(ravi, id).andExpect(status().isForbidden());

        api.get(asha, id).andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void capacityCannotDropBelowTheSeatsTakenAndFullFollowsCapacity() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food")); // capacity 3
        // Join requests arrive in a later milestone; take two seats directly.
        jdbc.update("update adventures set seats_taken = 2 where id = ?", UUID.fromString(id));

        api.edit(asha, id, "\"0\"", "{\"capacity\": 1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_SEATS"));

        api.edit(asha, id, "\"0\"", "{\"capacity\": 2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatsTaken").value(2))
                .andExpect(jsonPath("$.status").value("FULL"));
        api.edit(asha, id, "\"1\"", "{\"capacity\": 4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void cancelIsFinalAndSafeToRepeat() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food"));

        api.cancel(asha, id).andExpect(status().isNoContent());
        api.cancel(asha, id).andExpect(status().isNoContent());

        api.get(asha, id)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                // Bumped once, by the first cancel only.
                .andExpect(jsonPath("$.version").value(1));
        api.edit(asha, id, "\"1\"", "{\"capacity\": 5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_NOT_OPEN"));
    }

    @Test
    void aCompletedAdventureCannotBeCancelled() throws Exception {
        String id = api.createdId(asha, body(JAIPUR, "Forts and food"));
        jdbc.update("update adventures set status = 'COMPLETED' where id = ?", UUID.fromString(id));

        api.cancel(asha, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_NOT_OPEN"));
    }

    @Test
    void mineListsTheCallersAdventuresLatestStartFirst() throws Exception {
        String early = api.createdId(asha, body(JAIPUR, inDays(10), inDays(12), "[\"FOODIE\"]", "Early", 2));
        String late = api.createdId(asha, body(MANALI, inDays(50), inDays(55), "[\"HIKING\"]", "Late", 2));
        api.createdId(ravi, body(PARIS_FRANCE, "Someone else's"));

        api.mine(asha)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(late, early)))
                .andExpect(jsonPath("$[0].place.name").value("Manāli"));
        api.mine(UUID.randomUUID()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(MockMvcRequestBuilders.get("/adventures/mine").param("role", "spectator")
                        .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(asha)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnverifiedUserCannotCreate() throws Exception {
        UUID unverified = UUID.randomUUID();

        mvc.perform(MockMvcRequestBuilders.post("/adventures")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.unverifiedUser(unverified))
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(JAIPUR, "Forts and food")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_VERIFIED"));
        assertThat(countOrganizedBy(unverified)).isZero();
    }

    private void expectInvalid(String body) throws Exception {
        api.create(asha, newKey(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private int countOrganizedBy(UUID organizerId) {
        return jdbc.queryForObject("select count(*) from adventures where organizer_id = ?", Integer.class, organizerId);
    }
}
