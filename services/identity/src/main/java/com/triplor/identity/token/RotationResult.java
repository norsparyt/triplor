package com.triplor.identity.token;

import java.util.UUID;

/**
 * Outcome of presenting a refresh token.
 *
 * <p>Rotation returns a value instead of throwing on purpose. When reuse is detected the whole
 * token family is revoked, and that revocation must be committed. An exception thrown from the
 * transactional method would roll it back.
 */
public sealed interface RotationResult {

    /** The token was valid. It is now used up and {@code newToken} replaces it. */
    record Rotated(UUID userId, String newToken) implements RotationResult {
    }

    record Rejected(Reason reason) implements RotationResult {
    }

    enum Reason {
        UNKNOWN,
        EXPIRED,
        /** The token had already been used or revoked. Its whole family has been revoked. */
        REUSED
    }
}
