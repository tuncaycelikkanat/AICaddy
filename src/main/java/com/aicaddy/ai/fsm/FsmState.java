package com.aicaddy.ai.fsm;

/**
 * Generic FSM State interface for AI Caddy v2.
 *
 * @param <S> Enum type representing State IDs
 * @param <E> Enum type representing Event / Trigger IDs
 * @param <C> Context type passed during state execution and transitions
 */
public interface FsmState<S extends Enum<S>, E extends Enum<E>, C> {

    /**
     * @return The unique enum ID of this state.
     */
    S getId();

    /**
     * Called when the state machine enters this state.
     *
     * @param context     The operational context
     * @param fromState   The previous state (may be null on initial start)
     * @param triggerEvent The event that caused the transition (may be null for automatic transitions)
     */
    default void onEnter(C context, S fromState, E triggerEvent) {}

    /**
     * Called when the state machine exits this state.
     *
     * @param context     The operational context
     * @param toState     The target state being transitioned to
     * @param triggerEvent The event that caused the transition
     */
    default void onExit(C context, S toState, E triggerEvent) {}

    /**
     * Called periodically (e.g. server tick or timer) while this state is active.
     *
     * @param context The operational context
     */
    default void onTick(C context) {}
}
