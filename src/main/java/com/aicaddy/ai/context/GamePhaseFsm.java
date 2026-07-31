package com.aicaddy.ai.context;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.fsm.FsmState;
import com.aicaddy.ai.fsm.StateMachine;
import com.aicaddy.ai.fsm.TransitionGuard;
import com.aicaddy.ai.mood.CompanionMoodEngine;
import com.aicaddy.entity.AiCompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adım 4: Formal Progression FSM with Hysteresis (one-way forward advancement).
 * Prevents flickering if items are temporarily deposited in chests, and triggers
 * milestone celebration hooks on advancement.
 */
public final class GamePhaseFsm {

    private static final Map<UUID, StateMachine<GamePhaseAnalyzer.GamePhase, GamePhaseEvent, ServerPlayer>> FSMS =
            new ConcurrentHashMap<>();

    private GamePhaseFsm() {}

    public static synchronized StateMachine<GamePhaseAnalyzer.GamePhase, GamePhaseEvent, ServerPlayer> getFsm(ServerPlayer player) {
        if (player == null) return null;
        return FSMS.computeIfAbsent(player.getUUID(), uuid -> buildFsm(player));
    }

    public static synchronized void removeFsm(UUID playerUuid) {
        if (playerUuid != null) {
            FSMS.remove(playerUuid);
        }
    }

    private static StateMachine<GamePhaseAnalyzer.GamePhase, GamePhaseEvent, ServerPlayer> buildFsm(ServerPlayer player) {
        StateMachine.Builder<GamePhaseAnalyzer.GamePhase, GamePhaseEvent, ServerPlayer> builder =
                StateMachine.builder("GamePhaseFsm-" + player.getScoreboardName(), GamePhaseAnalyzer.GamePhase.EARLY);

        // 1. Register States with Enter hooks
        for (GamePhaseAnalyzer.GamePhase phase : GamePhaseAnalyzer.GamePhase.values()) {
            builder.state(new PhaseFsmState(phase));
        }

        // 2. Guard: Hysteresis — only allow forward progression (target ordinal > current ordinal) unless RESET_PHASE
        TransitionGuard<GamePhaseAnalyzer.GamePhase, GamePhaseEvent, ServerPlayer> forwardOnlyGuard =
                (context, from, to, event) -> {
                    if (event == GamePhaseEvent.RESET_PHASE) return true;
                    return to.ordinal() > from.ordinal();
                };

        // 3. Define forward transitions
        builder.transition(GamePhaseAnalyzer.GamePhase.EARLY, GamePhaseAnalyzer.GamePhase.MID,
                        GamePhaseEvent.ADVANCE_TO_MID, forwardOnlyGuard)
               .transition(GamePhaseAnalyzer.GamePhase.MID, GamePhaseAnalyzer.GamePhase.LATE,
                        GamePhaseEvent.ADVANCE_TO_LATE, forwardOnlyGuard)
               .transition(GamePhaseAnalyzer.GamePhase.LATE, GamePhaseAnalyzer.GamePhase.ENDGAME,
                        GamePhaseEvent.ADVANCE_TO_ENDGAME, forwardOnlyGuard)
               // Also allow jump directly from EARLY to LATE/ENDGAME if player skips MID
               .transition(GamePhaseAnalyzer.GamePhase.EARLY, GamePhaseAnalyzer.GamePhase.LATE,
                        GamePhaseEvent.ADVANCE_TO_LATE, forwardOnlyGuard)
               .transition(GamePhaseAnalyzer.GamePhase.EARLY, GamePhaseAnalyzer.GamePhase.ENDGAME,
                        GamePhaseEvent.ADVANCE_TO_ENDGAME, forwardOnlyGuard)
               .transition(GamePhaseAnalyzer.GamePhase.MID, GamePhaseAnalyzer.GamePhase.ENDGAME,
                        GamePhaseEvent.ADVANCE_TO_ENDGAME, forwardOnlyGuard);

        return builder.build();
    }

    /**
     * Updates the FSM with a newly scanned inventory phase and returns the hysteresis-protected active phase.
     */
    public static GamePhaseAnalyzer.GamePhase updateAndGetPhase(ServerPlayer player, GamePhaseAnalyzer.GamePhase scannedPhase) {
        StateMachine<GamePhaseAnalyzer.GamePhase, GamePhaseEvent, ServerPlayer> fsm = getFsm(player);
        if (fsm == null) return scannedPhase;

        GamePhaseAnalyzer.GamePhase current = fsm.getCurrentState();
        if (scannedPhase.ordinal() > current.ordinal()) {
            GamePhaseEvent event = switch (scannedPhase) {
                case MID -> GamePhaseEvent.ADVANCE_TO_MID;
                case LATE -> GamePhaseEvent.ADVANCE_TO_LATE;
                case ENDGAME -> GamePhaseEvent.ADVANCE_TO_ENDGAME;
                default -> null;
            };
            if (event != null) {
                fsm.fireEvent(player, event);
            }
        }

        return fsm.getCurrentState();
    }

    private static class PhaseFsmState implements FsmState<GamePhaseAnalyzer.GamePhase, GamePhaseEvent, ServerPlayer> {
        private final GamePhaseAnalyzer.GamePhase phase;

        PhaseFsmState(GamePhaseAnalyzer.GamePhase phase) {
            this.phase = phase;
        }

        @Override
        public GamePhaseAnalyzer.GamePhase getId() {
            return phase;
        }

        @Override
        public void onEnter(ServerPlayer player, GamePhaseAnalyzer.GamePhase fromState, GamePhaseEvent triggerEvent) {
            if (fromState != null && fromState.ordinal() < phase.ordinal()) {
                ExampleMod.LOGGER.info("🚀 [GamePhaseFsm] Player advanced from {} to {}!", fromState, phase);

                // Trigger physical companion celebration jump
                AiCompanionEntity.triggerCelebration(player);

                // Record milestone in emotional memory
                CompanionMoodEngine.recordEvent("Oyuncu " + phase.name() + " ilerleme fazına ulaştı!", player.getUUID());

                // Notify player in chat
                player.sendSystemMessage(
                        Component.literal("§6👑 [AI Caddy]: §fTebrikler! İlerleme fazı yükseldi: §e" + phase.name())
                );
            }
        }

        @Override
        public void onExit(ServerPlayer player, GamePhaseAnalyzer.GamePhase toState, GamePhaseEvent triggerEvent) {
            // Clean exit hook
        }
    }
}
