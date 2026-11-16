package com.triplor.adventure.joining;

import static com.triplor.adventure.AdventureApi.body;
import static com.triplor.adventure.AdventureApi.inDays;
import static com.triplor.adventure.AdventureApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The invariant this service exists to keep: a trip never has more accepted travellers than
 * seats, whatever arrives at the same moment. Every test here releases real threads together
 * against the real database.
 */
class SeatReservationConcurrencyTest extends AdventureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    AdventureApi api;
    UUID organizer;
    ExecutorService pool;

    @BeforeEach
    void setUp() {
        api = new AdventureApi(mvc);
        organizer = UUID.randomUUID();
        pool = Executors.newFixedThreadPool(50);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    /**
     * Five seats, fifty pending requests, fifty accepts at once. Repeated, because a race
     * that loses once in ten runs would pass a single run nine times out of ten.
     */
    @RepeatedTest(20)
    void fiftySimultaneousAcceptsFillExactlyFiveSeats() throws Exception {
        String adventure = api.createdId(organizer, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "Five seats", 5));
        List<String> requests = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            requests.add(api.joinRequestId(UUID.randomUUID(), adventure));
        }

        List<MockHttpServletResponse> responses = releaseTogether(requests.stream()
                .<Callable<MockHttpServletResponse>>map(request -> () -> api.accept(organizer, request).andReturn().getResponse())
                .toList());

        assertThat(responses).filteredOn(response -> response.getStatus() == 200).hasSize(5);
        assertThat(responses).filteredOn(response -> response.getStatus() == 409).hasSize(45)
                .allSatisfy(response -> assertThat(JsonPath.<String>read(response.getContentAsString(), "$.code"))
                        .isEqualTo("ADVENTURE_FULL"));

        Map<String, Object> row = jdbc.queryForMap(
                "select seats_taken, status from adventures where id = ?", UUID.fromString(adventure));
        assertThat(row.get("seats_taken")).isEqualTo(5);
        assertThat(row.get("status")).isEqualTo("FULL");
        assertThat(count("select count(*) from join_requests where adventure_id = ? and status = 'ACCEPTED'", adventure)).isEqualTo(5);
        assertThat(count("select count(*) from join_requests where adventure_id = ? and status = 'PENDING'", adventure)).isEqualTo(45);
        // Five members and the organizer.
        assertThat(count("select count(*) from adventure_members where adventure_id = ?", adventure)).isEqualTo(6);
    }

    @RepeatedTest(10)
    void twoSimultaneousAcceptsOfOneRequestTakeOneSeat() throws Exception {
        String adventure = api.createdId(organizer, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "Double accept", 3));
        String request = api.joinRequestId(UUID.randomUUID(), adventure);

        Callable<MockHttpServletResponse> accept = () -> api.accept(organizer, request).andReturn().getResponse();
        List<MockHttpServletResponse> responses = releaseTogether(List.of(accept, accept));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactly(200, 200);
        assertThat(count("select seats_taken from adventures where id = ?", adventure)).isEqualTo(1);
        assertThat(count("select count(*) from adventure_members where adventure_id = ?", adventure)).isEqualTo(2);
    }

    /** The last seat, wanted by two different requests at once: one gets it. */
    @RepeatedTest(10)
    void twoRequestsRacingForTheLastSeat() throws Exception {
        String adventure = api.createdId(organizer, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "One seat", 1));
        String first = api.joinRequestId(UUID.randomUUID(), adventure);
        String second = api.joinRequestId(UUID.randomUUID(), adventure);

        List<MockHttpServletResponse> responses = releaseTogether(List.of(
                () -> api.accept(organizer, first).andReturn().getResponse(),
                () -> api.accept(organizer, second).andReturn().getResponse()));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        assertThat(count("select seats_taken from adventures where id = ?", adventure)).isEqualTo(1);
    }

    /**
     * Cancel and accept on the same trip at the same moment, a hundred times. Both lock the
     * adventure row before any request row, so neither can deadlock the other, and whichever
     * goes first decides the outcome cleanly.
     */
    @Test
    void cancelAgainstAcceptNeverDeadlocksAndAlwaysEndsConsistent() throws Exception {
        int acceptedFirst = 0;
        for (int round = 0; round < 100; round++) {
            String adventure = api.createdId(organizer, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "Race " + round, 2));
            String request = api.joinRequestId(UUID.randomUUID(), adventure);

            List<MockHttpServletResponse> responses = releaseTogether(List.of(
                    () -> api.accept(organizer, request).andReturn().getResponse(),
                    () -> api.cancel(organizer, adventure).andReturn().getResponse()));
            int accept = responses.get(0).getStatus();
            int cancel = responses.get(1).getStatus();

            assertThat(cancel).as("cancel in round %d", round).isEqualTo(204);
            assertThat(accept).as("accept in round %d: %s", round, responses.get(0).getContentAsString()).isIn(200, 409);
            assertThat(jdbc.queryForObject("select status from adventures where id = ?", String.class, UUID.fromString(adventure)))
                    .isEqualTo("CANCELLED");
            String requestStatus = jdbc.queryForObject("select status from join_requests where id = ?", String.class, UUID.fromString(request));
            int seats = count("select seats_taken from adventures where id = ?", adventure);
            int members = count("select count(*) from adventure_members where adventure_id = ? and role = 'MEMBER'", adventure);
            if (accept == 200) {
                // The accept went first: the traveller is on the (now cancelled) trip.
                acceptedFirst++;
                assertThat(List.of(requestStatus, seats, members)).isEqualTo(List.of("ACCEPTED", 1, 1));
            } else {
                // The cancel went first and rejected the request.
                assertThat(List.of(requestStatus, seats, members)).isEqualTo(List.of("REJECTED", 0, 0));
            }
        }
        // Not an assertion on the race itself, only a record that both orders are possible.
        System.out.printf("cancel against accept: accept went first in %d of 100 rounds%n", acceptedFirst);
    }

    /** One traveller double-taps "join": two different keys, one live request. */
    @RepeatedTest(10)
    void twoSimultaneousRequestsFromOneTravellerLeaveOneRequest() throws Exception {
        String adventure = api.createdId(organizer, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\"]", "Double tap", 3));
        UUID traveller = UUID.randomUUID();

        Callable<MockHttpServletResponse> ask = () -> api.requestToJoin(traveller, adventure, newKey(), null).andReturn().getResponse();
        List<MockHttpServletResponse> responses = releaseTogether(List.of(ask, ask));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        assertThat(count("select count(*) from join_requests where adventure_id = ?", adventure)).isEqualTo(1);
    }

    /** Starts every task on its own thread, holds them all at a latch, then lets them go at once. */
    private <T> List<T> releaseTogether(List<Callable<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    private int count(String sql, String adventureId) {
        return jdbc.queryForObject(sql, Integer.class, UUID.fromString(adventureId));
    }
}
