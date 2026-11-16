package com.triplor.adventure.joining;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.triplor.adventure.idempotency.IdempotencyService;
import com.triplor.adventure.joining.JoinRequestDtos.JoinRequestResponse;
import com.triplor.adventure.joining.JoinRequestDtos.MemberResponse;
import com.triplor.adventure.joining.JoinRequestStateMachine.Action;
import com.triplor.adventure.joining.JoinRequestStateMachine.Transition;
import com.triplor.adventure.joining.JoinRequestStore.AdventureState;
import com.triplor.adventure.joining.MembershipNotifier.MembershipNotificationException;
import com.triplor.adventure.trip.AdventureRules;
import com.triplor.adventure.trip.AdventureStatus;
import com.triplor.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JoinRequestService {

    private static final Logger log = LoggerFactory.getLogger(JoinRequestService.class);

    static final String CREATE_OPERATION = "join-request.create";
    static final int DEFAULT_PAGE = 50;
    static final int MAX_PAGE = 100;

    /** What the idempotency hash of a create is computed over. */
    record CreateCommand(UUID adventureId, String message) {
    }

    private enum AcceptOutcome { ACCEPTED, NO_SEAT, NOT_PENDING }

    private final JoinRequestStore store;
    private final IdempotencyService idempotency;
    private final MembershipNotifier notifier;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public JoinRequestService(JoinRequestStore store, IdempotencyService idempotency, MembershipNotifier notifier,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.store = store;
        this.idempotency = idempotency;
        this.notifier = notifier;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Asks to join an adventure, once per idempotency key.
     *
     * <p>The adventure row is read with a shared lock. A cancel running at the same moment
     * either commits first, in which case this sees CANCELLED and refuses, or waits for this
     * transaction and then rejects the new request along with the other pending ones. No
     * pending request can be left behind on a cancelled trip.
     */
    @Transactional
    public IdempotencyService.Result<JoinRequestResponse> create(UUID adventureId, UUID requesterId,
            String idempotencyKey, String message) {
        CreateCommand command = new CreateCommand(adventureId, normalizeMessage(message));
        return idempotency.execute(requesterId, idempotencyKey, CREATE_OPERATION, command,
                HttpStatus.CREATED.value(), JoinRequestResponse.class, () -> insert(requesterId, command));
    }

    private JoinRequestResponse insert(UUID requesterId, CreateCommand command) {
        AdventureState adventure = store.findAdventureForShare(command.adventureId())
                .orElseThrow(JoinRequestService::adventureNotFound);
        if (adventure.organizerId().equals(requesterId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CANNOT_JOIN_OWN_ADVENTURE",
                    "You organize this adventure");
        }
        if (adventure.status() == AdventureStatus.FULL) {
            throw full();
        }
        if (adventure.status() != AdventureStatus.OPEN) {
            throw notOpen("This adventure is " + adventure.status().name().toLowerCase());
        }
        if (adventure.startDate().isBefore(AdventureRules.earliestStartDate(today()))) {
            throw notOpen("This adventure has already started");
        }

        store.expireStale(adventure.id(), requesterId);
        UUID id = UUID.randomUUID();
        if (store.insert(id, adventure.id(), requesterId, command.message()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_EXISTS",
                    "You already have a pending or accepted request for this adventure");
        }
        return JoinRequestResponse.of(require(id));
    }

    /**
     * Accepts a request: takes a seat, marks the request accepted and records the member, all
     * in one transaction, then tells chat.
     *
     * <p>This method is deliberately not {@code @Transactional}. The call to chat is a network
     * call and must not run while a database transaction, and the row locks it holds, are
     * open. The transaction is the block passed to {@code transaction.execute}, nothing more.
     *
     * <p>If chat cannot be reached the caller gets 502, but the seat stays taken: the commit
     * has already happened. Accepting again is safe and tells chat again. Phase 2 replaces
     * this weak spot with an event written in the same transaction.
     */
    public JoinRequestResponse accept(UUID requestId, UUID callerId) {
        JoinRequest request = require(requestId);
        AdventureState adventure = requireOrganizer(request.adventureId(), callerId);

        Transition transition = JoinRequestStateMachine.on(request.status(), Action.ACCEPT);
        if (transition == Transition.INVALID) {
            throw invalidTransition(request.status(), Action.ACCEPT);
        }
        if (transition == Transition.MOVE) {
            AcceptOutcome outcome = transaction.execute(status -> {
                // Adventure row first, request row second: the lock order every writer follows.
                if (store.takeSeat(adventure.id()) == 0) {
                    return AcceptOutcome.NO_SEAT;
                }
                if (store.markAccepted(requestId) == 0) {
                    // The request stopped being pending in the meantime. Give the seat back.
                    status.setRollbackOnly();
                    return AcceptOutcome.NOT_PENDING;
                }
                store.addMember(adventure.id(), request.requesterId());
                return AcceptOutcome.ACCEPTED;
            });
            if (outcome != AcceptOutcome.ACCEPTED) {
                explainFailedAccept(requestId, adventure.id(), outcome);
            }
        }

        notifyChat(adventure, request.requesterId());
        return JoinRequestResponse.of(require(requestId));
    }

    /**
     * Nothing was written. Reads the current state to decide the answer. If the request is
     * accepted by now, another call won the race, and this one succeeds too.
     */
    private void explainFailedAccept(UUID requestId, UUID adventureId, AcceptOutcome outcome) {
        JoinRequest current = require(requestId);
        Transition transition = JoinRequestStateMachine.on(current.status(), Action.ACCEPT);
        if (transition == Transition.NO_OP) {
            return;
        }
        if (transition == Transition.INVALID) {
            throw invalidTransition(current.status(), Action.ACCEPT);
        }
        // Still pending, so the seat was the problem.
        AdventureStatus status = store.findAdventure(adventureId).orElseThrow(JoinRequestService::adventureNotFound).status();
        if (outcome == AcceptOutcome.NO_SEAT && status != AdventureStatus.OPEN && status != AdventureStatus.FULL) {
            throw notOpen("This adventure is " + status.name().toLowerCase());
        }
        throw full();
    }

    /** Rejects a pending request. Rejecting a rejected request succeeds and changes nothing. */
    public JoinRequestResponse reject(UUID requestId, UUID callerId) {
        JoinRequest request = require(requestId);
        requireOrganizer(request.adventureId(), callerId);
        move(request, Action.REJECT);
        return JoinRequestResponse.of(require(requestId));
    }

    /** Takes back a pending request. Withdrawing a withdrawn request succeeds and changes nothing. */
    public void withdraw(UUID requestId, UUID callerId) {
        JoinRequest request = require(requestId);
        if (!request.requesterId().equals(callerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the requester can withdraw a request");
        }
        move(request, Action.WITHDRAW);
    }

    /**
     * Reject and withdraw: one conditional UPDATE on the request row. The state machine is
     * asked before it, for a precise answer, and again after it if the UPDATE changed nothing,
     * because another call may have decided the request in between.
     */
    private void move(JoinRequest request, Action action) {
        Transition transition = JoinRequestStateMachine.on(request.status(), action);
        if (transition == Transition.MOVE) {
            int changed = action == Action.REJECT ? store.markRejected(request.id()) : store.markWithdrawn(request.id());
            if (changed == 1) {
                return;
            }
            JoinRequest current = require(request.id());
            transition = JoinRequestStateMachine.on(current.status(), action);
            if (transition != Transition.NO_OP) {
                throw invalidTransition(current.status(), action);
            }
        } else if (transition == Transition.INVALID) {
            throw invalidTransition(request.status(), action);
        }
    }

    public List<JoinRequestResponse> listForAdventure(UUID adventureId, UUID callerId, JoinRequestStatus status, Integer limit) {
        requireOrganizer(adventureId, callerId);
        return store.findByAdventure(adventureId, status, pageSize(limit)).stream().map(JoinRequestResponse::of).toList();
    }

    public List<JoinRequestResponse> listIncoming(UUID organizerId, JoinRequestStatus status, Integer limit) {
        return store.findIncoming(organizerId, status, pageSize(limit)).stream().map(JoinRequestResponse::of).toList();
    }

    public List<JoinRequestResponse> listOutgoing(UUID requesterId, JoinRequestStatus status, Integer limit) {
        return store.findOutgoing(requesterId, status, pageSize(limit)).stream().map(JoinRequestResponse::of).toList();
    }

    /** The people on a trip. Visible to them only. */
    public List<MemberResponse> members(UUID adventureId, UUID callerId) {
        if (store.findAdventure(adventureId).isEmpty()) {
            throw adventureNotFound();
        }
        List<JoinRequestStore.Member> members = store.findMembers(adventureId);
        if (members.stream().noneMatch(member -> member.userId().equals(callerId))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only members can see who is on this adventure");
        }
        return members.stream()
                .map(member -> new MemberResponse(member.userId(), member.role(), member.joinedAt()))
                .toList();
    }

    private void notifyChat(AdventureState adventure, UUID memberId) {
        try {
            notifier.membersAdded(adventure.id(), List.of(adventure.organizerId(), memberId));
        } catch (MembershipNotificationException e) {
            log.warn("Could not tell chat about member {} of adventure {}", memberId, adventure.id(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CHAT_UNAVAILABLE",
                    "The request was accepted, but the conversation could not be updated. Accept it again to retry.");
        }
    }

    private JoinRequest require(UUID requestId) {
        return store.find(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Join request not found"));
    }

    private AdventureState requireOrganizer(UUID adventureId, UUID callerId) {
        AdventureState adventure = store.findAdventure(adventureId).orElseThrow(JoinRequestService::adventureNotFound);
        if (!adventure.organizerId().equals(callerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the organizer can do this");
        }
        return adventure;
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.length() > JoinRequestDtos.MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "message must be at most " + JoinRequestDtos.MAX_MESSAGE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static int pageSize(Integer limit) {
        return limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static ApiException adventureNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Adventure not found");
    }

    private static ApiException full() {
        return new ApiException(HttpStatus.CONFLICT, "ADVENTURE_FULL", "No seats remain on this adventure");
    }

    private static ApiException notOpen(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "ADVENTURE_NOT_OPEN", detail);
    }

    private static ApiException invalidTransition(JoinRequestStatus status, Action action) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                "A request that is " + status.name().toLowerCase() + " cannot be "
                        + action.result().name().toLowerCase());
    }
}
