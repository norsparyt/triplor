package com.triplor.identity.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.triplor.common.error.ApiException;
import com.triplor.identity.account.Profile;
import com.triplor.identity.account.ProfileRepository;
import com.triplor.identity.account.User;
import com.triplor.identity.account.UserRepository;
import com.triplor.identity.auth.AuthDtos.AuthResponse;
import com.triplor.identity.auth.AuthDtos.LoginRequest;
import com.triplor.identity.auth.AuthDtos.RegisterRequest;
import com.triplor.identity.auth.AuthDtos.UserSummary;
import com.triplor.identity.token.AccessTokenService;
import com.triplor.identity.token.RefreshTokenService;
import com.triplor.identity.token.RotationResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login, token refresh and logout. */
@Service
public class AuthService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    /** A real BCrypt hash that matches no password. See {@link #login}. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, ProfileRepository profiles, PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens, RefreshTokenService refreshTokens, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        Instant now = clock.instant();
        User user;
        try {
            // Flush now, so a duplicate is reported here and not at commit time. Two simultaneous
            // registrations can both pass the check above; the unique constraint on users.email
            // is what actually guarantees one account per email.
            user = users.saveAndFlush(new User(
                    UUID.randomUUID(), email, passwordEncoder.encode(request.password()), User.Role.USER, now));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        Profile profile = profiles.save(new Profile(user.getId(), request.displayName().trim(), now));
        return tokensFor(user, profile, refreshTokens.issueForNewLogin(user.getId()));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normaliseEmail(request.email())).orElse(null);

        // Always run one BCrypt comparison, even when the email is unknown. Otherwise an unknown
        // email would answer much faster than a wrong password, and the response time alone
        // would reveal which emails have accounts.
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        if (user.isSuspended()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account is suspended");
        }
        Profile profile = profiles.findById(user.getId()).orElseThrow();
        return tokensFor(user, profile, refreshTokens.issueForNewLogin(user.getId()));
    }

    /**
     * Not transactional itself: {@link RefreshTokenService#rotate} runs and commits its own
     * transaction first, so a revocation caused by reuse is kept even though this method then
     * throws.
     */
    public AuthResponse refresh(String refreshToken) {
        RotationResult result = refreshTokens.rotate(refreshToken);
        if (!(result instanceof RotationResult.Rotated rotated)) {
            // One error for every reason. Telling a caller "expired" versus "reused" would only help an attacker.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "The refresh token is not valid");
        }
        User user = users.findById(rotated.userId()).orElseThrow();
        if (user.isSuspended()) {
            refreshTokens.revoke(rotated.newToken());
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account is suspended");
        }
        Profile profile = profiles.findById(user.getId()).orElseThrow();
        return tokensFor(user, profile, rotated.newToken());
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
    }

    private AuthResponse tokensFor(User user, Profile profile, String refreshToken) {
        return new AuthResponse(
                new UserSummary(user.getId(), user.getEmail(), profile.getDisplayName(), profile.isVerified()),
                accessTokens.issue(user, profile.isVerified()),
                refreshToken,
                accessTokens.ttlSeconds(),
                "Bearer");
    }

    /** Emails are compared case-insensitively, so they are stored and looked up in lower case. */
    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
