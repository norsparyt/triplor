package com.triplor.chat.conversation;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** How far one member has read in one conversation: the id of the last message they have seen. */
@Document("read_states")
@CompoundIndex(name = "conversation_user_idx", def = "{'conversationId': 1, 'userId': 1}", unique = true)
public record ReadState(
        @Id String id,
        @Field(targetType = FieldType.OBJECT_ID) String conversationId,
        String userId,
        @Field(targetType = FieldType.OBJECT_ID) String lastReadMessageId) {
}
