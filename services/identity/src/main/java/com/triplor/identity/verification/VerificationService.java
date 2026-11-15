package com.triplor.identity.verification;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.triplor.common.error.ApiException;
import com.triplor.identity.account.Profile;
import com.triplor.identity.account.ProfileRepository;
import com.triplor.identity.account.User;
import com.triplor.identity.account.UserRepository;
import com.triplor.identity.verification.VerificationDtos.VerificationResponse;
import com.triplor.identity.verification.VerificationDtos.VerificationReviewItem;
import com.triplor.identity.verification.VerificationRequest.Status;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The verification workflow: a user asks to be verified, an admin approves or rejects.
 *
 * <p>Approval sets {@code profiles.verified_at}. From then on every access token issued for the
 * user carries {@code verified: true}, which is how other services learn about it. A token
 * issued before the approval keeps saying {@code false} until it expires or is refreshed.
 */
@Service
public class VerificationService {

    static final int MAX_QUEUE_PAGE = 100;

    private final VerificationRequestRepository requests;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final Clock clock;

    public VerificationService(VerificationRequestRepository requests, ProfileRepository profiles,
            UserRepository users, Clock clock) {
        this.requests = requests;
        this.profiles = profiles;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public VerificationResponse submit(UUID userId, UUID documentMediaId) {
        Profile profile = profiles.findById(userId).orElseThrow(VerificationService::notFound);
        if (profile.isVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_VERIFIED", "This account is already verified");
        }
        if (requests.existsByUserIdAndStatus(userId, Status.PENDING)) {
            throw pending();
        }
        try {
            // Flush now, so that two simultaneous submissions are caught here by the partial
            // unique index and not at commit time.
            VerificationRequest request = requests.saveAndFlush(
                    new VerificationRequest(UUID.randomUUID(), userId, documentMediaId, clock.instant()));
            return toResponse(request);
        } catch (DataIntegrityViolationException e) {
            throw pending();
        }
    }

    /** One page of the review queue, oldest first, with the requester's name and email. */
    @Transactional(readOnly = true)
    public List<VerificationReviewItem> list(Status status, int limit) {
        int pageSize = Math.max(1, Math.min(limit, MAX_QUEUE_PAGE));
        List<VerificationRequest> page = requests.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, pageSize));

        // Two extra queries for the whole page, not two per row.
        List<UUID> userIds = page.stream().map(VerificationRequest::getUserId).distinct().toList();
        Map<UUID, User> usersById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Profile> profilesById = profiles.findAllById(userIds).stream()
                .collect(Collectors.toMap(Profile::getId, Function.identity()));

        return page.stream()
                .map(request -> toReviewItem(
                        request, usersById.get(request.getUserId()), profilesById.get(request.getUserId())))
                .toList();
    }

    @Transactional
    public VerificationReviewItem approve(UUID requestId, UUID adminId) {
        VerificationRequest request = lockPending(requestId);
        Profile profile = profiles.findById(request.getUserId()).orElseThrow(VerificationService::notFound);
        request.approve(adminId, clock.instant());
        profile.markVerified(clock.instant());
        return toReviewItem(request, users.findById(request.getUserId()).orElse(null), profile);
    }

    @Transactional
    public VerificationReviewItem reject(UUID requestId, UUID adminId, String reason) {
        VerificationRequest request = lockPending(requestId);
        request.reject(reason.trim(), adminId, clock.instant());
        return toReviewItem(
                request,
                users.findById(request.getUserId()).orElse(null),
                profiles.findById(request.getUserId()).orElse(null));
    }

    /**
     * Loads the request with a row lock and insists that it is still pending. If two admins act
     * on the same request, the second waits for the first, then sees it has been reviewed.
     */
    private VerificationRequest lockPending(UUID requestId) {
        VerificationRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Verification request not found"));
        if (!request.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERIFICATION_ALREADY_REVIEWED",
                    "This request has already been " + request.getStatus().name().toLowerCase());
        }
        return request;
    }

    private static VerificationResponse toResponse(VerificationRequest request) {
        return new VerificationResponse(
                request.getId(), request.getStatus().name(), request.getReason(),
                request.getCreatedAt(), request.getReviewedAt());
    }

    private static VerificationReviewItem toReviewItem(VerificationRequest request, User user, Profile profile) {
        return new VerificationReviewItem(
                request.getId(),
                request.getUserId(),
                user == null ? null : user.getEmail(),
                profile == null ? null : profile.getDisplayName(),
                request.getDocumentMediaId(),
                request.getStatus().name(),
                request.getReason(),
                request.getCreatedAt(),
                request.getReviewedAt(),
                request.getReviewedBy());
    }

    private static ApiException pending() {
        return new ApiException(HttpStatus.CONFLICT, "VERIFICATION_PENDING", "A verification request is already pending");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
    }
}
