package com.triplor.adventure.trip;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.triplor.adventure.idempotency.IdempotencyService;
import com.triplor.adventure.place.Place;
import com.triplor.adventure.place.PlaceRepository;
import com.triplor.adventure.trip.AdventureDtos.AdventureResponse;
import com.triplor.adventure.trip.AdventureDtos.CreateAdventureRequest;
import com.triplor.adventure.trip.AdventureDtos.EditAdventureRequest;
import com.triplor.common.error.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdventureService {

    static final String CREATE_OPERATION = "adventure.create";
    static final int MAX_MINE_PAGE = 100;

    /** What the idempotency hash is computed over: the create request after normalisation. */
    record CreateCommand(long placeId, LocalDate startDate, LocalDate endDate, List<String> styles,
            String description, int capacity) {
    }

    private final AdventureRepository adventures;
    private final AdventureWriter writer;
    private final AdventureLinks links;
    private final PlaceRepository places;
    private final IdempotencyService idempotency;
    private final EntityManager entityManager;
    private final Clock clock;

    public AdventureService(AdventureRepository adventures, AdventureWriter writer, AdventureLinks links,
            PlaceRepository places, IdempotencyService idempotency, EntityManager entityManager, Clock clock) {
        this.adventures = adventures;
        this.writer = writer;
        this.links = links;
        this.places = places;
        this.idempotency = idempotency;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Creates an adventure, once per idempotency key. The key row and the adventure row are
     * written in this one transaction, so a retry can never find one without the other.
     */
    @Transactional
    public IdempotencyService.Result<AdventureResponse> create(UUID organizerId, String idempotencyKey,
            CreateAdventureRequest request) {
        CreateCommand command = new CreateCommand(
                request.placeId(),
                request.startDate(),
                request.endDate(),
                List.of(AdventureRules.normalizeStyles(request.styles())),
                AdventureRules.normalizeDescription(request.description()),
                request.capacity());
        AdventureRules.checkDates(command.startDate(), command.endDate(), today(), true);
        AdventureRules.checkCapacity(command.capacity());

        return idempotency.execute(organizerId, idempotencyKey, CREATE_OPERATION, command,
                HttpStatus.CREATED.value(), AdventureResponse.class, () -> insert(organizerId, command));
    }

    private AdventureResponse insert(UUID organizerId, CreateCommand command) {
        Place place = findPlace(command.placeId());
        Adventure adventure = new Adventure(
                UUID.randomUUID(), organizerId, place, command.startDate(), command.endDate(),
                command.styles().toArray(String[]::new), command.description(), command.capacity());
        adventures.saveAndFlush(adventure);
        links.addOrganizer(adventure.getId(), organizerId);
        // The database filled in seats taken, status, version and the timestamps. Read the row
        // back to answer with them.
        return AdventureResponse.of(reload(adventure.getId()));
    }

    /** One adventure, with how many people are waiting to join and the viewer's own request. */
    @Transactional(readOnly = true)
    public AdventureResponse get(UUID id, UUID viewerId) {
        Adventure adventure = find(id);
        return AdventureResponse.of(
                adventure, links.pendingRequestCount(id), links.latestRequestOf(id, viewerId).orElse(null));
    }

    /** Adventures the caller has been accepted on, latest start date first. */
    @Transactional(readOnly = true)
    public List<AdventureResponse> listJoinedBy(UUID userId, Integer limit) {
        int pageSize = limit == null ? MAX_MINE_PAGE : Math.max(1, Math.min(limit, MAX_MINE_PAGE));
        List<UUID> ids = links.joinedAdventureIds(userId, pageSize);
        // One query for all of them, then back into the order the ids came in.
        Map<UUID, Adventure> byId = adventures.findWithPlaceByIdIn(ids).stream()
                .collect(Collectors.toMap(Adventure::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(AdventureResponse::of).toList();
    }

    /** The caller's own adventures, latest start date first. */
    @Transactional(readOnly = true)
    public List<AdventureResponse> listOrganizedBy(UUID organizerId, Integer limit) {
        int pageSize = limit == null ? MAX_MINE_PAGE : Math.max(1, Math.min(limit, MAX_MINE_PAGE));
        return adventures.findByOrganizerIdOrderByStartDateDescIdDesc(organizerId, Limit.of(pageSize)).stream()
                .map(AdventureResponse::of)
                .toList();
    }

    /**
     * Edits an adventure if {@code expectedVersion} (from the client's {@code If-Match}) is
     * still the row's version.
     *
     * <p>The checks made here on the loaded row give precise errors, but they are not what
     * keeps the data correct: another request may change the row right after it was read. The
     * UPDATE repeats the conditions in its WHERE clause, and that is the check that counts.
     */
    @Transactional
    public AdventureResponse edit(UUID id, UUID callerId, long expectedVersion, EditAdventureRequest request) {
        Adventure current = find(id);
        requireOrganizer(current, callerId);
        ApiException rejection = rejectionFor(current, expectedVersion, request.capacity());
        if (rejection != null) {
            throw rejection;
        }

        LocalDate startDate = request.startDate() == null ? current.getStartDate() : request.startDate();
        LocalDate endDate = request.endDate() == null ? current.getEndDate() : request.endDate();
        boolean startDateChanged = !startDate.equals(current.getStartDate());
        AdventureRules.checkDates(startDate, endDate, today(), startDateChanged);

        AdventureWriter.Changes changes = new AdventureWriter.Changes(
                request.placeId() == null ? null : findPlace(request.placeId()),
                request.startDate(),
                request.endDate(),
                request.styles() == null ? null : AdventureRules.normalizeStyles(request.styles()),
                request.description() == null ? null : AdventureRules.normalizeDescription(request.description()),
                request.capacity());

        if (writer.edit(id, expectedVersion, changes) == 0) {
            // The row changed between the read above and the UPDATE. Read it again to say why.
            ApiException afterTheFact = rejectionFor(reload(id), expectedVersion, request.capacity());
            throw afterTheFact != null ? afterTheFact : versionConflict();
        }
        return AdventureResponse.of(reload(id));
    }

    /** Cancels an adventure. Cancelling one that is already cancelled succeeds and changes nothing. */
    @Transactional
    public void cancel(UUID id, UUID callerId) {
        Adventure current = find(id);
        requireOrganizer(current, callerId);
        if (current.getStatus() == AdventureStatus.CANCELLED) {
            return;
        }
        if (current.getStatus() == AdventureStatus.COMPLETED) {
            throw notOpen(current.getStatus());
        }
        // 0 rows here means a concurrent request cancelled it first, which is the outcome asked for.
        if (writer.cancel(id) == 1) {
            // The UPDATE above holds the adventure row. Travellers asking to join right now
            // wait for this transaction, then see CANCELLED; requests that got in before it
            // are rejected here. Accepted members stay as they are.
            links.rejectPendingRequests(id);
        }
    }

    /** Why an edit cannot go ahead on this row, or null if it can. */
    private static ApiException rejectionFor(Adventure adventure, long expectedVersion, Integer newCapacity) {
        if (adventure.getVersion() != expectedVersion) {
            return versionConflict();
        }
        if (!adventure.getStatus().isEditable()) {
            return notOpen(adventure.getStatus());
        }
        if (newCapacity != null && newCapacity < adventure.getSeatsTaken()) {
            return new ApiException(HttpStatus.CONFLICT, "CAPACITY_BELOW_SEATS",
                    "capacity cannot be lower than the " + adventure.getSeatsTaken() + " seats already taken");
        }
        return null;
    }

    private Adventure find(UUID id) {
        return adventures.findWithPlaceById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Adventure not found"));
    }

    /**
     * Reads the row again after it was inserted or changed by {@link AdventureWriter}.
     * Hibernate still holds the object from before and would hand it back unchanged, so the
     * persistence context is cleared first.
     */
    private Adventure reload(UUID id) {
        entityManager.clear();
        return find(id);
    }

    private Place findPlace(long placeId) {
        return places.findById(placeId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown placeId"));
    }

    private static void requireOrganizer(Adventure adventure, UUID callerId) {
        if (!adventure.isOrganizedBy(callerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the organizer can change this adventure");
        }
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                "The adventure was changed since you loaded it. Fetch it again and retry.");
    }

    private static ApiException notOpen(AdventureStatus status) {
        return new ApiException(HttpStatus.CONFLICT, "ADVENTURE_NOT_OPEN",
                "This adventure is " + status.name().toLowerCase() + " and can no longer be changed");
    }
}
