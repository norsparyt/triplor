package com.triplor.chat.conversation;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The conversation of one adventure. There is exactly one per adventure (the unique index),
 * and a two-person trip is simply a conversation with two members.
 *
 * <p>The latest message is copied into the document as {@code lastMessage}, so the list of
 * conversations is a single query with no join. The compound index serves that list:
 * "conversations I am in, most recently active first". An index on an array field has one
 * entry per element, so {@code memberIds: 1} finds a member directly.
 *
 * <p>User and adventure ids are stored as strings, not as BSON UUIDs, which avoids the
 * question of which binary UUID encoding a driver uses.
 */
@Document("conversations")
@CompoundIndex(name = "member_recent_idx", def = "{'memberIds': 1, 'lastMessageAt': -1}")
public record Conversation(
        @Id String id,
        @Indexed(unique = true) String adventureId,
        List<String> memberIds,
        LastMessage lastMessage,
        /** When the last message was sent, or when the conversation was created if it has none. */
        Instant lastMessageAt,
        Instant createdAt) {

    public record LastMessage(String id, String senderId, String preview, Instant sentAt) {
    }

    public boolean hasMember(String userId) {
        return memberIds != null && memberIds.contains(userId);
    }
}
