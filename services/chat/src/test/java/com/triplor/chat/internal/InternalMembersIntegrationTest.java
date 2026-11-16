package com.triplor.chat.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.triplor.chat.ChatIntegrationTest;
import com.triplor.chat.conversation.Conversation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalMembersIntegrationTest extends ChatIntegrationTest {

    @Autowired
    MongoTemplate mongo;

    final UUID adventure = UUID.randomUUID();
    final UUID organizer = UUID.randomUUID();
    final UUID ravi = UUID.randomUUID();
    final UUID meera = UUID.randomUUID();

    @Test
    void theFirstCallCreatesTheConversationWithItsMembers() throws Exception {
        putMembers(adventure, INTERNAL_TOKEN, organizer, ravi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.adventureId").value(adventure.toString()))
                .andExpect(jsonPath("$.memberIds").value(contains(organizer.toString(), ravi.toString())));

        // Both members now see it, empty and with nothing unread.
        conversations(ravi)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].adventureId").value(adventure.toString()))
                .andExpect(jsonPath("$[0].lastMessage").doesNotExist())
                .andExpect(jsonPath("$[0].unreadCount").value(0));
    }

    @Test
    void repeatingTheCallChangesNothingAndALaterCallAddsTheNewMember() throws Exception {
        String first = JsonPath.read(putMembers(adventure, INTERNAL_TOKEN, organizer, ravi)
                .andReturn().getResponse().getContentAsString(), "$.conversationId");

        // The same accept retried.
        putMembers(adventure, INTERNAL_TOKEN, organizer, ravi)
                .andExpect(jsonPath("$.conversationId").value(first))
                .andExpect(jsonPath("$.memberIds", hasSize(2)));
        // A second traveller accepted on the same trip.
        putMembers(adventure, INTERNAL_TOKEN, organizer, meera)
                .andExpect(jsonPath("$.conversationId").value(first))
                .andExpect(jsonPath("$.memberIds").value(contains(organizer.toString(), ravi.toString(), meera.toString())));

        assertThat(conversationCount(adventure)).isEqualTo(1);
    }

    @Test
    void simultaneousFirstCallsCreateOneConversation() throws Exception {
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> pending = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                UUID member = UUID.randomUUID();
                pending.add(pool.submit(() -> {
                    go.await();
                    return putMembers(adventure, INTERNAL_TOKEN, organizer, member).andReturn().getResponse();
                }));
            }
            go.countDown();

            Set<String> conversationIds = new HashSet<>();
            for (Future<MockHttpServletResponse> future : pending) {
                MockHttpServletResponse response = future.get();
                assertThat(response.getStatus()).isEqualTo(200);
                conversationIds.add(JsonPath.read(response.getContentAsString(), "$.conversationId"));
            }
            assertThat(conversationIds).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(conversationCount(adventure)).isEqualTo(1);
        // The organizer once, and each of the eight members.
        Conversation stored = mongo.findOne(Query.query(where("adventureId").is(adventure.toString())), Conversation.class);
        assertThat(stored.memberIds()).hasSize(callers + 1).doesNotHaveDuplicates();
    }

    @Test
    void theCallIsRefusedWithoutTheSharedSecret() throws Exception {
        putMembers(adventure, null, organizer, ravi)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        putMembers(adventure, "a-wrong-guess", organizer, ravi).andExpect(status().isForbidden());
        // A user's token is not the secret.
        mvc.perform(put("/internal/conversations/" + adventure + "/members")
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\": [\"" + organizer + "\"]}"))
                .andExpect(status().isForbidden());

        assertThat(conversationCount(adventure)).isZero();
    }

    @Test
    void theBodyMustListMembers() throws Exception {
        mvc.perform(put("/internal/conversations/" + adventure + "/members")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private long conversationCount(UUID adventureId) {
        return mongo.count(Query.query(where("adventureId").is(adventureId.toString())), Conversation.class);
    }
}
