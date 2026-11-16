package com.triplor.adventure.joining;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.triplor.adventure.idempotency.IdempotencyService;
import com.triplor.adventure.joining.JoinRequestDtos.CreateJoinRequest;
import com.triplor.adventure.joining.JoinRequestDtos.JoinRequestResponse;
import com.triplor.adventure.joining.JoinRequestDtos.MemberResponse;
import com.triplor.common.error.ApiException;
import com.triplor.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JoinRequestController {

    private final JoinRequestService joinRequests;

    public JoinRequestController(JoinRequestService joinRequests) {
        this.joinRequests = joinRequests;
    }

    /** Asks to join. Needs an {@code Idempotency-Key}, like every create. */
    @PostMapping("/adventures/{adventureId}/join-requests")
    ResponseEntity<JoinRequestResponse> create(CurrentUser user, @PathVariable UUID adventureId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) CreateJoinRequest body) {
        user.requireVerified();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "The Idempotency-Key header is required");
        }
        IdempotencyService.Result<JoinRequestResponse> result = joinRequests.create(
                adventureId, user.id(), idempotencyKey.trim(), body == null ? null : body.message());

        ResponseEntity.BodyBuilder response =
                ResponseEntity.created(URI.create("/join-requests/" + result.response().id()));
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.response());
    }

    /** The organizer's view of one adventure's requests, oldest first. */
    @GetMapping("/adventures/{adventureId}/join-requests")
    List<JoinRequestResponse> forAdventure(CurrentUser user, @PathVariable UUID adventureId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return joinRequests.listForAdventure(adventureId, user.id(), parseStatus(status), limit);
    }

    /**
     * The Requests tab: {@code direction=incoming} lists requests on adventures the caller
     * organizes, {@code direction=outgoing} the caller's own requests. Newest first.
     */
    @GetMapping("/join-requests")
    List<JoinRequestResponse> list(CurrentUser user,
            @RequestParam(name = "direction") String direction,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "incoming" -> joinRequests.listIncoming(user.id(), parseStatus(status), limit);
            case "outgoing" -> joinRequests.listOutgoing(user.id(), parseStatus(status), limit);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "direction must be 'incoming' or 'outgoing'");
        };
    }

    /** No idempotency key needed: accepting an accepted request is a no-op that succeeds. */
    @PostMapping("/join-requests/{id}/accept")
    JoinRequestResponse accept(CurrentUser user, @PathVariable UUID id) {
        return joinRequests.accept(id, user.id());
    }

    @PostMapping("/join-requests/{id}/reject")
    JoinRequestResponse reject(CurrentUser user, @PathVariable UUID id) {
        return joinRequests.reject(id, user.id());
    }

    /** The requester withdraws a pending request. */
    @DeleteMapping("/join-requests/{id}")
    ResponseEntity<Void> withdraw(CurrentUser user, @PathVariable UUID id) {
        joinRequests.withdraw(id, user.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/adventures/{adventureId}/members")
    List<MemberResponse> members(CurrentUser user, @PathVariable UUID adventureId) {
        return joinRequests.members(adventureId, user.id());
    }

    private static JoinRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return JoinRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown status: " + status);
        }
    }
}
