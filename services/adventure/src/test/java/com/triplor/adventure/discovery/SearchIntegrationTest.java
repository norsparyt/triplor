package com.triplor.adventure.discovery;

import static com.triplor.adventure.AdventureApi.body;
import static com.triplor.adventure.AdventureApi.inDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import com.triplor.testsupport.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Search against real PostGIS. Each test works in a city of its own and looks for its own
 * adventure ids, because the database is shared with every other test.
 */
class SearchIntegrationTest extends AdventureIntegrationTest {

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
    void aTripInsideTheRadiusIsFoundAndOneJustOutsideIsNot() throws Exception {
        String inBengaluru = api.createdId(asha, body(BENGALURU, "Coffee and gardens"));
        double distanceKm = distanceKm(CHENNAI, BENGALURU); // about 290 km

        String found = search(ravi, CHENNAI, "radiusKm", String.valueOf(distanceKm + 1));
        assertThat(ids(found)).contains(inBengaluru);
        double reported = JsonPath.<List<Double>>read(found, "$.items[?(@.id == '" + inBengaluru + "')].distanceKm").getFirst();
        assertThat(reported).isCloseTo(distanceKm, within(0.1));

        assertThat(ids(search(ravi, CHENNAI, "radiusKm", String.valueOf(distanceKm - 1)))).doesNotContain(inBengaluru);
    }

