package com.triplor.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;
import com.triplor.identity.account.User;
import com.triplor.identity.config.JwtProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** What exactly goes into an access token, checked by decoding it again. No Spring context needed. */
class AccessTokenServiceTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final JwtProperties PROPERTIES = new JwtProperties(
            "https://identity.triplor.test", "triplor-api", "unused", ACCESS_TTL, Duration.ofDays(30));

    private static RSAKey key;
    private static NimbusJwtDecoder decoder;

    // JWT timestamps have one-second precision.
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeAll
    static void generateKey() throws Exception {
        key = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }

    @Test
    void tokenCarriesTheClaimsOtherServicesRelyOn() {
        User user = user(User.Role.USER);

        Jwt jwt = decoder.decode(serviceAt(now).issue(user, true));

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://identity.triplor.test");
        assertThat(jwt.getAudience()).containsExactly("triplor-api");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsBoolean("verified")).isTrue();
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void tokenExpiresAfterTheConfiguredLifetime() {
        Jwt jwt = decoder.decode(serviceAt(now).issue(user(User.Role.USER), false));

        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(ACCESS_TTL));
        assertThat(serviceAt(now).ttlSeconds()).isEqualTo(900);
    }

    @Test
    void aTokenIssuedLongAgoIsRejectedAsExpired() {
        String stale = serviceAt(now.minus(Duration.ofHours(1))).issue(user(User.Role.USER), true);

        assertThatThrownBy(() -> decoder.decode(stale)).isInstanceOf(JwtException.class);
    }

    @Test
    void anAdminIsAlsoAUser() {
        Jwt jwt = decoder.decode(serviceAt(now).issue(user(User.Role.ADMIN), true));

        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER", "ADMIN");
    }

    @Test
    void unverifiedUsersAreMarkedAsSuch() {
        Jwt jwt = decoder.decode(serviceAt(now).issue(user(User.Role.USER), false));

        assertThat(jwt.getClaimAsBoolean("verified")).isFalse();
    }

    @Test
    void headerNamesTheAlgorithmAndTheSigningKey() throws Exception {
        SignedJWT parsed = SignedJWT.parse(serviceAt(now).issue(user(User.Role.USER), true));

        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(key.getKeyID());
    }

    @Test
    void everyTokenHasItsOwnId() {
        AccessTokenService service = serviceAt(now);
        User user = user(User.Role.USER);

        assertThat(decoder.decode(service.issue(user, true)).getId())
                .isNotEqualTo(decoder.decode(service.issue(user, true)).getId());
    }

    private static AccessTokenService serviceAt(Instant instant) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return new AccessTokenService(encoder, PROPERTIES, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static User user(User.Role role) {
        return new User(UUID.randomUUID(), "asha@example.com", "hash", role, Instant.now());
    }
}
