package com.triplor.adventure.joining;

import static org.assertj.core.api.Assertions.assertThat;

import com.triplor.adventure.joining.JoinRequestStateMachine.Action;
import com.triplor.adventure.joining.JoinRequestStateMachine.Transition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JoinRequestStateMachineTest {

    /** Every status with every action: all twenty pairs. */
    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
            "PENDING,   ACCEPT,   MOVE",
            "PENDING,   REJECT,   MOVE",
            "PENDING,   WITHDRAW, MOVE",
            "PENDING,   EXPIRE,   MOVE",
            "ACCEPTED,  ACCEPT,   NO_OP",
            "ACCEPTED,  REJECT,   INVALID",
            "ACCEPTED,  WITHDRAW, INVALID",
            "ACCEPTED,  EXPIRE,   INVALID",
            "REJECTED,  ACCEPT,   INVALID",
            "REJECTED,  REJECT,   NO_OP",
            "REJECTED,  WITHDRAW, INVALID",
            "REJECTED,  EXPIRE,   INVALID",
            "WITHDRAWN, ACCEPT,   INVALID",
            "WITHDRAWN, REJECT,   INVALID",
            "WITHDRAWN, WITHDRAW, NO_OP",
            "WITHDRAWN, EXPIRE,   INVALID",
            "EXPIRED,   ACCEPT,   INVALID",
            "EXPIRED,   REJECT,   INVALID",
            "EXPIRED,   WITHDRAW, INVALID",
            "EXPIRED,   EXPIRE,   NO_OP",
    })
    void everyPairOfStatusAndAction(JoinRequestStatus status, Action action, Transition expected) {
        assertThat(JoinRequestStateMachine.on(status, action)).isEqualTo(expected);
    }

    @Test
    void theTableAboveCoversEveryPair() {
        assertThat(JoinRequestStatus.values().length * Action.values().length).isEqualTo(20);
    }

    @Test
    void eachActionLeadsToItsOwnStatus() {
        assertThat(Action.ACCEPT.result()).isEqualTo(JoinRequestStatus.ACCEPTED);
        assertThat(Action.REJECT.result()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(Action.WITHDRAW.result()).isEqualTo(JoinRequestStatus.WITHDRAWN);
        assertThat(Action.EXPIRE.result()).isEqualTo(JoinRequestStatus.EXPIRED);
    }
}
