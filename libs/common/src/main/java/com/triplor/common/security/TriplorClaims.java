package com.triplor.common.security;

/** Names of the custom claims in a Triplor access token. Identity writes them; every service reads them. */
public final class TriplorClaims {

    /** List of role names, for example {@code ["USER", "ADMIN"]}. */
    public static final String ROLES = "roles";

    /** Whether the user's identity has been verified by an admin. */
    public static final String VERIFIED = "verified";

    private TriplorClaims() {
    }
}
