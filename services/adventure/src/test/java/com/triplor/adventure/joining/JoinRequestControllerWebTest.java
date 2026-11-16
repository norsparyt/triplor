package com.triplor.adventure.joining;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

/** What the HTTP layer decides before any join-request logic runs. The service is a mock. */
@WebMvcTest(JoinRequestController.class)
@ImportAutoConfiguration({TriplorWebAutoConfiguration.class, TriplorSecurityAutoConfiguration.class})
@Import(TestJwtDecoderConfig.class)
class JoinRequestControllerWebTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JoinRequestService joinRequests;

    final UUID userId = UUID.randomUUID();
    final String path = "/adventures/" + UUID.randomUUID() + "/join-requests";

    @Test
    void askingToJoinWithoutAnIdempotencyKeyIsRefused() throws Exception {
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.user(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verifyNoInteractions(joinRequests);
    }

    @Test
    void anUnverifiedCallerCannotAskToJoin() throws Exception {
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.unverifiedUser(userId))
                        .header("Idempotency-Key", "key-12345678"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_VERIFIED"));

        verifyNoInteractions(joinRequests);
    }

    @Test
    void aMessageLongerThanThreeHundredCharactersIsRefused() throws Exception {
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.user(userId))
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"" + "x".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(joinRequests);
    }

    @Test
    void theRequestsListNeedsAKnownDirectionAndStatus() throws Exception {
        String bearer = "Bearer " + TestTokens.user(userId);

        mvc.perform(get("/join-requests").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/join-requests").param("direction", "sideways").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/join-requests").param("direction", "incoming").param("status", "MAYBE")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(joinRequests);
    }

    @Test
    void everyEndpointNeedsAToken() throws Exception {
        mvc.perform(post(path).header("Idempotency-Key", "key-12345678")).andExpect(status().isUnauthorized());
        mvc.perform(post("/join-requests/" + UUID.randomUUID() + "/accept")).andExpect(status().isUnauthorized());
        mvc.perform(get("/join-requests").param("direction", "incoming")).andExpect(status().isUnauthorized());
    }
}
