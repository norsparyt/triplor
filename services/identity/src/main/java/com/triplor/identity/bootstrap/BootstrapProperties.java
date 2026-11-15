package com.triplor.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional accounts created at start-up.
 *
 * @param adminEmail      email of the first admin; nothing is created unless this and the password are set
 * @param adminPassword   password of the first admin, taken from the environment, never from a committed file
 * @param seedUserCount   how many load-test users the {@code seed} profile creates
 * @param seedPassword    password shared by all load-test users
 */
@ConfigurationProperties("triplor.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword, Integer seedUserCount, String seedPassword) {

    public boolean hasAdmin() {
        return adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank();
    }

    public int seedUserCountOrDefault() {
        return seedUserCount == null ? 200 : seedUserCount;
    }
}
