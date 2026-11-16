package com.triplor.adventure.place;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaceController {

    private final PlaceService places;

    public PlaceController(PlaceService places) {
        this.places = places;
    }

    /** Type-ahead for the destination field: {@code GET /places?q=jai}. */
    @GetMapping("/places")
    List<PlaceResponse> search(@RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return places.search(query, limit);
    }
}
