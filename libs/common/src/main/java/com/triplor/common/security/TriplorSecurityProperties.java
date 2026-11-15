package com.triplor.common.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-service security settings.
 *
 * @param publicPaths path patterns that need no token, for example {@code /auth/**}. Everything
 *                    else requires a valid access token.
 */
@ConfigurationProperties("triplor.security")
public record TriplorSecurityProperties(List<String> publicPaths) {

    public TriplorSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