    @Test
    void aCardCarriesThePlaceAndTheSeats() throws Exception {
        String id = api.createdId(asha, body(LEH, inDays(40), inDays(47), "[\"HIKING\"]", "High passes", 4));

        mvc.perform(searchRequest(ravi, LEH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '%s')].place.name".formatted(id)).value("Leh"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].place.countryCode".formatted(id)).value("IN"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].organizerId".formatted(id)).value(asha.toString()))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].startDate".formatted(id)).value(inDays(40).toString()))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].capacity".formatted(id)).value(4))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].seatsTaken".formatted(id)).value(0))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].distanceKm".formatted(id)).value(0.0));
    }

    @Test
    void dateRangesThatShareASingleDayOverlap() throws Exception {
        String id = api.createdId(asha, body(TOKYO, inDays(30), inDays(34), "[\"FOODIE\"]", "Ramen crawl", 2));

        // The window starts on the trip's last day, or ends on its first.
        assertThat(ids(search(ravi, TOKYO, "from", inDays(34), "to", inDays(40)))).contains(id);
        assertThat(ids(search(ravi, TOKYO, "from", inDays(20), "to", inDays(30)))).contains(id);
        // One day further and they no longer touch.
        assertThat(ids(search(ravi, TOKYO, "from", inDays(35), "to", inDays(40)))).doesNotContain(id);
        assertThat(ids(search(ravi, TOKYO, "from", inDays(20), "to", inDays(29)))).doesNotContain(id);
        // A window inside the trip.
        assertThat(ids(search(ravi, TOKYO, "from", inDays(32), "to", inDays(32)))).contains(id);
    }

    @Test
    void theStyleFilterMatchesAnySharedStyle() throws Exception {
        String id = api.createdId(asha, body(BANGKOK, inDays(30), inDays(34), "[\"CULTURE\", \"FOODIE\"]", "Temples and street food", 2));

        assertThat(ids(search(ravi, BANGKOK))).contains(id);
        assertThat(ids(search(ravi, BANGKOK, "styles", "FOODIE,HIKING"))).contains(id);
        assertThat(ids(search(ravi, BANGKOK, "styles", "foodie"))).contains(id);
        assertThat(ids(search(ravi, BANGKOK, "styles", "HIKING,RELAXING"))).doesNotContain(id);
        // The repeated-parameter form works too.
        String repeated = mvc.perform(searchRequest(ravi, BANGKOK).param("styles", "HIKING").param("styles", "CULTURE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(repeated)).contains(id);
    }

    @Test
    void onlyOtherPeoplesOpenTripsThatHaveNotStartedAreShown() throws Exception {
        String open = api.createdId(asha, body(KATHMANDU, "Open"));
        String cancelled = api.createdId(asha, body(KATHMANDU, "Cancelled"));
        String full = api.createdId(asha, body(KATHMANDU, "Full"));
        String started = api.createdId(asha, body(KATHMANDU, "Started"));
        api.cancel(asha, cancelled).andExpect(status().isNoContent());
        jdbc.update("update adventures set seats_taken = capacity, status = 'FULL' where id = ?", UUID.fromString(full));
        // Began five days ago and still running, so it overlaps the search window.
        jdbc.update("update adventures set start_date = current_date - 5, end_date = current_date + 5 where id = ?",
                UUID.fromString(started));

        assertThat(ids(search(ravi, KATHMANDU)))
                .contains(open)
                .doesNotContain(cancelled, full, started);
        // The organizer does not see their own trip in discovery.
        assertThat(ids(search(asha, KATHMANDU))).doesNotContain(open);
    }

    @Test
    void theDefaultWindowIsTheNextNinetyDays() throws Exception {
        String soon = api.createdId(asha, body(ZURICH, inDays(80), inDays(85), "[\"RELAXING\"]", "Lake days", 2));
        String later = api.createdId(asha, body(ZURICH, inDays(200), inDays(205), "[\"RELAXING\"]", "Much later", 2));

        assertThat(ids(search(ravi, ZURICH))).contains(soon).doesNotContain(later);
        assertThat(ids(search(ravi, ZURICH, "from", inDays(190), "to", inDays(210)))).contains(later).doesNotContain(soon);
    }

    @Test
    void pagesHaveNoDuplicatesAndNoGapsWhileTripsAreBeingAdded() throws Exception {
        // Seven trips; three of them start on the same day, so the id decides their order.
        List<String> created = new ArrayList<>();
        for (int day : List.of(10, 11, 12, 12, 12, 13, 14)) {
            created.add(api.createdId(asha, body(LONDON_UK, inDays(day), inDays(day + 2), "[\"CULTURE\"]", "Day " + day, 2)));
        }

        String firstPage = search(ravi, LONDON_UK, "limit", "3");
        List<String> seen = new ArrayList<>(ids(firstPage));
        String cursor = JsonPath.read(firstPage, "$.nextCursor");
        assertThat(seen).hasSize(3);
        assertThat(cursor).isNotNull();

        // While the user reads page one: a trip that sorts before the cursor, and one after it.
        String before = api.createdId(asha, body(LONDON_UK, inDays(9), inDays(11), "[\"CULTURE\"]", "Added before", 2));
        String after = api.createdId(asha, body(LONDON_UK, inDays(14), inDays(16), "[\"CULTURE\"]", "Added after", 2));

        int pages = 1;
        while (cursor != null) {
            String page = search(ravi, LONDON_UK, "limit", "3", "cursor", cursor);
            seen.addAll(ids(page));
            cursor = JsonPath.read(page, "$.nextCursor");
            assertThat(++pages).isLessThan(10);
        }

        // Every original trip exactly once, plus the one added further on. With OFFSET, the
        // trip added before the cursor would have pushed a row onto the next page a second time.
        assertThat(seen).doesNotHaveDuplicates();
        assertThat(seen).containsAll(created).contains(after).doesNotContain(before);
        assertThat(seen).hasSize(8);
        assertThat(pages).isEqualTo(3);

        // And the whole list is in (start date, id) order.
        assertThat(seen).isEqualTo(jdbc.queryForList("""
                select id::text from adventures
                 where id::text = any (?) order by start_date, id
                """, String.class, (Object) seen.toArray(String[]::new)));
    }

    @Test
    void theLastPageHasNoCursor() throws Exception {
        api.createdId(asha, body(PARIS_TEXAS, "Only one here"));

        mvc.perform(searchRequest(ravi, PARIS_TEXAS))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void badParametersAreValidationFailures() throws Exception {
        expectInvalid(get("/adventures/search").param("lng", "75.8"));
        expectInvalid(get("/adventures/search").param("lat", "abc").param("lng", "75.8"));
        expectInvalid(get("/adventures/search").param("lat", "95").param("lng", "75.8"));
        expectInvalid(get("/adventures/search").param("lat", "26.9").param("lng", "75.8").param("radiusKm", "501"));
        expectInvalid(get("/adventures/search").param("lat", "26.9").param("lng", "75.8").param("from", "tomorrow"));
        expectInvalid(get("/adventures/search").param("lat", "26.9").param("lng", "75.8")
                .param("from", inDays(10).toString()).param("to", inDays(5).toString()));
        expectInvalid(get("/adventures/search").param("lat", "26.9").param("lng", "75.8").param("styles", "SURFING"));
        expectInvalid(get("/adventures/search").param("lat", "26.9").param("lng", "75.8").param("cursor", "garbage!"));
        expectInvalid(get("/adventures/search").param("lat", "26.9").param("lng", "75.8").param("limit", "0"));
    }

    @Test
    void searchNeedsATokenButNotAVerifiedUser() throws Exception {
        mvc.perform(get("/adventures/search").param("lat", "26.9").param("lng", "75.8"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/adventures/search").param("lat", "26.9").param("lng", "75.8")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.unverifiedUser(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    /**
     * Searches around a fixture place, with extra parameters given as name, value pairs.
     * The radius is 5 km unless a {@code radiusKm} pair says otherwise.
     */
    private String search(UUID viewer, long placeId, Object... params) throws Exception {
        String radiusKm = "5";
        MockHttpServletRequestBuilder request = searchRequest(viewer, placeId, null);
        for (int i = 0; i < params.length; i += 2) {
            if (params[i].equals("radiusKm")) {
                radiusKm = params[i + 1].toString();
            } else {
                request.param(params[i].toString(), params[i + 1].toString());
            }
        }
        request.param("radiusKm", radiusKm);
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** A search within 5 km of a fixture place. */
    private MockHttpServletRequestBuilder searchRequest(UUID viewer, long placeId) {
        return searchRequest(viewer, placeId, "5");
    }

    private MockHttpServletRequestBuilder searchRequest(UUID viewer, long placeId, String radiusKm) {
        Map<String, Object> place = jdbc.queryForMap("select lat, lng from places where id = ?", placeId);
        MockHttpServletRequestBuilder request = get("/adventures/search")
                .header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(viewer))
                .param("lat", place.get("lat").toString())
                .param("lng", place.get("lng").toString());
        return radiusKm == null ? request : request.param("radiusKm", radiusKm);
    }

    private void expectInvalid(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(ravi)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private static List<String> ids(String searchResponse) {
        return JsonPath.read(searchResponse, "$.items[*].id");
    }

    private double distanceKm(long fromPlace, long toPlace) {
        return jdbc.queryForObject("""
                select ST_Distance(ST_SetSRID(ST_MakePoint(a.lng, a.lat), 4326)::geography,
                                   ST_SetSRID(ST_MakePoint(b.lng, b.lat), 4326)::geography) / 1000
                  from places a, places b where a.id = ? and b.id = ?
                """, Double.class, fromPlace, toPlace);
    }
}
