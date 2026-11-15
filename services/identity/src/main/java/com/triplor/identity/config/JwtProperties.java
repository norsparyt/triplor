package com.triplor.identity.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the tokens identity issues.
 *
 * @param issuer          the {@code iss} claim; other services reject tokens with a different issuer
 * @param audience        the {@code aud} claim; other services reject tokens not meant for them
 * @param privateKeyPath  PEM file holding the RSA private key (PKCS#8), created by {@code make keys}.
 *                        A plain string, because a relative path such as {@code ../../.local/key.pem}
 *                        cannot be bound to {@code java.nio.file.Path} inside a web application
 * @param accessTokenTtl  lifetime of an access token; short, because it cannot be revoked
 * @param refreshTokenTtl lifetime of a refresh token
 */
@Validated
@ConfigurationProperties("triplor.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String privateKeyPath,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
