package com.triplor.identity.bootstrap;

import com.triplor.identity.account.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin account at start-up, if one is configured.
 *
 * <p>Verification needs an admin to approve it, and an admin cannot be created through the public
 * API, so the first one has to come from configuration. Nothing happens unless both
 * {@code TRIPLOR_ADMIN_EMAIL} and {@code TRIPLOR_ADMIN_PASSWORD} are set. An existing account
 * with that email is left untouched: this never changes a password or promotes a user.
 */
@Component
@EnableConfigurationProperties(BootstrapProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapProperties properties;
    private final AccountProvisioner provisioner;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapProperties properties, AccountProvisioner provisioner, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.provisioner = provisioner;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.hasAdmin()) {
            return;
        }
        boolean created = provisioner.createIfAbsent(
                properties.adminEmail(),
                passwordEncoder.encode(properties.adminPassword()),
                User.Role.ADMIN,
                "Admin",
                true);
        if (created) {
            log.info("Created bootstrap admin account {}", properties.adminEmail());
        } else {
            log.info("Bootstrap admin {} already exists; left unchanged", properties.adminEmail());
        }
    }
}
