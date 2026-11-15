package com.triplor.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.triplor.identity.config.JwtProperties;
import com.triplor.identity.token.RotationResult.Reason;
import com.triplor.identity.token.RotationResult.Rejected;
import com.triplor.identity.token.RotationResult.Rotated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Every branch of refresh-token rotation, with the repository mocked and time frozen.
 * The same rules are exercised against a real database in AuthFlowIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    @Mock
    RefreshTokenRepository repository;

    RefreshTokenService service;

    final UUID userId = UUID.randomUUID();
    final UUID familyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "https://identity.triplor.test", "triplor-api", "unused", Duration.ofMinutes(15), REFRESH_TTL);
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void newLoginStoresOnlyTheHashOfTheToken() {
        String token = service.issueForNewLogin(userId);

        RefreshToken stored = savedToken();
        assertThat(token).hasSize(43); // 32 random bytes, base64url without padding
        assertThat(stored.getTokenHash()).isEqualTo(RefreshTokenService.hash(token));
        assertThat(stored.getTokenHash()).isNotEqualTo(token.getBytes());
        assertThat(stored.getUserId()).isEqualTo(userId);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void everyLoginGetsADifferentTokenAndFamily() {
        String first = service.issueForNewLogin(userId);
        UUID firstFamily = savedToken().getFamilyId();
        org.mockito.Mockito.clearInvocations(repository);
        String second = service.issueForNewLogin(userId);

        assertThat(second).isNotEqualTo(first);
        assertThat(savedToken().getFamilyId()).isNotEqualTo(firstFamily);
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThat(service.rotate("never-issued")).isEqualTo(new Rejected(Reason.UNKNOWN));
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejectedWithoutRevokingTheFamily() {
        RefreshToken expired = existingToken(NOW.minus(REFRESH_TTL).minusSeconds(1));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(expired));

        assertThat(service.rotate("expired")).isEqualTo(new Rejected(Reason.EXPIRED));
        verify(repository, never()).revokeFamily(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void tokenExpiringExactlyNowIsAlreadyExpired() {
        RefreshToken token = existingToken(NOW.minus(REFRESH_TTL));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));

        assertThat(service.rotate("boundary")).isEqualTo(new Rejected(Reason.EXPIRED));
    }

    @Test
    void reusedTokenRevokesItsWholeFamily() {
        RefreshToken alreadyUsed = existingToken(NOW.minus(Duration.ofDays(1)));
        alreadyUsed.rotateTo(UUID.randomUUID(), NOW.minus(Duration.ofHours(1)));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(alreadyUsed));

        assertThat(service.rotate("used-before")).isEqualTo(new Rejected(Reason.REUSED));
        verify(repository).revokeFamily(familyId, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void validTokenIsReplacedByANewOneInTheSameFamily() {
        RefreshToken current = existingToken(NOW.minus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(current));

        RotationResult result = service.rotate("valid");

        assertThat(result).isInstanceOf(Rotated.class);
        Rotated rotated = (Rotated) result;
        RefreshToken successor = savedToken();

        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(successor.getFamilyId()).isEqualTo(familyId);
        assertThat(successor.getUserId()).isEqualTo(userId);
        assertThat(successor.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotated.newToken()));
        assertThat(successor.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedBy()).isEqualTo(successor.getId());
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void logoutRevokesAKnownTokenAndIgnoresAnUnknownOne() {
        RefreshToken token = existingToken(NOW.minus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token), Optional.empty());

        service.revoke("known");
        service.revoke("unknown");

        assertThat(token.isRevoked()).isTrue();
    }

    private RefreshToken existingToken(Instant createdAt) {
        return new RefreshToken(UUID.randomUUID(), userId, familyId, new byte[32], createdAt, createdAt.plus(REFRESH_TTL));
    }

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
