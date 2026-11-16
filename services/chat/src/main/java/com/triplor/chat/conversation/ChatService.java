package com.triplor.chat.conversation;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.triplor.chat.conversation.ChatDtos.ConversationResponse;
import com.triplor.chat.conversation.ChatDtos.LastMessageResponse;
import com.triplor.chat.conversation.ChatDtos.MessagePage;
import com.triplor.chat.conversation.ChatDtos.MessageResponse;
import com.triplor.common.error.ApiException;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    static final int DEFAULT_CONVERSATIONS = 20;
    static final int MAX_CONVERSATIONS = 50;
    static final int DEFAULT_MESSAGES = 30;
    static final int MAX_MESSAGES = 100;
    static final int PREVIEW_LENGTH = 80;

    /** The unread count shown is at most this; the client renders more as "99+". */
    static final int UNREAD_CAP = 99;

    /** A message that was sent, and whether this call created it or found it already stored. */
    public record Sent(MessageResponse message, boolean created) {
    }

    private final ChatStore store;
    private final Clock clock;

    public ChatService(ChatStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** The caller's conversations, most recently active first, each with its unread count. */
    public List<ConversationResponse> conversationsOf(String userId, Integer limit) {
        int pageSize = limit == null ? DEFAULT_CONVERSATIONS : Math.max(1, Math.min(limit, MAX_CONVERSATIONS));
        return store.findConversationsOf(userId, pageSize).stream()
                .map(conversation -> toResponse(conversation, userId))
                .toList();
    }

    public MessagePage messages(String conversationId, String userId, String before, Integer limit) {
        requireMember(conversationId, userId);
        if (before != null && !ObjectId.isValid(before)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "before is not a message id");
        }
        int pageSize = limit == null ? DEFAULT_MESSAGES : Math.max(1, Math.min(limit, MAX_MESSAGES));
        // One more than the page: if it comes back, there is older history.
        List<Message> found = store.findMessages(conversationId, before, pageSize + 1);
        boolean more = found.size() > pageSize;
        List<MessageResponse> page = found.stream().limit(pageSize).map(MessageResponse::of).toList();
        return new MessagePage(page, more ? page.getLast().id() : null);
    }

    /**
     * Sends a message, once per {@code clientMessageId}.
     *
     * <p>Two writes, in this order and without a transaction: the message, then the
     * conversation's copy of it. If the second is lost the message still exists and is in the
     * history; only the preview in the conversation list is behind, and the next message
     * brings it up to date. The other order could show a preview of a message that was never
     * stored.
     */
    public Sent send(String conversationId, String senderId, String clientMessageId, String body) {
        requireMember(conversationId, senderId);
        String text = body.trim();
        Message message = new Message(new ObjectId().toHexString(), conversationId, senderId, clientMessageId.trim(),
                Message.Type.TEXT, text, now());

        if (!store.insert(message)) {
            // A retry of a message that did get through. Return what was stored the first time.
            Message existing = store.findByClientMessageId(conversationId, senderId, message.clientMessageId())
                    .orElseThrow(() -> new IllegalStateException("Duplicate message reported but not found"));
            return new Sent(MessageResponse.of(existing), false);
        }
        store.recordLastMessage(message, preview(text));
        return new Sent(MessageResponse.of(message), true);
    }

    /** Records that the caller has read up to and including a message. Never moves backwards. */
    public void markRead(String conversationId, String userId, String lastReadMessageId) {
        requireMember(conversationId, userId);
        store.findMessage(conversationId, lastReadMessageId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "lastReadMessageId is not a message of this conversation"));
        store.advanceReadMarker(conversationId, userId, lastReadMessageId);
    }

    /** 404 if there is no such conversation, 403 if the caller is not in it. */
    private Conversation requireMember(String conversationId, String userId) {
        Conversation conversation = store.findConversation(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Conversation not found"));
        if (!conversation.hasMember(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not a member of this conversation");
        }
        return conversation;
    }

    private ConversationResponse toResponse(Conversation conversation, String userId) {
        String lastRead = store.findReadState(conversation.id(), userId).map(ReadState::lastReadMessageId).orElse(null);
        int unread = conversation.lastMessage() == null
                ? 0
                : store.countUnread(conversation.id(), userId, lastRead, UNREAD_CAP);
        Conversation.LastMessage last = conversation.lastMessage();
        return new ConversationResponse(
                conversation.id(),
                conversation.adventureId(),
                conversation.memberIds(),
                last == null ? null : new LastMessageResponse(last.id(), last.senderId(), last.preview(), last.sentAt()),
                conversation.lastMessageAt(),
                unread);
    }

    static String preview(String text) {
        String singleLine = text.replaceAll("\\s+", " ");
        return singleLine.length() <= PREVIEW_LENGTH ? singleLine : singleLine.substring(0, PREVIEW_LENGTH - 1) + "…";
    }

    /** MongoDB stores dates to the millisecond; truncate here so what is returned equals what is stored. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
