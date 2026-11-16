package com.triplor.chat.conversation;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.triplor.chat.conversation.Conversation.LastMessage;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Every MongoDB operation of the chat service.
 *
 * <p>There are no multi-document transactions here. Each write is a single-document operation
 * that MongoDB applies atomically, and the operators ({@code $addToSet}, {@code $max},
 * {@code $setOnInsert}, the unique indexes) are chosen so that repeating a write or running
 * two at once still ends in the right state.
 */
@Repository
public class ChatStore {

    private final MongoTemplate mongo;

    public ChatStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ---- conversations ----

    /**
     * Makes sure the adventure has a conversation and that it contains these members.
     * One upsert: {@code $setOnInsert} applies only when the document is created,
     * {@code $addToSet} adds each member unless already present. Calling it again with the
     * same members changes nothing.
     */
    public Conversation ensureMembers(String adventureId, Collection<String> memberIds, Instant now) {
        Update update = new Update()
                .setOnInsert("createdAt", now)
                .setOnInsert("lastMessageAt", now)
                .addToSet("memberIds").each(memberIds.toArray());
        Query byAdventure = Query.query(where("adventureId").is(adventureId));
        try {
            return upsert(byAdventure, update);
        } catch (DuplicateKeyException e) {
            // Two first calls for the same adventure at once: both tried to insert, the unique
            // index let one through. The document exists now, so the retry is a plain update.
            return upsert(byAdventure, update);
        }
    }

    private Conversation upsert(Query query, Update update) {
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().upsert(true).returnNew(true), Conversation.class);
    }

    public Optional<Conversation> findConversation(String conversationId) {
        if (!ObjectId.isValid(conversationId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findById(conversationId, Conversation.class));
    }

    /** The user's conversations, most recently active first. */
    public List<Conversation> findConversationsOf(String userId, int limit) {
        Query query = Query.query(where("memberIds").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt"))
                .limit(limit);
        return mongo.find(query, Conversation.class);
    }

    /**
     * Copies a message into the conversation as its latest, unless a newer one is already
     * there. Two messages sent at the same moment may run this in either order; the condition
     * makes the newer one win both ways.
     */
    public void recordLastMessage(Message message, String preview) {
        Query olderOrEmpty = Query.query(where("_id").is(new ObjectId(message.conversationId()))
                .orOperator(where("lastMessage").isNull(), where("lastMessage._id").lt(new ObjectId(message.id()))));
        Update update = new Update()
                .set("lastMessage", new org.bson.Document()
                        .append("_id", new ObjectId(message.id()))
                        .append("senderId", message.senderId())
                        .append("preview", preview)
                        .append("sentAt", java.util.Date.from(message.createdAt())))
                .set("lastMessageAt", message.createdAt());
        mongo.updateFirst(olderOrEmpty, update, Conversation.class);
    }

    // ---- messages ----

    /**
     * Stores a message. Returns false if this sender has already stored a message with the
     * same {@code clientMessageId} in this conversation: the unique index refused the insert.
     */
    public boolean insert(Message message) {
        try {
            mongo.insert(message);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<Message> findByClientMessageId(String conversationId, String senderId, String clientMessageId) {
        Query query = Query.query(where("conversationId").is(new ObjectId(conversationId))
                .and("senderId").is(senderId)
                .and("clientMessageId").is(clientMessageId));
        return Optional.ofNullable(mongo.findOne(query, Message.class));
    }

    public Optional<Message> findMessage(String conversationId, String messageId) {
        if (!ObjectId.isValid(messageId)) {
            return Optional.empty();
        }
        Query query = Query.query(where("_id").is(new ObjectId(messageId))
                .and("conversationId").is(new ObjectId(conversationId)));
        return Optional.ofNullable(mongo.findOne(query, Message.class));
    }

    /**
     * Messages of a conversation, newest first, optionally only those before a given message.
     * Keyset pagination on the id: no skipping, and new messages arriving while someone
     * scrolls back cannot shift the pages.
     */
    public List<Message> findMessages(String conversationId, String beforeMessageId, int limit) {
        Criteria criteria = where("conversationId").is(new ObjectId(conversationId));
        if (beforeMessageId != null) {
            criteria = criteria.and("_id").lt(new ObjectId(beforeMessageId));
        }
        return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "_id")).limit(limit), Message.class);
    }

    /**
     * How many messages from other people the user has not read, counting at most
     * {@code cap}: the client shows "99+" beyond that, so there is no point counting further.
     */
    public int countUnread(String conversationId, String userId, String lastReadMessageId, int cap) {
        Criteria criteria = where("conversationId").is(new ObjectId(conversationId)).and("senderId").ne(userId);
        if (lastReadMessageId != null) {
            criteria = criteria.and("_id").gt(new ObjectId(lastReadMessageId));
        }
        return (int) mongo.count(Query.query(criteria).limit(cap), Message.class);
    }

    // ---- read states ----

    public Optional<ReadState> findReadState(String conversationId, String userId) {
        return Optional.ofNullable(mongo.findOne(readStateOf(conversationId, userId), ReadState.class));
    }

    /**
     * Moves the user's read marker to this message, unless it is already further on.
     * {@code $max} only ever raises the stored value, so a late or repeated call with an older
     * message cannot move the marker backwards, and two calls at once end at the higher one.
     */
    public void advanceReadMarker(String conversationId, String userId, String messageId) {
        Update update = new Update().max("lastReadMessageId", new ObjectId(messageId));
        try {
            mongo.upsert(readStateOf(conversationId, userId), update, ReadState.class);
        } catch (DuplicateKeyException e) {
            mongo.upsert(readStateOf(conversationId, userId), update, ReadState.class);
        }
    }

    private static Query readStateOf(String conversationId, String userId) {
        return Query.query(where("conversationId").is(new ObjectId(conversationId)).and("userId").is(userId));
    }
}
