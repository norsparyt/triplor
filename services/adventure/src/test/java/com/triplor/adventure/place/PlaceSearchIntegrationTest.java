package com.triplor.adventure.place;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import com.triplor.testsupport.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PlaceSearchIntegrationTest extends AdventureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void findsPlacesByPrefixBiggestFirst() throws Exception {
        search("jai")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Jaipur", "Jaisalmer")))
                .andExpect(jsonPath("$[0].id").value(JAIPUR))
                .andExpect(jsonPath("$[0].admin1").value("Rajasthan"))
                .andExpect(jsonPath("$[0].countryCode").value("IN"))
                .andExpect(jsonPath("$[0].lat").value(26.91962));
    }

    @Test
    void namesakesAreToldApartByRegionAndCountry() throws Exception {
        search("paris")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].countryCode").value(contains("FR", "US")));
    }

    @Test
    void theQueryIsCaseAndAccentInsensitive() throws Exception {
        search("ZÜR").andExpect(jsonPath("$[*].name").value(contains("Zürich")));
        search("  Manā ").andExpect(jsonPath("$[*].name").value(contains("Manāli")));
    }

    @Test
    void matchesTheStartOfTheNameOnly() throws Exception {
        search("pur").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void theLimitIsApplied() throws Exception {
        mvc.perform(get("/places").param("q", "pa").param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(UUID.randomUUID())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].countryCode").value("FR"));
    }

    @Test
    void wildcardsTypedByTheUserAreMatchedLiterally() throws Exception {
        // Unescaped, '%%' would match every place and 'j_i' would match Jaipur.
        search("%%").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        search("j_i").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void theQueryNeedsAtLeastTwoCharacters() throws Exception {
        search("j").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/places").header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchNeedsATokenButNotAVerifiedUser() throws Exception {
        mvc.perform(get("/places").param("q", "jai")).andExpect(status().isUnauthorized());
        mvc.perform(get("/places").param("q", "jai")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.unverifiedUser(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    private ResultActions search(String query) throws Exception {
        return mvc.perform(get("/places").param("q", query)
                .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(UUID.randomUUID())));
    }
}
