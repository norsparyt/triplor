package com.triplor.identity.token;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.triplor.common.security.TriplorClaims;
import com.triplor.identity.account.User;
import com.triplor.identity.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens: short-lived, signed JWTs that carry everything another service needs to
 * authorise a request (who, which roles, verified or not).
 *
 * <p>An access token cannot be revoked once issued, which is why it lives for minutes, not days.
 */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(User user, boolean verified) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(TriplorClaims.ROLES, user.getRole().tokenRoles())
                .claim(TriplorClaims.VERIFIED, verified)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long ttlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
