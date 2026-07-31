package com.aicaddy.ai.fsm;

import java.util.Objects;

/**
 * Represents a transition rule in the state machine.
 *
 * @param fromState    Source state, or {@code null} to match ANY source state (wildcard).
 * @param toState      Target state (must not be null).
 * @param triggerEvent Event that triggers this transition, or {@code null} for automatic tick transitions.
 * @param guard        Guard condition check (defaults to {@link TransitionGuard#always()}).
 */
public record Transition<S extends Enum<S>, E extends Enum<E>, C>(
        S fromState,
        S toState,
        E triggerEvent,
        TransitionGuard<S, E, C> guard
) {
    public Transition {
        Objects.requireNonNull(toState, "toState cannot be null");
        if (guard == null) {
            guard = TransitionGuard.always();
        }
    }

    /**
     * Checks if this transition matches the current state, event, and guard condition.
     */
    public boolean matches(C context, S current, E event) {
        if (fromState != null && fromState != current) {
            return false;
        }
        if (!Objects.equals(triggerEvent, event)) {
            return false;
        }
        return guard.canTransition(context, current, toState, event);
    }
}
