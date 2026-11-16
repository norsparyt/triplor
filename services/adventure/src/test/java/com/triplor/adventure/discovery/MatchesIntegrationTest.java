package com.triplor.adventure.discovery;

import static com.triplor.adventure.AdventureApi.body;
import static com.triplor.adventure.AdventureApi.inDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Each test uses a city that no other test touches, so the lists can be asserted exactly. */
class MatchesIntegrationTest extends AdventureIntegrationTest {

    @Autowired
    MockMvc mvc;

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
    void matchesAreOtherPeoplesOverlappingTripsBestFitFirst() throws Exception {
        // Mine: ten days in New York, culture and food.
        String mine = api.createdId(asha, body(NEW_YORK, inDays(30), inDays(39), "[\"CULTURE\", \"FOODIE\"]", "Mine", 2));
        String twin = api.createdId(ravi, body(NEW_YORK, inDays(30), inDays(39), "[\"FOODIE\", \"CULTURE\"]", "Same trip", 2));
        String half = api.createdId(ravi, body(NEW_YORK, inDays(35), inDays(44), "[\"CULTURE\"]", "Half the days", 2));
        String lastDay = api.createdId(ravi, body(NEW_YORK, inDays(39), inDays(45), "[\"HIKING\"]", "One shared day", 2));
        // Never candidates: no day in common, my own other trip, a cancelled trip, another city.
        api.createdId(ravi, body(NEW_YORK, inDays(50), inDays(55), "[\"CULTURE\"]", "Later", 2));
        api.createdId(asha, body(NEW_YORK, inDays(30), inDays(39), "[\"CULTURE\"]", "Also mine", 2));
        String cancelled = api.createdId(ravi, body(NEW_YORK, inDays(30), inDays(39), "[\"CULTURE\"]", "Cancelled", 2));
        api.cancel(ravi, cancelled).andExpect(status().isNoContent());
        api.createdId(ravi, body(TOKYO, inDays(30), inDays(39), "[\"CULTURE\", \"FOODIE\"]", "Far away", 2));

        matches(asha, mine)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].adventure.id").value(contains(twin, half, lastDay)))
                // Same dates, same styles, same spot.
                .andExpect(jsonPath("$[0].score.total").value(1.0))
                // 5 of my 10 days (0.25), 1 of 2 styles (0.15), same spot (0.2).
                .andExpect(jsonPath("$[1].score.dateOverlap").value(0.5))
                .andExpect(jsonPath("$[1].score.styleSimilarity").value(0.5))
                .andExpect(jsonPath("$[1].score.proximity").value(1.0))
                .andExpect(jsonPath("$[1].score.total").value(0.6))
                // 1 of my 10 days (0.05), no shared style, same spot (0.2).
                .andExpect(jsonPath("$[2].score.total").value(0.25))
                .andExpect(jsonPath("$[2].adventure.place.name").value("New York City"))
                .andExpect(jsonPath("$[2].adventure.description").value("One shared day"));

        mvc.perform(get("/adventures/" + mine + "/matches").param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(asha)))
                .andExpect(jsonPath("$[*].adventure.id").value(contains(twin)));
    }

    @Test
    void aNearbyCityMatchesWithinTheRadiusAndScoresLowerOnProximity() throws Exception {
        String mine = api.createdId(asha, body(MUMBAI, inDays(30), inDays(34), "[\"FOODIE\"]", "Mine", 2));
        String inPune = api.createdId(ravi, body(PUNE, inDays(30), inDays(34), "[\"FOODIE\"]", "Pune, about 120 km away", 2));

        // Outside the default 50 km.
        matches(asha, mine).andExpect(jsonPath("$", hasSize(0)));

        String response = mvc.perform(get("/adventures/" + mine + "/matches").param("radiusKm", "200")
                        .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].adventure.id").value(contains(inPune)))
                .andReturn().getResponse().getContentAsString();
        double distanceKm = JsonPath.read(response, "$[0].adventure.distanceKm");
        double proximity = JsonPath.read(response, "$[0].score.proximity");
        double total = JsonPath.read(response, "$[0].score.total");
        assertThat(distanceKm).isBetween(100.0, 140.0);
        assertThat(proximity).isBetween(0.3, 0.5);
        // Full marks on dates and styles, partial on distance.
        assertThat(total).isBetween(0.86, 0.9);
    }

    @Test
    void onlyTheOrganizerOfAnOpenAdventureCanAskForMatches() throws Exception {
        String mine = api.createdId(asha, body(LONDON_CANADA, "Mine"));

        matches(ravi, mine).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        matches(asha, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        mvc.perform(get("/adventures/" + mine + "/matches")).andExpect(status().isUnauthorized());
        mvc.perform(get("/adventures/" + mine + "/matches").param("radiusKm", "900")
                        .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(asha)))
                .andExpect(status().isBadRequest());

        api.cancel(asha, mine).andExpect(status().isNoContent());
        matches(asha, mine).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADVENTURE_NOT_OPEN"));
    }

    private ResultActions matches(UUID caller, String adventureId) throws Exception {
        return mvc.perform(get("/adventures/" + adventureId + "/matches")
                .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(caller)));
    }
}
