package com.triplor.adventure.trip;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.triplor.adventure.idempotency.IdempotencyService;
import com.triplor.adventure.trip.AdventureDtos.AdventureResponse;
import com.triplor.adventure.trip.AdventureDtos.CreateAdventureRequest;
import com.triplor.adventure.trip.AdventureDtos.EditAdventureRequest;
import com.triplor.common.error.ApiException;
import com.triplor.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/adventures")
public class AdventureController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final AdventureService adventures;

    public AdventureController(AdventureService adventures) {
        this.adventures = adventures;
    }

    /**
     * Creates an adventure. The {@code Idempotency-Key} header makes a retry safe: a repeat of
     * the same request returns the first response again, marked {@code Idempotent-Replayed: true}.
     */
    @PostMapping
    ResponseEntity<AdventureResponse> create(CurrentUser user,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateAdventureRequest request) {
        user.requireVerified();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "The Idempotency-Key header is required");
        }
        IdempotencyService.Result<AdventureResponse> result =
                adventures.create(user.id(), idempotencyKey.trim(), request);
        AdventureResponse adventure = result.response();

        ResponseEntity.BodyBuilder response = ResponseEntity
                .created(URI.create("/adventures/" + adventure.id()))
                .eTag(ETags.of(adventure.version()));
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(adventure);
    }

    /**
     * The caller's adventures: {@code role=organizer} (the default) lists the ones they
     * organize, {@code role=joined} the ones they have been accepted on.
     */
    @GetMapping("/mine")
    List<AdventureResponse> mine(CurrentUser user,
            @RequestParam(name = "role", defaultValue = "organizer") String role,
            @RequestParam(name = "limit", required = false) Integer limit) {
        if ("organizer".equalsIgnoreCase(role)) {
            return adventures.listOrganizedBy(user.id(), limit);
        }
        if ("joined".equalsIgnoreCase(role)) {
            return adventures.listJoinedBy(user.id(), limit);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "role must be 'organizer' or 'joined'");
    }

    @GetMapping("/{id}")
    ResponseEntity<AdventureResponse> get(CurrentUser user, @PathVariable UUID id) {
        AdventureResponse adventure = adventures.get(id, user.id());
        return ResponseEntity.ok().eTag(ETags.of(adventure.version())).body(adventure);
    }

    /**
     * Edits an adventure. The client must send the ETag it last saw in {@code If-Match}; if the
     * adventure has changed since, the edit is refused with 412 instead of overwriting the
     * other change.
     */
    @PatchMapping("/{id}")
    ResponseEntity<AdventureResponse> edit(CurrentUser user, @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody EditAdventureRequest request) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
                    "Send the adventure's ETag in the If-Match header");
        }
        long expectedVersion = ETags.parse(ifMatch).orElseThrow(() -> new ApiException(
                HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT", "If-Match does not match the current version"));
        AdventureResponse adventure = adventures.edit(id, user.id(), expectedVersion, request);
        return ResponseEntity.ok().eTag(ETags.of(adventure.version())).body(adventure);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> cancel(CurrentUser user, @PathVariable UUID id) {
        adventures.cancel(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
