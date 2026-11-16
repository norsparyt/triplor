package com.triplor.adventure.idempotency;

import static com.triplor.adventure.AdventureApi.body;
import static com.triplor.adventure.AdventureApi.inDays;
import static com.triplor.adventure.AdventureApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import com.triplor.adventure.AdventureApi;
import com.triplor.adventure.AdventureIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class IdempotencyIntegrationTest extends AdventureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    AdventureApi api;
    UUID asha;

    @BeforeEach
    void setUp() {
        api = new AdventureApi(mvc);
        asha = UUID.randomUUID();
    }

    @Test
    void aReplayReturnsTheIdenticalResponseAndCreatesNothing() throws Exception {
        String key = newKey();
        String body = body(JAIPUR, "Forts and food");

        MockHttpServletResponse first = api.create(asha, key, body)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse();
        MockHttpServletResponse replay = api.create(asha, key, body)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse();

        assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(replay.getHeader(HttpHeaders.LOCATION)).isEqualTo(first.getHeader(HttpHeaders.LOCATION));
        assertThat(replay.getHeader(HttpHeaders.ETAG)).isEqualTo(first.getHeader(HttpHeaders.ETAG));
        assertThat(adventuresOf(asha)).isEqualTo(1);
        assertThat(keysOf(asha)).isEqualTo(1);
    }

    @Test
    void aReplayReturnsTheFirstResponseNotTheCurrentState() throws Exception {
        String key = newKey();
        String body = body(JAIPUR, "Forts and food");
        String id = JsonPath.read(api.create(asha, key, body).andReturn().getResponse().getContentAsString(), "$.id");
        api.edit(asha, id, "\"0\"", "{\"capacity\": 9}").andExpect(status().isOk());

        // The retry answers "what happened when you first asked", which is what the client missed.
        api.create(asha, key, body)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.capacity").value(3));
    }

    @Test
    void theSameKeyWithADifferentRequestIsRefused() throws Exception {
        String key = newKey();
        api.create(asha, key, body(JAIPUR, "Forts and food")).andExpect(status().isCreated());

        api.create(asha, key, body(MANALI, "Snow and silence"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(adventuresOf(asha)).isEqualTo(1);
    }

    @Test
    void requestsThatMeanTheSameCountAsTheSameRequest() throws Exception {
        String key = newKey();
        api.create(asha, key, body(JAIPUR, inDays(30), inDays(34), "[\"CULTURE\", \"FOODIE\"]", "Forts and food", 3))
                .andExpect(status().isCreated());

        // Styles in another order, with a duplicate, and padded text: still the same request.
        api.create(asha, key,
                        body(JAIPUR, inDays(30), inDays(34), "[\"FOODIE\", \"CULTURE\", \"FOODIE\"]", "  Forts and food ", 3))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void keysBelongToOneUser() throws Exception {
        String key = newKey();
        UUID ravi = UUID.randomUUID();

        String ashas = api.createdWith(asha, key, body(JAIPUR, "Forts and food"));
        String ravis = api.createdWith(ravi, key, body(JAIPUR, "Forts and food"));

        assertThat(ashas).isNotEqualTo(ravis);
        assertThat(adventuresOf(ravi)).isEqualTo(1);
    }

    @Test
    void aFailedAttemptStoresNothingSoTheKeyCanBeUsedAgain() throws Exception {
        String key = newKey();
        api.create(asha, key, body(999, "No such place")).andExpect(status().isBadRequest());
        assertThat(keysOf(asha)).isZero();

        // Not IDEMPOTENCY_KEY_REUSED: the failed attempt left no trace.
        api.create(asha, key, body(JAIPUR, "Forts and food"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void simultaneousRequestsWithOneKeyCreateOneAdventure() throws Exception {
        int clients = 8;
        String key = newKey();
        String body = body(JAIPUR, "Forts and food");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<MockHttpServletResponse>> pending = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                pending.add(pool.submit(() -> {
                    start.await();
                    return api.create(asha, key, body).andReturn().getResponse();
                }));
            }
            start.countDown();

            Set<String> ids = new HashSet<>();
            int firstAttempts = 0;
            for (Future<MockHttpServletResponse> future : pending) {
                MockHttpServletResponse response = future.get();
                assertThat(response.getStatus()).isEqualTo(201);
                ids.add(JsonPath.read(response.getContentAsString(), "$.id"));
                if (response.getHeader("Idempotent-Replayed") == null) {
                    firstAttempts++;
                }
            }

            // Everyone got the same adventure; exactly one request actually created it.
            assertThat(ids).hasSize(1);
            assertThat(firstAttempts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(adventuresOf(asha)).isEqualTo(1);
        assertThat(keysOf(asha)).isEqualTo(1);
    }

    @Test
    void aKeyMustBeOfReasonableLength() throws Exception {
        api.create(asha, "short", body(JAIPUR, "Forts and food"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.create(asha, "k".repeat(129), body(JAIPUR, "Forts and food")).andExpect(status().isBadRequest());

        assertThat(adventuresOf(asha)).isZero();
    }

    private int adventuresOf(UUID userId) {
        return jdbc.queryForObject("select count(*) from adventures where organizer_id = ?", Integer.class, userId);
    }

    private int keysOf(UUID userId) {
        return jdbc.queryForObject("select count(*) from idempotency_keys where user_id = ?", Integer.class, userId);
    }
}
