package com.triplor.chat.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.triplor.chat.conversation.ChatStore;
import com.triplor.chat.conversation.Conversation;
import com.triplor.common.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoint the adventure service calls when someone is accepted on a trip.
 *
 * <p>Temporary. It is a synchronous call between two services, which ties accepting a
 * traveller to chat being up; Phase 2 replaces it with an event and deletes this class. Until
 * then it is protected by a secret both services are configured with, not by a user token:
 * the caller is a service, not a person.
 */
@RestController
public class InternalMembersController {

    public static final String TOKEN_HEADER = "X-Internal-Token";

    public record MembersRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> memberIds) {
    }

    public record MembersResponse(String conversationId, String adventureId, List<String> memberIds) {
    }

    private final ChatStore store;
    private final Clock clock;
    private final byte[] expectedToken;

    public InternalMembersController(ChatStore store, Clock clock, @Value("${triplor.internal.token:}") String token) {
        this.store = store;
        this.clock = clock;
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    /** Creates the adventure's conversation if needed and adds the members. Safe to repeat. */
    @PutMapping("/internal/conversations/{adventureId}/members")
    MembersResponse putMembers(@PathVariable UUID adventureId,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody MembersRequest request) {
        requireInternalCaller(token);
        Conversation conversation = store.ensureMembers(
                adventureId.toString(),
                request.memberIds().stream().map(UUID::toString).toList(),
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
        return new MembersResponse(conversation.id(), conversation.adventureId(), conversation.memberIds());
    }

    /**
     * Compares in constant time, so the response time does not reveal how many leading
     * characters of a guess were right. An unconfigured (empty) secret matches nothing.
     */
    private void requireInternalCaller(String token) {
        boolean configured = expectedToken.length > 0;
        boolean matches = token != null && MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
        if (!configured || !matches) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This endpoint is for internal callers");
        }
    }
}
