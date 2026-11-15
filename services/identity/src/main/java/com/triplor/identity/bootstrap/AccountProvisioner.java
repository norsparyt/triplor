package com.triplor.identity.bootstrap;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.triplor.identity.account.Profile;
import com.triplor.identity.account.ProfileRepository;
import com.triplor.identity.account.User;
import com.triplor.identity.account.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts outside the normal registration flow: the first admin, and load-test users.
 * Both must be safe to run on every start, so creation is skipped when the email already exists.
 */
@Component
public class AccountProvisioner {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final Clock clock;

    public AccountProvisioner(UserRepository users, ProfileRepository profiles, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * @param passwordHash an already encoded password; the caller decides how many times to pay for BCrypt
     * @return true if the account was created, false if the email was already taken
     */
    @Transactional
    public boolean createIfAbsent(String email, String passwordHash, User.Role role, String displayName, boolean verified) {
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalised)) {
            return false;
        }
        Instant now = clock.instant();
        User user = users.save(new User(UUID.randomUUID(), normalised, passwordHash, role, now));
        Profile profile = new Profile(user.getId(), displayName, now);
        if (verified) {
            profile.markVerified(now);
        }
        profiles.save(profile);
        return true;
    }
}
