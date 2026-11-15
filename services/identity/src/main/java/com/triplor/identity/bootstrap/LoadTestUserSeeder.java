package com.triplor.identity.bootstrap;

import com.triplor.identity.account.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a batch of verified users for load tests: {@code loadtest-1@triplor.test} and so on,
 * all with the same password.
 *
 * <p>Active only under the {@code seed} Spring profile, which must never be enabled outside a
 * local or throwaway environment: these accounts have a known password.
 */
@Component
@Profile("seed")
public class LoadTestUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadTestUserSeeder.class);

    private final BootstrapProperties properties;
    private final AccountProvisioner provisioner;
    private final PasswordEncoder passwordEncoder;

    public LoadTestUserSeeder(BootstrapProperties properties, AccountProvisioner provisioner, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.provisioner = provisioner;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.seedPassword() == null || properties.seedPassword().isBlank()) {
            throw new IllegalStateException("The seed profile needs triplor.bootstrap.seed-password to be set");
        }
        // BCrypt is slow by design. Hash once and reuse the result, instead of paying for it per user.
        String passwordHash = passwordEncoder.encode(properties.seedPassword());
        int created = 0;
        for (int i = 1; i <= properties.seedUserCountOrDefault(); i++) {
            if (provisioner.createIfAbsent(
                    emailOf(i), passwordHash, User.Role.USER, "Load Tester " + i, true)) {
                created++;
            }
        }
        log.warn("Seed profile is active: {} load-test users exist ({} created now). Never use this profile in production.",
                properties.seedUserCountOrDefault(), created);
    }

    public static String emailOf(int index) {
        return "loadtest-" + index + "@triplor.test";
    }
}
