package com.triplor.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.triplor.identity.IdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AuthFlowIntegrationTest extends IdentityIntegrationTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registerReturnsTokensAndTheNewUser() throws Exception {
        String email = uniqueEmail();

        String body = register(email, "Asha")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("Asha"))
                .andExpect(jsonPath("$.user.verified").value(false))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(JsonPath.<String>read(body, "$.user.id"));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsBoolean("verified")).isFalse();
        assertThat(jwt.getAudience()).containsExactly("triplor-api");
    }

    @Test
    void loginSucceedsAfterRegistration() throws Exception {
        String email = uniqueEmail();
        register(email, "Asha").andExpect(status().isCreated());

        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void emailIsNotCaseSensitive() throws Exception {
        String email = uniqueEmail();
        register(email.toUpperCase(), "Asha")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email));

        login("  " + email.toUpperCase() + " ", PASSWORD).andExpect(status().isOk());
        register(email, "Someone Else")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        String email = uniqueEmail();
        register(email, "Asha").andExpect(status().isCreated());

        login(email, "not the password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void invalidRegistrationListsEveryBadField() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void passwordsAndRefreshTokensAreStoredOnlyAsHashes() throws Exception {
        String email = uniqueEmail();
        String body = register(email, "Asha").andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(body, "$.refreshToken");
        String userId = JsonPath.read(body, "$.user.id");

        String passwordHash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(passwordHash).startsWith("$2").doesNotContain(PASSWORD);

        List<byte[]> hashes = jdbc.queryForList(
                "select token_hash from refresh_tokens where user_id = ?", byte[].class, UUID.fromString(userId));
        assertThat(hashes).hasSize(1);
        assertThat(hashes.get(0)).hasSize(32).isNotEqualTo(refreshToken.getBytes());
    }

    @Test
    void refreshTokenWorksOnceAndIsReplaced() throws Exception {
        String first = refreshTokenOf(register(uniqueEmail(), "Asha"));

        String refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(refreshed, "$.refreshToken");

        assertThat(second).isNotEqualTo(first);
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        String first = refreshTokenOf(register(uniqueEmail(), "Asha"));
        String second = refreshTokenOf(refresh(first).andExpect(status().isOk()));

        // Someone presents the old token again: either the user or a thief holds a stale copy.
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The newer token, which was valid a moment ago, is now dead too.
        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void aSeparateLoginIsNotAffectedByAnotherFamilyBeingRevoked() throws Exception {
        String email = uniqueEmail();
        String phone = refreshTokenOf(register(email, "Asha"));
        String laptop = refreshTokenOf(login(email, PASSWORD));

        String phoneNext = refreshTokenOf(refresh(phone));
        refresh(phone).andExpect(status().isUnauthorized());
        refresh(phoneNext).andExpect(status().isUnauthorized());

        refresh(laptop).andExpect(status().isOk());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        refresh("this-token-was-never-issued")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesTheRefreshTokenAndCanBeRepeated() throws Exception {
        String token = refreshTokenOf(register(uniqueEmail(), "Asha"));

        logout(token).andExpect(status().isNoContent());
        logout(token).andExpect(status().isNoContent());
        logout("never-issued").andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedUserCanNeitherLogInNorRefresh() throws Exception {
        String email = uniqueEmail();
        String token = refreshTokenOf(register(email, "Asha"));
        jdbc.update("update users set status = 'SUSPENDED' where email = ?", email);

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        refresh(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void everythingOutsideThePublicPathsNeedsAToken() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        String body = register(uniqueEmail(), "Asha").andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private ResultActions register(String email, String displayName) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "displayName": "%s"}
                """.formatted(email, PASSWORD, displayName)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                {"refreshToken": "%s"}
                """.formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("""
                {"refreshToken": "%s"}
                """.formatted(refreshToken)));
    }

    private static String refreshTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
