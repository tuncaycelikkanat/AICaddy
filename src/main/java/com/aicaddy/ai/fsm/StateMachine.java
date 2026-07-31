package com.aicaddy.ai.fsm;

import com.aicaddy.ExampleMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Thread-safe Generic Finite State Machine (FSM) engine.
 *
 * @param <S> Enum type representing State IDs
 * @param <E> Enum type representing Event IDs
 * @param <C> Context type passed during evaluations and hooks
 */
public class StateMachine<S extends Enum<S>, E extends Enum<E>, C> {

    private final String name;
    private final Map<S, FsmState<S, E, C>> stateHandlers = new ConcurrentHashMap<>();
    private final List<Transition<S, E, C>> transitions = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<S, S>> transitionListeners = new CopyOnWriteArrayList<>();

    private volatile S currentState;
    private volatile S previousState = null;

    private StateMachine(String name, S initialState) {
        this.name = Objects.requireNonNull(name, "StateMachine name cannot be null");
        this.currentState = Objects.requireNonNull(initialState, "Initial state cannot be null");
    }

    /**
     * Creates a new Builder for configuring a StateMachine.
     */
    public static <S extends Enum<S>, E extends Enum<E>, C> Builder<S, E, C> builder(String name, S initialState) {
        return new Builder<>(name, initialState);
    }

    public String getName() {
        return name;
    }

    public S getCurrentState() {
        return currentState;
    }

    public S getPreviousState() {
        return previousState;
    }

    /**
     * Registers a listener to be notified on state transitions (oldState, newState).
     */
    public void addTransitionListener(BiConsumer<S, S> listener) {
        if (listener != null) {
            transitionListeners.add(listener);
        }
    }

    /**
     * Fires an event into the state machine.
     * Evaluates registered transitions in order and executes the first matching transition.
     *
     * @param context The operational context
     * @param event   The event triggering the evaluation
     * @return true if a state transition occurred, false otherwise
     */
    public synchronized boolean fireEvent(C context, E event) {
        if (event == null) return false;

        for (Transition<S, E, C> transition : transitions) {
            if (transition.matches(context, currentState, event)) {
                return executeTransition(context, transition.toState(), event);
            }
        }
        return false;
    }

    /**
     * Executes regular tick logic on the active state and evaluates automatic transitions
     * (transitions where triggerEvent == null).
     *
     * @param context The operational context
     * @return true if an automatic state transition occurred during tick
     */
    public synchronized boolean tick(C context) {
        FsmState<S, E, C> handler = stateHandlers.get(currentState);
        if (handler != null) {
            try {
                handler.onTick(context);
            } catch (Exception ex) {
                ExampleMod.LOGGER.error("[FSM - {}] Error in onTick for state {}", name, currentState, ex);
            }
        }

        // Evaluate automatic transitions (where triggerEvent is null)
        for (Transition<S, E, C> transition : transitions) {
            if (transition.triggerEvent() == null && transition.matches(context, currentState, null)) {
                return executeTransition(context, transition.toState(), null);
            }
        }
        return false;
    }

    /**
     * Forces an immediate transition to the target state without checking guards.
     */
    public synchronized void forceState(C context, S targetState) {
        if (targetState == null || targetState == currentState) return;
        executeTransition(context, targetState, null);
    }

    private boolean executeTransition(C context, S nextState, E triggerEvent) {
        if (nextState == currentState) {
            return false;
        }

        S oldState = this.currentState;
        FsmState<S, E, C> oldHandler = stateHandlers.get(oldState);
        if (oldHandler != null) {
            try {
                oldHandler.onExit(context, nextState, triggerEvent);
            } catch (Exception ex) {
                ExampleMod.LOGGER.error("[FSM - {}] Error in onExit for state {}", name, oldState, ex);
            }
        }

        this.previousState = oldState;
        this.currentState = nextState;

        FsmState<S, E, C> newHandler = stateHandlers.get(nextState);
        if (newHandler != null) {
            try {
                newHandler.onEnter(context, oldState, triggerEvent);
            } catch (Exception ex) {
                ExampleMod.LOGGER.error("[FSM - {}] Error in onEnter for state {}", name, nextState, ex);
            }
        }

        for (BiConsumer<S, S> listener : transitionListeners) {
            try {
                listener.accept(oldState, nextState);
            } catch (Exception ex) {
                ExampleMod.LOGGER.error("[FSM - {}] Error notifying transition listener", name, ex);
            }
        }

        ExampleMod.LOGGER.info("🔄 [FSM - {}] Transitioned: {} -> {} (event: {})",
                name, oldState, nextState, triggerEvent != null ? triggerEvent : "AUTO");
        return true;
    }

    // ─── Builder ─────────────────────────────────────────────────────────────

    public static class Builder<S extends Enum<S>, E extends Enum<E>, C> {
        private final StateMachine<S, E, C> fsm;

        private Builder(String name, S initialState) {
            this.fsm = new StateMachine<>(name, initialState);
        }

        public Builder<S, E, C> state(FsmState<S, E, C> state) {
            Objects.requireNonNull(state, "State handler cannot be null");
            fsm.stateHandlers.put(state.getId(), state);
            return this;
        }

        public Builder<S, E, C> transition(S from, S to, E event, TransitionGuard<S, E, C> guard) {
            fsm.transitions.add(new Transition<>(from, to, event, guard));
            return this;
        }

        public Builder<S, E, C> transition(S from, S to, E event) {
            return transition(from, to, event, TransitionGuard.always());
        }

        public Builder<S, E, C> autoTransition(S from, S to, TransitionGuard<S, E, C> guard) {
            return transition(from, to, null, guard);
        }

        public Builder<S, E, C> anyTransition(S to, E event, TransitionGuard<S, E, C> guard) {
            return transition(null, to, event, guard);
        }

        public Builder<S, E, C> anyTransition(S to, E event) {
            return anyTransition(to, event, TransitionGuard.always());
        }

        public Builder<S, E, C> onTransition(BiConsumer<S, S> listener) {
            fsm.addTransitionListener(listener);
            return this;
        }

        public StateMachine<S, E, C> build() {
            return fsm;
        }
    }
}
