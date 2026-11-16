package com.triplor.adventure.joining;

import static com.triplor.adventure.AdventureApi.body;
import static com.triplor.adventure.AdventureApi.inDays;
import static com.triplor.adventure.AdventureApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import com.triplor.adventure.RecordingMembershipNotifier;
import com.triplor.testsupport.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class JoinRequestIntegrationTest extends AdventureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingMembershipNotifier chat;

    AdventureApi api;
    UUID asha; // organizes
    UUID ravi; // asks to join
    UUID meera;
    String adventure;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdventureApi(mvc);
        asha = UUID.randomUUID();
        ravi = UUID.randomUUID();
        meera = UUID.randomUUID();
        adventure = api.createdId(asha, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "Forts and food", 2));
    }

    // ---- asking to join ----

    @Test
    void aTravellerCanAskToJoin() throws Exception {
        String response = api.requestToJoin(ravi, adventure, newKey(), "  Can I come along?  ")
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.adventureId").value(adventure))
                .andExpect(jsonPath("$.requesterId").value(ravi.toString()))
                .andExpect(jsonPath("$.message").value("Can I come along?"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.decidedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // The organizer has 72 hours to decide.
        Instant created = Instant.parse(JsonPath.read(response, "$.createdAt"));
        Instant expires = Instant.parse(JsonPath.read(response, "$.expiresAt"));
        assertThat(Duration.between(created, expires)).isEqualTo(Duration.ofHours(72));

        // Asking takes no seat.
        api.get(asha, adventure)
                .andExpect(jsonPath("$.seatsTaken").value(0))
                .andExpect(jsonPath("$.interestedCount").value(1))
                .andExpect(jsonPath("$.viewerRequest").doesNotExist());
        api.get(ravi, adventure)
                .andExpect(jsonPath("$.viewerRequest.id").value(JsonPath.<String>read(response, "$.id")))
                .andExpect(jsonPath("$.viewerRequest.status").value("PENDING"));
    }

    @Test
    void theMessageIsOptional() throws Exception {
        api.requestToJoin(ravi, adventure, newKey(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void aRetryWithTheSameKeyReturnsTheSameRequest() throws Exception {
        String key = newKey();
        String first = api.requestToJoin(ravi, adventure, key, "Hello").andReturn().getResponse().getContentAsString();

        String replay = api.requestToJoin(ravi, adventure, key, "Hello")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(requestCount(adventure)).isEqualTo(1);
        // The same key for something else is refused.
        api.requestToJoin(ravi, adventure, key, "Something else")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void aSecondLiveRequestFromTheSameTravellerIsRefused() throws Exception {
        String first = api.joinRequestId(ravi, adventure);

        api.requestToJoin(ravi, adventure, newKey(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_EXISTS"));
        // Also once accepted.
        api.accept(asha, first).andExpect(status().isOk());
        api.requestToJoin(ravi, adventure, newKey(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_EXISTS"));
        assertThat(requestCount(adventure)).isEqualTo(1);
    }

    @Test
    void whoCannotAskToJoin() throws Exception {
        api.requestToJoin(asha, adventure, newKey(), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANNOT_JOIN_OWN_ADVENTURE"));
        mvc.perform(post("/adventures/" + adventure + "/join-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.unverifiedUser(UUID.randomUUID()))
                        .header("Idempotency-Key", newKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_VERIFIED"));
        api.requestToJoin(ravi, UUID.randomUUID().toString(), newKey(), null).andExpect(status().isNotFound());

        assertThat(requestCount(adventure)).isZero();
    }

    @Test
    void aFullCancelledOrStartedTripTakesNoRequests() throws Exception {
        String full = api.createdId(asha, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "One seat", 1));
        api.accept(asha, api.joinRequestId(meera, full)).andExpect(status().isOk());
        api.requestToJoin(ravi, full, newKey(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_FULL"));

        String cancelled = api.createdId(asha, body(JAIPUR, "Cancelled"));
        api.cancel(asha, cancelled).andExpect(status().isNoContent());
        api.requestToJoin(ravi, cancelled, newKey(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_NOT_OPEN"));

        String started = api.createdId(asha, body(JAIPUR, "Started"));
        jdbc.update("update adventures set start_date = current_date - 5, end_date = current_date + 5 where id = ?",
                UUID.fromString(started));
        api.requestToJoin(ravi, started, newKey(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_NOT_OPEN"));
    }

    // ---- accept ----

    @Test
    void acceptingTakesASeatAndMakesTheTravellerAMember() throws Exception {
        String request = api.joinRequestId(ravi, adventure);

        api.accept(asha, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        api.get(ravi, adventure)
                .andExpect(jsonPath("$.seatsTaken").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.interestedCount").value(0))
                .andExpect(jsonPath("$.viewerRequest.status").value("ACCEPTED"));
        api.members(ravi, adventure)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId").value(contains(asha.toString(), ravi.toString())))
                .andExpect(jsonPath("$[*].role").value(contains("ORGANIZER", "MEMBER")));
        // The trip now shows up among the traveller's joined trips, not the organizer's.
        mvc.perform(get("/adventures/mine").param("role", "joined").header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(ravi)))
                .andExpect(jsonPath("$[*].id").value(contains(adventure)));
        mvc.perform(get("/adventures/mine").param("role", "joined").header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(asha)))
                .andExpect(jsonPath("$", hasSize(0)));
        // Chat was told who is on the trip.
        assertThat(chat.callsFor(UUID.fromString(adventure)))
                .containsExactly(new RecordingMembershipNotifier.Call(UUID.fromString(adventure), List.of(asha, ravi)));
    }

    @Test
    void acceptingTwiceSucceedsAndTakesOneSeat() throws Exception {
        String request = api.joinRequestId(ravi, adventure);
        api.accept(asha, request).andExpect(status().isOk());

        api.accept(asha, request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(seatsTaken(adventure)).isEqualTo(1);
        assertThat(memberCount(adventure)).isEqualTo(2);
    }

    @Test
    void theLastSeatMakesTheTripFullAndTheNextAcceptFails() throws Exception {
        UUID third = UUID.randomUUID();
        String fromRavi = api.joinRequestId(ravi, adventure);
        String fromMeera = api.joinRequestId(meera, adventure);
        String fromThird = api.joinRequestId(third, adventure);

        api.accept(asha, fromRavi).andExpect(status().isOk());
        api.accept(asha, fromMeera).andExpect(status().isOk());
        api.accept(asha, fromThird)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_FULL"));

        api.get(asha, adventure)
                .andExpect(jsonPath("$.seatsTaken").value(2))
                .andExpect(jsonPath("$.status").value("FULL"))
                // The third request is still waiting; the organizer can raise the capacity.
                .andExpect(jsonPath("$.interestedCount").value(1));
        api.edit(asha, adventure, "\"0\"", "{\"capacity\": 3}").andExpect(jsonPath("$.status").value("OPEN"));
        api.accept(asha, fromThird).andExpect(status().isOk());
        api.get(asha, adventure).andExpect(jsonPath("$.seatsTaken").value(3)).andExpect(jsonPath("$.status").value("FULL"));
    }

    @Test
    void onlyTheOrganizerDecides() throws Exception {
        String request = api.joinRequestId(ravi, adventure);

        api.accept(ravi, request).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        api.accept(meera, request).andExpect(status().isForbidden());
        api.reject(ravi, request).andExpect(status().isForbidden());
        api.accept(asha, UUID.randomUUID().toString()).andExpect(status().isNotFound());

        assertThat(seatsTaken(adventure)).isZero();
    }

    @Test
    void whenChatIsDownTheSeatStaysTakenAndARetryFinishesTheJob() throws Exception {
        UUID adventureId = UUID.fromString(adventure);
        String request = api.joinRequestId(ravi, adventure);
        chat.failFor(adventureId);

        api.accept(asha, request)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CHAT_UNAVAILABLE"));
        // The database transaction had already committed.
        assertThat(seatsTaken(adventure)).isEqualTo(1);
        assertThat(chat.callsFor(adventureId)).isEmpty();

        chat.recoverFor(adventureId);
        api.accept(asha, request).andExpect(status().isOk());
        assertThat(seatsTaken(adventure)).isEqualTo(1);
        assertThat(chat.callsFor(adventureId)).hasSize(1);
    }

    // ---- reject, withdraw, expiry ----

    @Test
    void rejectWorksOnceAndIsThenIdempotent() throws Exception {
        String request = api.joinRequestId(ravi, adventure);

        api.reject(asha, request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        api.reject(asha, request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        // A decision is final.
        api.accept(asha, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        api.withdraw(ravi, request).andExpect(status().isConflict());
        assertThat(seatsTaken(adventure)).isZero();
        // But the traveller may ask again.
        api.requestToJoin(ravi, adventure, newKey(), "Second try").andExpect(status().isCreated());
    }

    @Test
    void withdrawWorksOnceAndIsThenIdempotent() throws Exception {
        String request = api.joinRequestId(ravi, adventure);

        api.withdraw(meera, request).andExpect(status().isForbidden());
        api.withdraw(asha, request).andExpect(status().isForbidden());
        api.withdraw(ravi, request).andExpect(status().isNoContent());
        api.withdraw(ravi, request).andExpect(status().isNoContent());

        api.accept(asha, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        api.get(ravi, adventure).andExpect(jsonPath("$.viewerRequest.status").value("WITHDRAWN"));
    }

    @Test
    void anAcceptedRequestCanNoLongerBeRejectedOrWithdrawn() throws Exception {
        String request = api.joinRequestId(ravi, adventure);
        api.accept(asha, request).andExpect(status().isOk());

        api.reject(asha, request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        api.withdraw(ravi, request).andExpect(status().isConflict());
        assertThat(seatsTaken(adventure)).isEqualTo(1);
    }

    @Test
    void aRequestTheOrganizerDidNotDecideInTimeIsExpired() throws Exception {
        String request = api.joinRequestId(ravi, adventure);
        jdbc.update("update join_requests set expires_at = now() - interval '1 minute' where id = ?", UUID.fromString(request));

        // Reported as expired although no job has touched the row yet.
        api.get(ravi, adventure)
                .andExpect(jsonPath("$.viewerRequest.status").value("EXPIRED"))
                .andExpect(jsonPath("$.interestedCount").value(0));
        api.accept(asha, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(seatsTaken(adventure)).isZero();

        // And it does not block a new request.
        api.requestToJoin(ravi, adventure, newKey(), null).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select status from join_requests where id = ?", String.class, UUID.fromString(request)))
                .isEqualTo("EXPIRED");
    }

    // ---- cancelling the adventure ----

    @Test
    void cancellingRejectsPendingRequestsAndKeepsAcceptedMembers() throws Exception {
        String accepted = api.joinRequestId(ravi, adventure);
        String pending = api.joinRequestId(meera, adventure);
        api.accept(asha, accepted).andExpect(status().isOk());

        api.cancel(asha, adventure).andExpect(status().isNoContent());

        assertThat(statusOf(pending)).isEqualTo("REJECTED");
        assertThat(statusOf(accepted)).isEqualTo("ACCEPTED");
        assertThat(memberCount(adventure)).isEqualTo(2);
        api.accept(asha, pending)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    // ---- lists ----

    @Test
    void theRequestsTabShowsIncomingAndOutgoing() throws Exception {
        String other = api.createdId(asha, body(MANALI, "Another trip of Asha's"));
        String onFirst = api.joinRequestId(ravi, adventure);
        String onOther = api.joinRequestId(ravi, other);
        String fromMeera = api.joinRequestId(meera, adventure);
        api.reject(asha, onOther).andExpect(status().isOk());

        // Incoming: everything on Asha's trips, newest first.
        list(asha, "incoming", null).andExpect(jsonPath("$[*].id").value(contains(fromMeera, onOther, onFirst)));
        list(asha, "incoming", "PENDING").andExpect(jsonPath("$[*].id").value(contains(fromMeera, onFirst)));
        list(asha, "incoming", "rejected").andExpect(jsonPath("$[*].id").value(contains(onOther)));
        // Outgoing: what Ravi asked for.
        list(ravi, "outgoing", null).andExpect(jsonPath("$[*].id").value(contains(onOther, onFirst)));
        list(ravi, "incoming", null).andExpect(jsonPath("$", hasSize(0)));
        // Nobody sees other people's requests here.
        list(meera, "outgoing", null).andExpect(jsonPath("$[*].id").value(contains(fromMeera)));

        // One adventure's requests, for its organizer only, oldest first.
        mvc.perform(get("/adventures/" + adventure + "/join-requests").header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(onFirst, fromMeera)));
        mvc.perform(get("/adventures/" + adventure + "/join-requests").header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(ravi)))
                .andExpect(status().isForbidden());
    }

    @Test
    void membersAreVisibleToMembersOnly() throws Exception {
        api.members(asha, adventure)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId").value(contains(asha.toString())))
                .andExpect(jsonPath("$[0].role").value("ORGANIZER"));
        // Asking to join does not make one a member.
        api.joinRequestId(ravi, adventure);
        api.members(ravi, adventure).andExpect(status().isForbidden());
        api.members(asha, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        api.members(asha, adventure).andExpect(jsonPath("$[*].userId").value(not(hasItem(ravi.toString()))));
    }

    private ResultActions list(UUID user, String direction, String status) throws Exception {
        var request = get("/join-requests").param("direction", direction).header(HttpHeaders.AUTHORIZATION, AdventureApi.bearer(user));
        if (status != null) {
            request.param("status", status);
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private int requestCount(String adventureId) {
        return jdbc.queryForObject("select count(*) from join_requests where adventure_id = ?", Integer.class, UUID.fromString(adventureId));
    }

    private int seatsTaken(String adventureId) {
        return jdbc.queryForObject("select seats_taken from adventures where id = ?", Integer.class, UUID.fromString(adventureId));
    }

    private int memberCount(String adventureId) {
        return jdbc.queryForObject("select count(*) from adventure_members where adventure_id = ?", Integer.class, UUID.fromString(adventureId));
    }

    private String statusOf(String requestId) {
        return jdbc.queryForObject("select status from join_requests where id = ?", String.class, UUID.fromString(requestId));
    }
}
