package com.triplor.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.triplor.identity.IdentityIntegrationTest;
import com.triplor.identity.TestAccounts;
import com.triplor.identity.TestAccounts.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class VerificationIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder jwtDecoder;

    TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(mvc, jdbc);
    }

    @Test
    void aUserCanAskToBeVerified() throws Exception {
        Account asha = accounts.register("Asha");

        submit(asha)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(jsonPath("$.verification").value("PENDING"))
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void onlyOneRequestCanBePendingAtATime() throws Exception {
        Account asha = accounts.register("Asha");
        submit(asha).andExpect(status().isCreated());

        submit(asha)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_PENDING"));
    }

    @Test
    void adminRoutesAreForbiddenToOrdinaryUsers() throws Exception {
        Account asha = accounts.register("Asha");
        String requestId = idOf(submit(asha));

        mvc.perform(get("/admin/verifications").header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // A user cannot approve their own request.
        mvc.perform(post("/admin/verifications/" + requestId + "/approve").header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/verifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void theAdminQueueListsPendingRequestsWithTheRequester() throws Exception {
        Account admin = accounts.registerAdmin();
        Account asha = accounts.register("Asha");
        String requestId = idOf(submit(asha));

        mvc.perform(get("/admin/verifications").param("limit", "100").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + requestId + "')].email").value(hasItem(asha.email())))
                .andExpect(jsonPath("$[?(@.id == '" + requestId + "')].displayName").value(hasItem("Asha")))
                .andExpect(jsonPath("$[*].status").value(not(hasItem("APPROVED"))));
    }

    @Test
    void approvalMakesTheUserVerifiedAndTheNextTokenSaysSo() throws Exception {
        Account admin = accounts.registerAdmin();
        Account asha = accounts.register("Asha");
        String requestId = idOf(submit(asha));
        assertThat(jwtDecoder.decode(asha.accessToken()).getClaimAsBoolean("verified")).isFalse();

        mvc.perform(post("/admin/verifications/" + requestId + "/approve").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value(admin.id().toString()))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        // The profile is verified at once.
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.verification").value("APPROVED"));
        mvc.perform(get("/users/" + asha.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.verified").value(true));

        // The token issued before approval still says false: claims are a snapshot.
        assertThat(jwtDecoder.decode(asha.accessToken()).getClaimAsBoolean("verified")).isFalse();

        // A refreshed token carries the new state, without logging in again.
        String refreshed = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + asha.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.verified").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(jwtDecoder.decode(JsonPath.read(refreshed, "$.accessToken")).getClaimAsBoolean("verified")).isTrue();
    }

    @Test
    void aVerifiedUserCannotAskAgain() throws Exception {
        Account admin = accounts.registerAdmin();
        Account asha = accounts.register("Asha");
        approve(admin, idOf(submit(asha))).andExpect(status().isOk());

        submit(asha)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_VERIFIED"));
    }

    @Test
    void rejectionRecordsTheReasonAndAllowsANewRequest() throws Exception {
        Account admin = accounts.registerAdmin();
        Account asha = accounts.register("Asha");
        String requestId = idOf(submit(asha));

        reject(admin, requestId, "The photo is unreadable")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("The photo is unreadable"));

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verification").value("REJECTED"));
        submit(asha).andExpect(status().isCreated());
    }

    @Test
    void rejectionNeedsAReason() throws Exception {
        Account admin = accounts.registerAdmin();
        String requestId = idOf(submit(accounts.register("Asha")));

        reject(admin, requestId, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aRequestCanOnlyBeReviewedOnce() throws Exception {
        Account admin = accounts.registerAdmin();
        String requestId = idOf(submit(accounts.register("Asha")));
        approve(admin, requestId).andExpect(status().isOk());

        approve(admin, requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_ALREADY_REVIEWED"));
        reject(admin, requestId, "Changed my mind")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_ALREADY_REVIEWED"));
    }

    @Test
    void reviewingAnUnknownRequestIsNotFound() throws Exception {
        Account admin = accounts.registerAdmin();

        approve(admin, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions submit(Account account) throws Exception {
        return mvc.perform(post("/users/me/verification").header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private ResultActions approve(Account admin, String requestId) throws Exception {
        return mvc.perform(post("/admin/verifications/" + requestId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()));
    }

    private ResultActions reject(Account admin, String requestId, String reason) throws Exception {
        return mvc.perform(post("/admin/verifications/" + requestId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}"));
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
