package com.triplor.identity.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import com.triplor.identity.config.JwtProperties;
import com.triplor.identity.token.RotationResult.Reason;
import com.triplor.identity.token.RotationResult.Rejected;
import com.triplor.identity.token.RotationResult.Rotated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens: long-lived, opaque, single-use credentials that are exchanged for a new access
 * token.
 *
 * <ul>
 *   <li><b>Opaque and hashed.</b> The token is 256 random bits. Only its SHA-256 hash is stored,
 *       so a leaked database does not leak usable tokens. A fast hash is fine here because the
 *       input is already high-entropy, unlike a password.</li>
 *   <li><b>Rotated.</b> Each use replaces the token with a new one in the same family.</li>
 *   <li><b>Reuse detection.</b> A token that is presented twice means two parties hold it, and one
 *       of them is not the user. We cannot tell which, so the whole family is revoked and the
 *       user has to log in again.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new token family, at login or registration. Returns the token to hand to the client. */
    @Transactional
    public String issueForNewLogin(UUID userId) {
        return create(userId, UUID.randomUUID(), UUID.randomUUID(), clock.instant());
    }

    /**
     * Exchanges a refresh token for a new one. The row is locked for the duration, so two
     * requests with the same token cannot both succeed: the second waits, then sees a revoked
     * token and is treated as reuse.
     */
    @Transactional
    public RotationResult rotate(String presentedToken) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = repository.findByTokenHashForUpdate(hash(presentedToken));
        if (found.isEmpty()) {
            return new Rejected(Reason.UNKNOWN);
        }
        RefreshToken current = found.get();
        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                    current.getUserId(), revoked, current.getFamilyId());
            return new Rejected(Reason.REUSED);
        }
        if (current.isExpiredAt(now)) {
            return new Rejected(Reason.EXPIRED);
        }
        UUID successorId = UUID.randomUUID();
        current.rotateTo(successorId, now);
        String newToken = create(current.getUserId(), current.getFamilyId(), successorId, now);
        return new Rotated(current.getUserId(), newToken);
    }

    /** Logout. Revoking an unknown or already revoked token is not an error. */
    @Transactional
    public void revoke(String presentedToken) {
        repository.findByTokenHashForUpdate(hash(presentedToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private String create(UUID userId, UUID familyId, UUID tokenId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(
                tokenId, userId, familyId, hash(token), now, now.plus(properties.refreshTokenTtl())));
        return token;
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
