package com.triplor.adventure.joining;

public enum JoinRequestStatus {
    /** Waiting for the organizer. */
    PENDING,
    /** The traveller is on the trip and holds a seat. */
    ACCEPTED,
    REJECTED,
    /** Taken back by the traveller before a decision. */
    WITHDRAWN,
    /** Not decided within 72 hours. */
    EXPIRED
}
