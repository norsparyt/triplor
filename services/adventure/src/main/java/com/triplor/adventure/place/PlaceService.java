package com.triplor.adventure.place;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import com.triplor.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int DEFAULT_LIMIT = 8;
    static final int MAX_LIMIT = 20;

    private final PlaceRepository places;

    public PlaceService(PlaceRepository places) {
        this.places = places;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> search(String query, Integer limit) {
        String folded = fold(query == null ? "" : query);
        if (folded.length() < MIN_QUERY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "q must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        int pageSize = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return places.searchByNamePrefix(toPrefixPattern(folded), pageSize).stream()
                .map(PlaceResponse::of)
                .toList();
    }

    /**
     * Brings what the user typed to the form of {@code ascii_name}: trimmed, lower case, accents
     * removed. "Zürich" becomes "zurich".
     */
    static String fold(String query) {
        String decomposed = Normalizer.normalize(query.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Escapes LIKE's own wildcards, so a typed '%' or '_' is matched literally, then adds the prefix wildcard. */
    static String toPrefixPattern(String folded) {
        return folded.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
