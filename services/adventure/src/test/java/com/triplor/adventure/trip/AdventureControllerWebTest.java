package com.triplor.adventure.trip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.triplor.adventure.idempotency.IdempotencyService;
import com.triplor.adventure.place.PlaceResponse;
import com.triplor.adventure.trip.AdventureDtos.AdventureResponse;
import com.triplor.common.security.TriplorSecurityAutoConfiguration;
import com.triplor.common.web.TriplorWebAutoConfiguration;
import com.triplor.testsupport.TestJwtDecoderConfig;
import com.triplor.testsupport.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A web-slice test: the controller, validation, security and error handling, with the service
 * mocked. It covers what the HTTP layer decides by itself, before any business logic runs.
 */
@WebMvcTest(AdventureController.class)
@ImportAutoConfiguration({TriplorWebAutoConfiguration.class, TriplorSecurityAutoConfiguration.class})
@Import(TestJwtDecoderConfig.class)
class AdventureControllerWebTest {

    static final String VALID_BODY = """
            {"placeId": 1269515, "startDate": "2030-03-01", "endDate": "2030-03-05",
             "styles": ["CULTURE"], "description": "Forts and food", "capacity": 3}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdventureService adventures;

    final UUID userId = UUID.randomUUID();

    @Test
    void createWithoutAnIdempotencyKeyIsRefused() throws Exception {
        mvc.perform(createRequest(TestTokens.user(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(createRequest(TestTokens.user(userId)).header("Idempotency-Key", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verifyNoInteractions(adventures);
    }

    @Test
    void anUnverifiedCallerCannotCreate() throws Exception {
        mvc.perform(createRequest(TestTokens.unverifiedUser(userId)).header("Idempotency-Key", "key-12345678"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_VERIFIED"));

        verifyNoInteractions(adventures);
    }

    @Test
    void createNeedsAToken() throws Exception {
        mvc.perform(post("/adventures").header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anInvalidBodyListsTheFieldsAndNeverReachesTheService() throws Exception {
        mvc.perform(post("/adventures")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.user(userId))
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"styles\": [], \"description\": \"\", \"capacity\": 50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'placeId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'startDate')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'styles')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'capacity')]").exists());

        verifyNoInteractions(adventures);
    }

    @Test
    void createAnswers201WithLocationAndETag() throws Exception {
        AdventureResponse created = response(0);
        when(adventures.create(eq(userId), eq("key-12345678"), any()))
                .thenReturn(new IdempotencyService.Result<>(created, false));

        mvc.perform(createRequest(TestTokens.user(userId)).header("Idempotency-Key", "key-12345678"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/adventures/" + created.id()))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.id").value(created.id().toString()));
    }

    @Test
    void aReplayedCreateIsMarkedAsSuch() throws Exception {
        when(adventures.create(any(), any(), any())).thenReturn(new IdempotencyService.Result<>(response(0), true));

        mvc.perform(createRequest(TestTokens.user(userId)).header("Idempotency-Key", "key-12345678"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void editWithoutIfMatchIsRefused() throws Exception {
        mvc.perform(patch("/adventures/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.user(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\": 4}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        verifyNoInteractions(adventures);
    }

    @Test
    void editWithSomethingThatIsNotAnETagCannotMatch() throws Exception {
        mvc.perform(patch("/adventures/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.user(userId))
                        .header(HttpHeaders.IF_MATCH, "latest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\": 4}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        verifyNoInteractions(adventures);
    }

    @Test
    void editPassesTheVersionFromIfMatchAndAnswersWithTheNewETag() throws Exception {
        UUID id = UUID.randomUUID();
        when(adventures.edit(eq(id), eq(userId), eq(4L), any())).thenReturn(response(5));

        mvc.perform(patch("/adventures/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.user(userId))
                        .header(HttpHeaders.IF_MATCH, "\"4\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\": 4}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"5\""));

        verify(adventures).edit(eq(id), eq(userId), eq(4L), any());
    }

    private static MockHttpServletRequestBuilder createRequest(String token) {
        return post("/adventures")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY);
    }

    private static AdventureResponse response(long version) {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        return new AdventureResponse(
                UUID.randomUUID(), UUID.randomUUID(),
                new PlaceResponse(1269515, "Jaipur", "Rajasthan", "IN", 26.91962, 75.78781),
                LocalDate.parse("2030-03-01"), LocalDate.parse("2030-03-05"),
                List.of("CULTURE"), "Forts and food", 3, 0, "OPEN", version, now, now, null, null);
    }
}
