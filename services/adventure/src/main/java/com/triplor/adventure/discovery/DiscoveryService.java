package com.triplor.adventure.discovery;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.triplor.adventure.discovery.DiscoveryDtos.AdventureCard;
import com.triplor.adventure.discovery.DiscoveryDtos.Match;
import com.triplor.adventure.discovery.DiscoveryDtos.SearchPage;
import com.triplor.adventure.trip.Adventure;
import com.triplor.adventure.trip.AdventureRepository;
import com.triplor.adventure.trip.AdventureRules;
import com.triplor.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryService {

    static final double DEFAULT_MATCH_RADIUS_KM = SearchCriteria.DEFAULT_RADIUS_KM;
    static final int DEFAULT_MATCH_LIMIT = 10;
    static final int MAX_MATCH_LIMIT = 20;

    /** How many candidates are scored at most. Beyond this the ranking covers the best-overlapping, nearest ones only. */
    static final int MAX_MATCH_CANDIDATES = 200;

    private final DiscoveryRepository discovery;
    private final AdventureRepository adventures;
    private final Clock clock;

    public DiscoveryService(DiscoveryRepository discovery, AdventureRepository adventures, Clock clock) {
        this.discovery = discovery;
        this.adventures = adventures;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SearchPage search(UUID viewerId, Double lat, Double lng, Double radiusKm, LocalDate from, LocalDate to,
            List<String> styles, String cursor, Integer limit) {
        LocalDate today = today();
        SearchCriteria criteria = SearchCriteria.of(lat, lng, radiusKm, from, to, styles, cursor, limit, today);

        // One row more than the page: if it comes back, there is a next page.
        List<AdventureCard> rows = discovery.search(
                criteria, viewerId, AdventureRules.earliestStartDate(today), criteria.limit() + 1);
        if (rows.size() <= criteria.limit()) {
            return new SearchPage(rows, null);
        }
        List<AdventureCard> page = rows.subList(0, criteria.limit());
        AdventureCard last = page.getLast();
        return new SearchPage(List.copyOf(page), new Cursor(last.startDate(), last.id()).encode());
    }

    /**
     * Other open adventures that could be combined with the organizer's own: nearby, with
     * overlapping dates, best fit first. The database narrows the candidates down with its
     * indexes; the scoring is plain Java in {@link MatchScorer}.
     */
    @Transactional(readOnly = true)
    public List<Match> matches(UUID adventureId, UUID callerId, Double radiusKm, Integer limit) {
        Adventure mine = adventures.findById(adventureId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Adventure not found"));
        if (!mine.isOrganizedBy(callerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the organizer can see an adventure's matches");
        }
        if (!mine.getStatus().isEditable()) {
            throw new ApiException(HttpStatus.CONFLICT, "ADVENTURE_NOT_OPEN",
                    "This adventure is " + mine.getStatus().name().toLowerCase() + " and has no matches");
        }
        double radius = radiusKm == null ? DEFAULT_MATCH_RADIUS_KM : radiusKm;
        if (!(radius > 0 && radius <= SearchCriteria.MAX_RADIUS_KM)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "radiusKm must be greater than 0 and at most " + (int) SearchCriteria.MAX_RADIUS_KM);
        }
        int pageSize = limit == null ? DEFAULT_MATCH_LIMIT : Math.max(1, Math.min(limit, MAX_MATCH_LIMIT));
        List<String> myStyles = List.of(mine.getStyles());

        return discovery.matchCandidates(
                        adventureId, radius * 1000, AdventureRules.earliestStartDate(today()), MAX_MATCH_CANDIDATES)
                .stream()
                .map(candidate -> new Match(candidate, MatchScorer.score(
                        mine.getStartDate(), mine.getEndDate(), myStyles,
                        candidate.startDate(), candidate.endDate(), candidate.styles(),
                        candidate.distanceKm(), radius)))
                // Highest score first; the id makes the order of equal scores stable.
                .sorted(Comparator.comparingDouble((Match match) -> match.score().total()).reversed()
                        .thenComparing(match -> match.adventure().id()))
                .limit(pageSize)
                .toList();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
