package com.aicaddy.voice;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.fsm.FsmState;
import com.aicaddy.ai.fsm.StateMachine;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adım 5: Formal FSM for Voice, AI generation, and TTS lifecycle.
 * Prevents race conditions (e.g. speaking while listening) and handles Barge-In (interruption).
 */
public final class ConversationFlowFsm {

    private static final Map<UUID, StateMachine<ConversationState, ConversationEvent, ServerPlayer>> FSMS =
            new ConcurrentHashMap<>();

    private ConversationFlowFsm() {}

    public static synchronized StateMachine<ConversationState, ConversationEvent, ServerPlayer> getFsm(UUID playerUuid) {
        if (playerUuid == null) return null;
        return FSMS.computeIfAbsent(playerUuid, uuid -> buildFsm(uuid));
    }

    public static synchronized void removeFsm(UUID playerUuid) {
        if (playerUuid != null) {
            FSMS.remove(playerUuid);
        }
    }

    private static StateMachine<ConversationState, ConversationEvent, ServerPlayer> buildFsm(UUID playerUuid) {
        StateMachine.Builder<ConversationState, ConversationEvent, ServerPlayer> builder =
                StateMachine.builder("ConvFsm-" + playerUuid.toString().substring(0, 8), ConversationState.IDLE);

        // 1. Register States
        for (ConversationState state : ConversationState.values()) {
            builder.state(new ConvFsmState(state));
        }

        // 2. Define Transitions
        builder.transition(ConversationState.IDLE, ConversationState.LISTENING, ConversationEvent.START_LISTENING)
               .transition(ConversationState.LISTENING, ConversationState.PROCESSING, ConversationEvent.SPEECH_RECOGNIZED)
               .transition(ConversationState.LISTENING, ConversationState.IDLE, ConversationEvent.SPEECH_ABORTED)
               .transition(ConversationState.PROCESSING, ConversationState.SPEAKING, ConversationEvent.AI_RESPONSE_READY)
               .transition(ConversationState.PROCESSING, ConversationState.IDLE, ConversationEvent.AI_ERROR)
               .transition(ConversationState.SPEAKING, ConversationState.IDLE, ConversationEvent.TTS_FINISHED)
               // Barge-in: Interrupt speaking when user starts talking
               .transition(ConversationState.SPEAKING, ConversationState.LISTENING, ConversationEvent.BARGE_IN);

        return builder.build();
    }

    public static boolean fireEvent(UUID playerUuid, ConversationEvent event) {
        StateMachine<ConversationState, ConversationEvent, ServerPlayer> fsm = getFsm(playerUuid);
        return fsm != null && fsm.fireEvent(null, event);
    }

    public static ConversationState getCurrentState(UUID playerUuid) {
        StateMachine<ConversationState, ConversationEvent, ServerPlayer> fsm = getFsm(playerUuid);
        return fsm != null ? fsm.getCurrentState() : ConversationState.IDLE;
    }

    private static class ConvFsmState implements FsmState<ConversationState, ConversationEvent, ServerPlayer> {
        private final ConversationState state;

        ConvFsmState(ConversationState state) {
            this.state = state;
        }

        @Override
        public ConversationState getId() {
            return state;
        }

        @Override
        public void onEnter(ServerPlayer player, ConversationState fromState, ConversationEvent triggerEvent) {
            switch (state) {
                case LISTENING:
                    if (triggerEvent == ConversationEvent.BARGE_IN) {
                        ExampleMod.LOGGER.info("🛑 [ConversationFsm] Barge-in detected: interrupted active TTS speech!");
                    } else {
                        ExampleMod.LOGGER.debug("🎤 [ConversationFsm] LISTENING (STT Active)");
                    }
                    break;
                case PROCESSING:
                    ExampleMod.LOGGER.debug("🧠 [ConversationFsm] PROCESSING (AI Generating response)");
                    break;
                case SPEAKING:
                    ExampleMod.LOGGER.debug("🔊 [ConversationFsm] SPEAKING (TTS Audio Playing - Muting STT Echo)");
                    break;
                case IDLE:
                default:
                    ExampleMod.LOGGER.debug("⏸️ [ConversationFsm] IDLE");
                    break;
            }
        }

        @Override
        public void onExit(ServerPlayer player, ConversationState toState, ConversationEvent triggerEvent) {
            if (state == ConversationState.SPEAKING) {
                ExampleMod.LOGGER.debug("🔊 [ConversationFsm] Exited SPEAKING (Unmuting STT)");
            }
        }
    }
}
