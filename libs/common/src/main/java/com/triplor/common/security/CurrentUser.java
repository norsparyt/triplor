package com.triplor.common.security;

import java.util.Set;
import java.util.UUID;

import com.triplor.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The caller, as described by the claims of a verified access token. Declare it as a controller
 * method parameter and {@link CurrentUserArgumentResolver} fills it in.
 *
 * <p>{@code verified} comes from the token, so it can be up to one token lifetime out of date.
 */
public record CurrentUser(UUID id, Set<String> roles, boolean verified) {

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    /** Rejects the call with 403 {@code NOT_VERIFIED} unless the caller's identity has been verified. */
    public void requireVerified() {
        if (!verified) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_VERIFIED",
                    "Your account must be verified before you can do this");
        }
    }
}
