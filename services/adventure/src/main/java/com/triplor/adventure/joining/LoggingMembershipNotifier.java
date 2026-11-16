package com.triplor.adventure.joining;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stands in until the chat service exists (M6): it only logs. */
@Component
class LoggingMembershipNotifier implements MembershipNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingMembershipNotifier.class);

    @Override
    public void membersAdded(UUID adventureId, List<UUID> memberIds) {
        log.info("Members {} are on adventure {} (no chat service to tell yet)", memberIds, adventureId);
    }
}
