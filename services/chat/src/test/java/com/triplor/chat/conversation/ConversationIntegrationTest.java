package com.triplor.chat.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import com.triplor.chat.ChatIntegrationTest;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class ConversationIntegrationTest extends ChatIntegrationTest {

    @Autowired
    MongoTemplate mongo;

    UUID asha;
    UUID ravi;
    UUID stranger;
    String conversation;

    @BeforeEach
    void setUp() throws Exception {
        asha = UUID.randomUUID();
        ravi = UUID.randomUUID();
        stranger = UUID.randomUUID();
        conversation = conversationOf(asha, ravi);
    }

    // ---- sending ----

    @Test
    void aMemberCanSendAMessage() throws Exception {
        send(asha, conversation, "client-id-0001", "  Hello Ravi  ")
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.conversationId").value(conversation))
                .andExpect(jsonPath("$.senderId").value(asha.toString()))
                .andExpect(jsonPath("$.clientMessageId").value("client-id-0001"))
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.body").value("Hello Ravi"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // The conversation carries a copy of it.
        conversations(ravi)
                .andExpect(jsonPath("$[0].lastMessage.senderId").value(asha.toString()))
                .andExpect(jsonPath("$[0].lastMessage.preview").value("Hello Ravi"));
    }

    @Test
    void aRepeatedClientMessageIdReturnsTheStoredMessageAndStoresNothing() throws Exception {
        String first = send(asha, conversation, "client-id-0002", "Sent once")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        // The retry of a request whose response was lost. Even a changed body does not create a second message.
        String retry = send(asha, conversation, "client-id-0002", "Sent once")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        send(asha, conversation, "client-id-0002", "Edited on retry")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Sent once"));

        assertThat(retry).isEqualTo(first);
        assertThat(messageCount(conversation)).isEqualTo(1);
    }

    @Test
    void theClientMessageIdIsPerSender() throws Exception {
        send(asha, conversation, "client-id-0003", "From Asha").andExpect(status().isCreated());
        send(ravi, conversation, "client-id-0003", "From Ravi").andExpect(status().isCreated());

        assertThat(messageCount(conversation)).isEqualTo(2);
    }

    @Test
    void twentySimultaneousSendsOfOneMessageStoreItOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Integer> sendIt = () -> {
                go.await();
                return send(asha, conversation, "client-id-race", "Only once").andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> pending = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                pending.add(pool.submit(sendIt));
            }
            go.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : pending) {
                statuses.add(future.get());
            }
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(19);
        } finally {
            pool.shutdownNow();
        }
        assertThat(messageCount(conversation)).isEqualTo(1);
    }

    @Test
    void aMessageNeedsAClientIdAndABodyOfAtMostTwoThousandCharacters() throws Exception {
        send(asha, conversation, "client-id-0004", "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(asha, conversation, "short", "Hello").andExpect(status().isBadRequest());
        send(asha, conversation, "client-id-0005", "x".repeat(2001)).andExpect(status().isBadRequest());
        send(asha, conversation, "client-id-0006", "x".repeat(2000)).andExpect(status().isCreated());

        assertThat(messageCount(conversation)).isEqualTo(1);
    }

    @Test
    void theLastMessagePreviewIsShortened() throws Exception {
        sent(asha, conversation, "word ".repeat(40));

        String preview = JsonPath.read(conversations(ravi).andReturn().getResponse().getContentAsString(), "$[0].lastMessage.preview");
        assertThat(preview).hasSize(80).endsWith("…");
    }

    // ---- who may do what ----

    @Test
    void onlyMembersCanReadSendOrMarkRead() throws Exception {
        String message = sent(asha, conversation, "Members only");

        history(stranger, conversation).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        send(stranger, conversation, "client-id-0007", "Let me in").andExpect(status().isForbidden());
        markRead(stranger, conversation, message).andExpect(status().isForbidden());
        conversations(stranger).andExpect(jsonPath("$", hasSize(0)));

        assertThat(messageCount(conversation)).isEqualTo(1);
    }

    @Test
    void anUnknownConversationIsNotFound() throws Exception {
        history(asha, new ObjectId().toHexString()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        history(asha, "not-an-object-id").andExpect(status().isNotFound());
        send(asha, new ObjectId().toHexString(), "client-id-0008", "Anyone?").andExpect(status().isNotFound());
    }

    @Test
    void everyEndpointNeedsAToken() throws Exception {
        mvc.perform(get("/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/conversations/" + conversation + "/messages")).andExpect(status().isUnauthorized());
    }

    // ---- history ----

    @Test
    void pagingThroughHistoryReturnsEachMessageOnceNewestFirst() throws Exception {
        List<String> sentIds = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            sentIds.add(sent(i % 2 == 0 ? asha : ravi, conversation, "Message " + i));
        }

        List<String> seen = new ArrayList<>();
        String before = null;
        int pages = 0;
        do {
            var request = get("/conversations/" + conversation + "/messages").param("limit", "10")
                    .header(HttpHeaders.AUTHORIZATION, bearer(asha));
            if (before != null) {
                request.param("before", before);
            }
            String page = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            seen.addAll(JsonPath.<List<String>>read(page, "$.items[*].id"));
            before = JsonPath.read(page, "$.nextBefore");
            pages++;
            if (pages == 1) {
                // A message arriving while the user scrolls back does not disturb the older pages.
                sent(ravi, conversation, "Arrived meanwhile");
            }
        } while (before != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).doesNotHaveDuplicates();
        assertThat(seen).isEqualTo(sentIds.reversed());
    }

    @Test
    void historyStartsWithTheNewestMessage() throws Exception {
        sent(asha, conversation, "First");
        sent(ravi, conversation, "Second");

        history(asha, conversation)
                .andExpect(jsonPath("$.items[*].body").value(contains("Second", "First")))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());
        mvc.perform(get("/conversations/" + conversation + "/messages").param("before", "nonsense")
                        .header(HttpHeaders.AUTHORIZATION, bearer(asha)))
                .andExpect(status().isBadRequest());
    }

    // ---- read markers and unread counts ----

    @Test
    void unreadCountsAreKeptPerMemberAndIgnoreOwnMessages() throws Exception {
        String first = sent(asha, conversation, "One");
        sent(asha, conversation, "Two");
        String third = sent(ravi, conversation, "Three");

        // Asha wrote two, Ravi one: each has the other's messages unread.
        conversations(asha).andExpect(jsonPath("$[0].unreadCount").value(1));
        conversations(ravi).andExpect(jsonPath("$[0].unreadCount").value(2));

        markRead(ravi, conversation, first).andExpect(status().isNoContent());
        conversations(ravi).andExpect(jsonPath("$[0].unreadCount").value(1));
        markRead(ravi, conversation, third).andExpect(status().isNoContent());
        conversations(ravi).andExpect(jsonPath("$[0].unreadCount").value(0));
        // Ravi reading does not change what Asha has read.
        conversations(asha).andExpect(jsonPath("$[0].unreadCount").value(1));
    }

    @Test
    void theReadMarkerNeverMovesBackwards() throws Exception {
        String first = sent(asha, conversation, "One");
        sent(asha, conversation, "Two");
        String third = sent(asha, conversation, "Three");

        markRead(ravi, conversation, third).andExpect(status().isNoContent());
        // A delayed request from another device, pointing at an older message.
        markRead(ravi, conversation, first).andExpect(status().isNoContent());

        conversations(ravi).andExpect(jsonPath("$[0].unreadCount").value(0));
        ReadState state = mongo.findOne(Query.query(where("userId").is(ravi.toString())), ReadState.class);
        assertThat(state.lastReadMessageId()).isEqualTo(third);
    }

    @Test
    void theReadMarkerMustPointAtAMessageOfThisConversation() throws Exception {
        String elsewhere = sent(asha, conversationOf(asha, ravi), "In another conversation");

        markRead(ravi, conversation, elsewhere).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        markRead(ravi, conversation, "nonsense").andExpect(status().isBadRequest());
    }

    @Test
    void theUnreadCountStopsAtNinetyNine() throws Exception {
        for (int i = 0; i < 105; i++) {
            sent(asha, conversation, "Message " + i);
        }

        conversations(ravi).andExpect(jsonPath("$[0].unreadCount").value(99));
    }

    // ---- the list ----

    @Test
    void conversationsAreListedMostRecentlyActiveFirst() throws Exception {
        UUID traveller = UUID.randomUUID();
        String older = conversationOf(traveller, asha);
        String newer = conversationOf(traveller, ravi);
        conversations(traveller).andExpect(jsonPath("$[*].id").value(contains(newer, older)));

        // A message brings the older conversation to the top.
        sent(asha, older, "Are you still coming?");

        conversations(traveller)
                .andExpect(jsonPath("$[*].id").value(contains(older, newer)))
                .andExpect(jsonPath("$[0].unreadCount").value(1))
                .andExpect(jsonPath("$[1].unreadCount").value(0));
    }

    private ResultActions history(UUID user, String conversationId) throws Exception {
        return mvc.perform(get("/conversations/" + conversationId + "/messages").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private long messageCount(String conversationId) {
        return mongo.count(Query.query(where("conversationId").is(new ObjectId(conversationId))), Message.class);
    }
}
