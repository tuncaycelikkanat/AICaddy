package com.aicaddy.ai.fsm;

import java.util.Objects;

/**
 * Guard condition that determines whether a transition is permitted.
 *
 * @param <S> Enum type representing State IDs
 * @param <E> Enum type representing Event IDs
 * @param <C> Context type
 */
@FunctionalInterface
public interface TransitionGuard<S extends Enum<S>, E extends Enum<E>, C> {

    boolean canTransition(C context, S fromState, S toState, E event);

    static <S extends Enum<S>, E extends Enum<E>, C> TransitionGuard<S, E, C> always() {
        return (context, from, to, event) -> true;
    }

    static <S extends Enum<S>, E extends Enum<E>, C> TransitionGuard<S, E, C> never() {
        return (context, from, to, event) -> false;
    }

    default TransitionGuard<S, E, C> and(TransitionGuard<S, E, C> other) {
        Objects.requireNonNull(other);
        return (context, from, to, event) ->
                this.canTransition(context, from, to, event) &&
                other.canTransition(context, from, to, event);
    }

    default TransitionGuard<S, E, C> or(TransitionGuard<S, E, C> other) {
        Objects.requireNonNull(other);
        return (context, from, to, event) ->
                this.canTransition(context, from, to, event) ||
                other.canTransition(context, from, to, event);
    }

    default TransitionGuard<S, E, C> not() {
        return (context, from, to, event) -> !this.canTransition(context, from, to, event);
    }
}
