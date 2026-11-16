package com.triplor.adventure.joining;

import java.util.List;
import java.util.UUID;

/**
 * Tells the rest of the system that people are on a trip together, so that the chat service
 * can put them in the trip's conversation.
 *
 * <p>An interface, because how this is done changes: in Phase 1 it is a direct HTTP call to
 * chat, in Phase 2 an event written to an outbox table. The join-request logic does not change.
 *
 * <p>Implementations must be safe to call again with the same arguments, because accepting a
 * request twice notifies twice.
 */
public interface MembershipNotifier {

    /**
     * @param memberIds users who must be in the adventure's conversation
     * @throws MembershipNotificationException if the notification could not be delivered
     */
    void membersAdded(UUID adventureId, List<UUID> memberIds);

    class MembershipNotificationException extends RuntimeException {

        public MembershipNotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
