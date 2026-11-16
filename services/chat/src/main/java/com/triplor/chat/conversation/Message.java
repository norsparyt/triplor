package com.triplor.chat.conversation;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * One message. Its id is a MongoDB ObjectId, which starts with a timestamp, so ids sort in
 * the order messages were created and the id doubles as the pagination cursor.
 *
 * <ul>
 *   <li>{@code conversation_history_idx} serves "the newest messages of this conversation
 *       before message X" directly from the index.
 *   <li>{@code client_message_idx} is unique: the same sender cannot store the same
 *       {@code clientMessageId} twice in a conversation. That is what makes sending idempotent.
 * </ul>
 */
@Document("messages")
@CompoundIndex(name = "conversation_history_idx", def = "{'conversationId': 1, '_id': -1}")
@CompoundIndex(name = "client_message_idx", def = "{'conversationId': 1, 'senderId': 1, 'clientMessageId': 1}", unique = true)
public record Message(
        @Id String id,
        @Field(targetType = FieldType.OBJECT_ID) String conversationId,
        String senderId,
        String clientMessageId,
        Type type,
        String body,
        Instant createdAt) {

    public enum Type {
        TEXT,
        /** Written by the service, for example "Ravi joined". Not used yet. */
        SYSTEM
    }
}
