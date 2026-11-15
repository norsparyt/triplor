package com.triplor.testsupport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Mints access tokens for tests, signed with a throwaway key created once per test JVM.
 * Together with {@link TestJwtDecoderConfig} it lets a service's tests authenticate callers
 * without the identity service running.
 *
 * <p>The claims match what identity issues: {@code sub}, {@code roles}, {@code verified},
 * issuer and audience.
 */
public final class TestTokens {

    public static final String ISSUER = "https://identity.triplor.test";
    public static final String AUDIENCE = "triplor-api";

    private static final RSAKey KEY = generateKey();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));

    private TestTokens() {
    }

    /** A token for an ordinary, verified user. */
    public static String user(UUID userId) {
        return accessToken(userId, true, "USER");
    }

    /** A token for a user who has not been verified yet. */
    public static String unverifiedUser(UUID userId) {
        return accessToken(userId, false, "USER");
    }

    /** A token for an admin. */
    public static String admin(UUID userId) {
        return accessToken(userId, true, "USER", "ADMIN");
    }

    public static String accessToken(UUID userId, boolean verified, String... roles) {
        return accessToken(userId, verified, Instant.now(), Duration.ofMinutes(15), roles);
    }

    /** Full control over the validity window, for tests about expired tokens. */
    public static String accessToken(UUID userId, boolean verified, Instant issuedAt, Duration ttl, String... roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("roles", new ArrayList<>(List.of(roles)))
                .claim("verified", verified)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** A decoder that accepts exactly the tokens minted here, and checks issuer and audience as production does. */
    public static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    new JwtAudienceValidator(AUDIENCE)));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not build the test JWT decoder", e);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate the test signing key", e);
        }
    }
}
