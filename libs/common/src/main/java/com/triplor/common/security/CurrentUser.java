package com.triplor.common.security;

import java.util.Set;
import java.util.UUID;

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
}
