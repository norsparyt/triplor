package com.triplor.adventure.discovery;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.triplor.adventure.place.PlaceResponse;

public final class DiscoveryDtos {

    private DiscoveryDtos() {
    }

    /** An open adventure as discovery shows it: enough for a card in a list. */
    public record AdventureCard(
            UUID id,
            UUID organizerId,
            PlaceResponse place,
            LocalDate startDate,
            LocalDate endDate,
            List<String> styles,
            String description,
            int capacity,
            int seatsTaken,
            double distanceKm) {
    }

    /** One page of search results. {@code nextCursor} is null on the last page. */
    public record SearchPage(List<AdventureCard> items, String nextCursor) {
    }

    /** How well another adventure fits the organizer's own, overall and per component, each from 0 to 1. */
    public record MatchScore(double total, double dateOverlap, double styleSimilarity, double proximity) {
    }

    public record Match(AdventureCard adventure, MatchScore score) {
    }
}
