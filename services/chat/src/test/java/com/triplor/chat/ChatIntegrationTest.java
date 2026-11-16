package com.triplor.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import com.triplor.testsupport.MongoContainerConfig;
import com.triplor.testsupport.TestJwtDecoderConfig;
import com.triplor.testsupport.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base class for the chat service's integration tests: the whole application against a real
 * MongoDB in Docker, with callers authenticated by tokens from {@code TestTokens}.
 *
 * <p>All subclasses share one application context and one database. Every test makes its own
 * users and adventure ids, so tests do not see each other's conversations.
 */
@SpringBootTest(properties = "triplor.internal.token=" + ChatIntegrationTest.INTERNAL_TOKEN)
@AutoConfigureMockMvc
@Import({MongoContainerConfig.class, TestJwtDecoderConfig.class})
public abstract class ChatIntegrationTest {

    public static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    protected MockMvc mvc;

    protected static String bearer(UUID userId) {
        return "Bearer " + TestTokens.user(userId);
    }

    /** What the adventure service does on accept: put these members in the adventure's conversation. */
    protected ResultActions putMembers(UUID adventureId, String token, UUID... memberIds) throws Exception {
        String ids = Arrays.stream(memberIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(", "));
        var request = put("/internal/conversations/" + adventureId + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberIds\": [" + ids + "]}");
        if (token != null) {
            request.header("X-Internal-Token", token);
        }
        return mvc.perform(request);
    }

    /** Creates a conversation for a new adventure with these members and returns its id. */
    protected String conversationOf(UUID... memberIds) throws Exception {
        String response = putMembers(UUID.randomUUID(), INTERNAL_TOKEN, memberIds)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.conversationId");
    }

    protected ResultActions send(UUID sender, String conversationId, String clientMessageId, String body) throws Exception {
        return mvc.perform(post("/conversations/" + conversationId + "/messages")
                .header(HttpHeaders.AUTHORIZATION, bearer(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientMessageId\": \"" + clientMessageId + "\", \"body\": \"" + body + "\"}"));
    }

    /** Sends a message with a fresh client id and returns the stored message's id. */
    protected String sent(UUID sender, String conversationId, String body) throws Exception {
        String response = send(sender, conversationId, UUID.randomUUID().toString(), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    protected ResultActions conversations(UUID user) throws Exception {
        return mvc.perform(get("/conversations").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    protected ResultActions markRead(UUID user, String conversationId, String messageId) throws Exception {
        return mvc.perform(put("/conversations/" + conversationId + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastReadMessageId\": \"" + messageId + "\"}"));
    }
}
