package com.triplor.adventure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.triplor.testsupport.TestTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Calls the adventure endpoints the way a client would, to keep the tests readable. */
public final class AdventureApi {

    private final MockMvc mvc;

    public AdventureApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    public static String bearer(UUID userId) {
        return "Bearer " + TestTokens.user(userId);
    }

    public static LocalDate inDays(int days) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(days);
    }

    /** A valid create body: five days in Jaipur a month from now, three seats. */
    public static String body(long placeId, String description) {
        return body(placeId, inDays(30), inDays(34), "[\"CULTURE\", \"FOODIE\"]", description, 3);
    }

    public static String body(long placeId, LocalDate start, LocalDate end, String stylesJson, String description,
            int capacity) {
        return """
                {"placeId": %d, "startDate": "%s", "endDate": "%s", "styles": %s, "description": "%s", "capacity": %d}
                """.formatted(placeId, start, end, stylesJson, description, capacity);
    }

    public ResultActions create(UUID userId, String idempotencyKey, String body) throws Exception {
        return mvc.perform(post("/adventures")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Creates an adventure with a fresh key and returns its id. */
    public String createdId(UUID userId, String body) throws Exception {
        return createdWith(userId, newKey(), body);
    }

    /** Creates an adventure with the given key and returns its id. */
    public String createdWith(UUID userId, String idempotencyKey, String body) throws Exception {
        String response = create(userId, idempotencyKey, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    public ResultActions get(UUID userId, String adventureId) throws Exception {
        return mvc.perform(MockMvcRequestBuilders
                .get("/adventures/" + adventureId).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
    }

    public ResultActions edit(UUID userId, String adventureId, String ifMatch, String body) throws Exception {
        return mvc.perform(patch("/adventures/" + adventureId)
                .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    public ResultActions cancel(UUID userId, String adventureId) throws Exception {
        return mvc.perform(delete("/adventures/" + adventureId).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
    }

    public ResultActions mine(UUID userId) throws Exception {
        return mvc.perform(MockMvcRequestBuilders
                .get("/adventures/mine").header(HttpHeaders.AUTHORIZATION, bearer(userId)));
    }

    public static String newKey() {
        return "key-" + UUID.randomUUID();
    }
}
