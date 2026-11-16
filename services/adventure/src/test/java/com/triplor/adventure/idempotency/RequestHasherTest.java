package com.triplor.adventure.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RequestHasherTest {

    record Trip(long placeId, LocalDate startDate, List<String> styles, String description) {
    }

    /** The same data as {@link Trip}, with the fields declared in another order. */
    record TripReordered(String description, List<String> styles, LocalDate startDate, long placeId) {
    }

    static final Trip TRIP = new Trip(1269515, LocalDate.parse("2030-03-01"), List.of("CULTURE", "FOODIE"), "Forts");

    @Test
    void theHashIsSha256AndStable() {
        byte[] hash = RequestHasher.hash("adventure.create", TRIP);

        assertThat(hash).hasSize(32);
        assertThat(RequestHasher.hash("adventure.create", TRIP)).isEqualTo(hash);
    }

    @Test
    void theCanonicalFormSortsKeysAndHasNoWhitespace() {
        assertThat(RequestHasher.canonicalJson(TRIP)).isEqualTo(
                "{\"description\":\"Forts\",\"placeId\":1269515,\"startDate\":\"2030-03-01\",\"styles\":[\"CULTURE\",\"FOODIE\"]}");
    }

    @Test
    void theOrderOfFieldsAndMapKeysDoesNotMatter() {
        TripReordered reordered = new TripReordered("Forts", List.of("CULTURE", "FOODIE"), LocalDate.parse("2030-03-01"), 1269515);
        Map<String, Object> oneWay = new LinkedHashMap<>();
        oneWay.put("a", 1);
        oneWay.put("b", 2);
        Map<String, Object> otherWay = new LinkedHashMap<>();
        otherWay.put("b", 2);
        otherWay.put("a", 1);

        assertThat(RequestHasher.hash("adventure.create", reordered)).isEqualTo(RequestHasher.hash("adventure.create", TRIP));
        assertThat(RequestHasher.hash("op", oneWay)).isEqualTo(RequestHasher.hash("op", otherWay));
    }

    @Test
    void aDifferentValueGivesADifferentHash() {
        Trip otherPlace = new Trip(1263968, TRIP.startDate(), TRIP.styles(), TRIP.description());
        Trip otherText = new Trip(TRIP.placeId(), TRIP.startDate(), TRIP.styles(), "Forts!");

        assertThat(RequestHasher.hash("adventure.create", otherPlace)).isNotEqualTo(RequestHasher.hash("adventure.create", TRIP));
        assertThat(RequestHasher.hash("adventure.create", otherText)).isNotEqualTo(RequestHasher.hash("adventure.create", TRIP));
    }

    @Test
    void theOrderOfAListIsPartOfTheValue() {
        // Lists are hashed as given, which is why callers sort the styles before hashing.
        Trip swapped = new Trip(TRIP.placeId(), TRIP.startDate(), List.of("FOODIE", "CULTURE"), TRIP.description());

        assertThat(RequestHasher.hash("adventure.create", swapped)).isNotEqualTo(RequestHasher.hash("adventure.create", TRIP));
    }

    @Test
    void theOperationIsPartOfTheHash() {
        assertThat(RequestHasher.hash("join-request.create", TRIP)).isNotEqualTo(RequestHasher.hash("adventure.create", TRIP));
    }
}
