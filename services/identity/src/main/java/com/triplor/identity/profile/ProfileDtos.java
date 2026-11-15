package com.triplor.identity.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the {@code /users} endpoints. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /**
     * What a user sees about themselves. Includes private fields (email, date of birth) that
     * never appear on a {@link PublicProfile}.
     *
     * @param verification state of the latest verification request: NONE, PENDING, APPROVED or REJECTED
     */
    public record MyProfile(
            UUID id,
            String email,
            String displayName,
            String bio,
            LocalDate dateOfBirth,
            String homeCity,
            List<String> styles,
            boolean verified,
            String verification,
            Instant memberSince) {
    }

    /**
     * What other users may see. It exposes the age, never the date of birth, and no email.
     *
     * @param age whole years, or null if the user has not given a date of birth
     */
    public record PublicProfile(
            UUID id,
            String displayName,
            String bio,
            String homeCity,
            List<String> styles,
            boolean verified,
            Integer age,
            Instant memberSince) {
    }

    /**
     * A partial update: a field that is absent (null) is left unchanged. To clear {@code bio} or
     * {@code homeCity}, send an empty string. An unknown style name makes the whole request
     * malformed.
     */
    public record UpdateProfileRequest(
            @Size(min = 2, max = 50) String displayName,
            @Size(max = 500) String bio,
            @Past LocalDate dateOfBirth,
            @Size(max = 100) String homeCity,
            @Size(max = 6) Set<TravelStyle> styles) {
    }
}
