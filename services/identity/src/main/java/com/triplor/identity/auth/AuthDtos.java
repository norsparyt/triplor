package com.triplor.identity.auth;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the {@code /auth} endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // BCrypt only reads the first 72 bytes of a password, so longer ones are refused, not silently truncated.
            @NotBlank @Size(min = 10, max = 72, message = "must be between 10 and 72 characters") String password,
            @NotBlank @Size(min = 2, max = 50) String displayName) {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record UserSummary(UUID id, String email, String displayName, boolean verified) {
    }

    /**
     * @param expiresIn lifetime of the access token in seconds
     */
    public record AuthResponse(
            UserSummary user,
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType) {
    }
}
