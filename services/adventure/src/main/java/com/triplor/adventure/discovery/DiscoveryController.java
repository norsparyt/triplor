package com.triplor.adventure.discovery;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.triplor.adventure.discovery.DiscoveryDtos.Match;
import com.triplor.adventure.discovery.DiscoveryDtos.SearchPage;
import com.triplor.common.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/adventures")
public class DiscoveryController {

    private final DiscoveryService discovery;

    public DiscoveryController(DiscoveryService discovery) {
        this.discovery = discovery;
    }

    /**
     * Open adventures near a point that overlap a date window, soonest first.
     * {@code GET /adventures/search?lat=26.9&lng=75.8&radiusKm=50&from=2027-01-10&to=2027-01-20&styles=HIKING,FOODIE}.
     * To get the next page, send the same parameters plus the {@code nextCursor} of the last response.
     */
    @GetMapping("/search")
    SearchPage search(CurrentUser user,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lng", required = false) Double lng,
            @RequestParam(name = "radiusKm", required = false) Double radiusKm,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "styles", required = false) List<String> styles,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return discovery.search(user.id(), lat, lng, radiusKm, from, to, styles, cursor, limit);
    }

    /** Other people's open adventures that fit the caller's own one, best fit first. Organizer only. */
    @GetMapping("/{id}/matches")
    List<Match> matches(CurrentUser user, @PathVariable UUID id,
            @RequestParam(name = "radiusKm", required = false) Double radiusKm,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return discovery.matches(id, user.id(), radiusKm, limit);
    }
}
