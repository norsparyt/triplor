package com.triplor.identity.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.triplor.common.error.ApiException;
import com.triplor.common.security.TriplorSecurityAutoConfiguration;
import com.triplor.common.web.TriplorWebAutoConfiguration;
import com.triplor.identity.auth.AuthDtos.AuthResponse;
import com.triplor.identity.auth.AuthDtos.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A web-slice test: only the controller, validation, security and error handling are started.
 * The service is a mock, so no database is needed and the test runs in milliseconds.
 */
@WebMvcTest(AuthController.class)
@ImportAutoConfiguration({TriplorWebAutoConfiguration.class, TriplorSecurityAutoConfiguration.class})
class AuthControllerWebTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void registerReturns201WithTheServiceResult() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.register(any())).thenReturn(new AuthResponse(
                new UserSummary(id, "asha@example.com", "Asha", false), "access", "refresh", 900, "Bearer"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "asha@example.com", "password": "correct horse battery", "displayName": "Asha"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void invalidBodyNeverReachesTheService() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "not-an-email", "password": "short", "displayName": ""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'displayName')]").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void missingRefreshTokenIsAValidationError() throws Exception {
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void serviceErrorsBecomeProblemJson() throws Exception {
        when(authService.login(any())).thenThrow(
                new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "asha@example.com", "password": "whatever"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
    }

    @Test
    void logoutReturns204() throws Exception {
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("""
                        {"refreshToken": "some-token"}
                        """))
                .andExpect(status().isNoContent());

        verify(authService).logout("some-token");
    }
}
