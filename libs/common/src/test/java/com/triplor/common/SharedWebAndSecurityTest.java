package com.triplor.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.triplor.testsupport.TestJwtDecoderConfig;
import com.triplor.testsupport.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the shared auto-configuration the way a service uses it: real filter chain, real
 * token verification, real error rendering.
 */
@SpringBootTest(properties = "triplor.security.public-paths=/open/**")
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class SharedWebAndSecurityTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    MockMvc mvc;

    @Test
    void publicPathNeedsNoToken() throws Exception {
        mvc.perform(get("/open/ping")).andExpect(status().isOk());
    }

    @Test
    void protectedPathWithoutTokenIs401AsProblemJson() throws Exception {
        mvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void validTokenIsResolvedIntoCurrentUser() throws Exception {
        UUID userId = UUID.randomUUID();
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.admin(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.roles", hasItem("ADMIN")));
    }

    @Test
    void unverifiedClaimIsCarriedThrough() throws Exception {
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.unverifiedUser(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        String expired = TestTokens.accessToken(
                UUID.randomUUID(), true, Instant.now().minus(Duration.ofHours(2)), Duration.ofMinutes(15), "USER");
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        String token = TestTokens.user(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutAdminRoleIs403AsProblemJson() throws Exception {
        mvc.perform(get("/admin/only").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.user(UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminRoleIsAllowed() throws Exception {
        mvc.perform(get("/admin/only").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.admin(UUID.randomUUID()))))
                .andExpect(status().isOk());
    }

    @Test
    void validationFailureListsTheFields() throws Exception {
        mvc.perform(post("/open/echo").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"far too long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/open/echo").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void apiExceptionKeepsItsStatusAndCode() throws Exception {
        mvc.perform(get("/open/teapot"))
                .andExpect(status().isIAmATeapot())
                .andExpect(jsonPath("$.code").value("TEAPOT"))
                .andExpect(jsonPath("$.detail").value("I am a teapot"));
    }

    @Test
    void unexpectedExceptionIs500AndDoesNotLeakDetails() throws Exception {
        mvc.perform(get("/open/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }

    @Test
    void aQueryParameterOfTheWrongTypeIsAValidationFailure() throws Exception {
        mvc.perform(get("/open/page").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("limit has an invalid value"));
    }

    @Test
    void aMissingQueryParameterIsAValidationFailure() throws Exception {
        mvc.perform(get("/open/page"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("limit is required"));
    }

    @Test
    void frameworkErrorsAlsoCarryACode() throws Exception {
        mvc.perform(post("/open/ping"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
