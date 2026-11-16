package com.triplor.adventure.trip;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.triplor.adventure.place.PlaceResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdventureDtos {

    private AdventureDtos() {
    }

    public record CreateAdventureRequest(
            @NotNull Long placeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty @Size(max = 6) List<@NotNull TravelStyle> styles,
            @NotBlank @Size(max = AdventureRules.MAX_DESCRIPTION_LENGTH) String description,
            @NotNull @Min(AdventureRules.MIN_CAPACITY) @Max(AdventureRules.MAX_CAPACITY) Integer capacity) {
    }

    /** A partial update: a field that is absent (null) is left as it is. */
    public record EditAdventureRequest(
            Long placeId,
            LocalDate startDate,
            LocalDate endDate,
            @Size(min = 1, max = 6) List<@NotNull TravelStyle> styles,
            @Size(max = AdventureRules.MAX_DESCRIPTION_LENGTH) String description,
            @Min(AdventureRules.MIN_CAPACITY) @Max(AdventureRules.MAX_CAPACITY) Integer capacity) {
    }

    public record AdventureResponse(
            UUID id,
            UUID organizerId,
            PlaceResponse place,
            LocalDate startDate,
            LocalDate endDate,
            List<String> styles,
            String description,
            int capacity,
            int seatsTaken,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            /** How many requests are waiting for the organizer. Only on {@code GET /adventures/{id}}. */
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer interestedCount,
            /** The caller's own latest request, if they made one. Only on {@code GET /adventures/{id}}. */
            @JsonInclude(JsonInclude.Include.NON_NULL) AdventureLinks.ViewerRequest viewerRequest) {

        static AdventureResponse of(Adventure adventure) {
            return of(adventure, null, null);
        }

        static AdventureResponse of(Adventure adventure, Integer interestedCount, AdventureLinks.ViewerRequest viewerRequest) {
            return new AdventureResponse(
                    adventure.getId(),
                    adventure.getOrganizerId(),
                    PlaceResponse.of(adventure.getPlace()),
                    adventure.getStartDate(),
                    adventure.getEndDate(),
                    List.of(adventure.getStyles()),
                    adventure.getDescription(),
                    adventure.getCapacity(),
                    adventure.getSeatsTaken(),
                    adventure.getStatus().name(),
                    adventure.getVersion(),
                    adventure.getCreatedAt(),
                    adventure.getUpdatedAt(),
                    interestedCount,
                    viewerRequest);
        }
    }
}
