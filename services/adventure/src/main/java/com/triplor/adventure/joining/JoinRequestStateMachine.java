package com.triplor.adventure.joining;

import static com.triplor.adventure.joining.JoinRequestStatus.ACCEPTED;
import static com.triplor.adventure.joining.JoinRequestStatus.EXPIRED;
import static com.triplor.adventure.joining.JoinRequestStatus.PENDING;
import static com.triplor.adventure.joining.JoinRequestStatus.REJECTED;
import static com.triplor.adventure.joining.JoinRequestStatus.WITHDRAWN;

/**
 * The life of a join request, as a pure function from (current status, action) to what should
 * happen. Only a pending request can move; every status it can move to is final.
 *
 * <p>Repeating the action that produced the current status is a no-op, not an error. That is
 * what makes accept, reject and withdraw safe to retry without an idempotency key: the second
 * call finds the work done and reports success.
 */
public final class JoinRequestStateMachine {

    public enum Action {
        ACCEPT(ACCEPTED),
        REJECT(REJECTED),
        WITHDRAW(WITHDRAWN),
        EXPIRE(EXPIRED);

        private final JoinRequestStatus result;

        Action(JoinRequestStatus result) {
            this.result = result;
        }

        /** The status this action leaves a pending request in. */
        public JoinRequestStatus result() {
            return result;
        }
    }

    public enum Transition {
        /** The request is pending: carry out the action. */
        MOVE,
        /** The request is already in the status the action leads to: succeed without doing anything. */
        NO_OP,
        /** The action is not possible from this status: 409 INVALID_STATE_TRANSITION. */
        INVALID
    }

    private JoinRequestStateMachine() {
    }

    public static Transition on(JoinRequestStatus current, Action action) {
        if (current == PENDING) {
            return Transition.MOVE;
        }
        return current == action.result() ? Transition.NO_OP : Transition.INVALID;
    }
}
