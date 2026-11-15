package com.triplor.identity.profile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.triplor.common.error.ApiException;
import com.triplor.identity.account.Profile;
import com.triplor.identity.account.ProfileRepository;
import com.triplor.identity.account.User;
import com.triplor.identity.account.UserRepository;
import com.triplor.identity.profile.ProfileDtos.MyProfile;
import com.triplor.identity.profile.ProfileDtos.PublicProfile;
import com.triplor.identity.profile.ProfileDtos.UpdateProfileRequest;
import com.triplor.identity.verification.VerificationRequest;
import com.triplor.identity.verification.VerificationRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    /** Travellers meet strangers in person, so the service is for adults only. */
    static final int MINIMUM_AGE = 18;

    /** Upper bound of one batch lookup. Keeps a single request from loading the whole user table. */
    static final int MAX_BATCH_SIZE = 100;

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final VerificationRequestRepository verificationRequests;
    private final Clock clock;

    public ProfileService(UserRepository users, ProfileRepository profiles,
            VerificationRequestRepository verificationRequests, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.verificationRequests = verificationRequests;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyProfile getOwn(UUID userId) {
        User user = users.findById(userId).orElseThrow(ProfileService::notFound);
        Profile profile = profiles.findById(userId).orElseThrow(ProfileService::notFound);
        return toMyProfile(user, profile);
    }

    /**
     * Applies a partial update. The profile is a managed entity, so changing it inside the
     * transaction is enough: Hibernate writes the UPDATE at commit.
     */
    @Transactional
    public MyProfile updateOwn(UUID userId, UpdateProfileRequest request) {
        User user = users.findById(userId).orElseThrow(ProfileService::notFound);
        Profile profile = profiles.findById(userId).orElseThrow(ProfileService::notFound);
        Instant now = clock.instant();

        if (request.displayName() != null) {
            String displayName = request.displayName().trim();
            if (displayName.length() < 2) {
                throw invalid("displayName must be at least 2 characters");
            }
            profile.changeDisplayName(displayName, now);
        }
        if (request.bio() != null) {
            profile.changeBio(blankToNull(request.bio()), now);
        }
        if (request.homeCity() != null) {
            profile.changeHomeCity(blankToNull(request.homeCity()), now);
        }
        if (request.dateOfBirth() != null) {
            if (ageOn(request.dateOfBirth(), today()) < MINIMUM_AGE) {
                throw invalid("You must be at least " + MINIMUM_AGE + " years old");
            }
            profile.changeDateOfBirth(request.dateOfBirth(), now);
        }
        if (request.styles() != null) {
            // Sorted, so the stored array does not depend on the order the client sent.
            String[] styles = request.styles().stream().map(Enum::name).sorted().toArray(String[]::new);
            profile.changeStyles(styles, now);
        }
        return toMyProfile(user, profile);
    }

    @Transactional(readOnly = true)
    public PublicProfile getPublic(UUID userId) {
        User user = users.findById(userId).filter(u -> !u.isSuspended()).orElseThrow(ProfileService::notFound);
        Profile profile = profiles.findById(userId).orElseThrow(ProfileService::notFound);
        return toPublicProfile(user, profile);
    }

    /**
     * Looks up many users in two queries, however many ids are asked for. Callers that render a
     * list (a feed, a conversation's members) use this instead of one request per user.
     * Unknown and suspended users are simply left out of the result.
     */
    @Transactional(readOnly = true)
    public List<PublicProfile> getPublic(Collection<UUID> userIds) {
        List<UUID> ids = userIds.stream().distinct().toList();
        if (ids.size() > MAX_BATCH_SIZE) {
            throw invalid("At most " + MAX_BATCH_SIZE + " ids may be requested at once");
        }
        Map<UUID, Profile> profilesById = profiles.findAllById(ids).stream()
                .collect(Collectors.toMap(Profile::getId, Function.identity()));
        return users.findAllById(ids).stream()
                .filter(user -> !user.isSuspended())
                .map(user -> {
                    Profile profile = profilesById.get(user.getId());
                    return profile == null ? null : toPublicProfile(user, profile);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private MyProfile toMyProfile(User user, Profile profile) {
        String verification = profile.isVerified()
                ? VerificationRequest.Status.APPROVED.name()
                : verificationRequests.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                        .map(request -> request.getStatus().name())
                        .orElse("NONE");
        return new MyProfile(
                user.getId(),
                user.getEmail(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getDateOfBirth(),
                profile.getHomeCity(),
                Arrays.asList(profile.getStyles()),
                profile.isVerified(),
                verification,
                user.getCreatedAt());
    }

    private PublicProfile toPublicProfile(User user, Profile profile) {
        Integer age = profile.getDateOfBirth() == null ? null : ageOn(profile.getDateOfBirth(), today());
        return new PublicProfile(
                user.getId(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getHomeCity(),
                Arrays.asList(profile.getStyles()),
                profile.isVerified(),
                age,
                user.getCreatedAt());
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    static int ageOn(LocalDate dateOfBirth, LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }
}
