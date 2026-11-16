package com.triplor.chat.conversation;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChatDtos {

    static final int MAX_BODY_LENGTH = 2000;

    private ChatDtos() {
    }

    public record ConversationResponse(String id, String adventureId, List<String> memberIds,
            LastMessageResponse lastMessage, Instant lastMessageAt, int unreadCount) {
    }

    public record LastMessageResponse(String id, String senderId, String preview, Instant sentAt) {
    }

    public record MessageResponse(String id, String conversationId, String senderId, String clientMessageId,
            String type, String body, Instant createdAt) {

        static MessageResponse of(Message message) {
            return new MessageResponse(message.id(), message.conversationId(), message.senderId(),
                    message.clientMessageId(), message.type().name(), message.body(), message.createdAt());
        }
    }

    /** One page of history, newest first. Pass {@code nextBefore} as {@code before} to go further back. */
    public record MessagePage(List<MessageResponse> items, String nextBefore) {
    }

    /**
     * @param clientMessageId an id the client makes up for this message, for example a UUID.
     *        Sending the same id again returns the stored message instead of creating another.
     */
    public record SendMessageRequest(
            @NotBlank @Size(min = 8, max = 64) String clientMessageId,
            @NotBlank @Size(max = MAX_BODY_LENGTH) String body) {
    }

    public record MarkReadRequest(@NotBlank String lastReadMessageId) {
    }
}
