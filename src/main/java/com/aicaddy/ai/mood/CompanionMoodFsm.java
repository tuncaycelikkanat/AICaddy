package com.aicaddy.ai.mood;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.debug.CompanionDebugLogger;
import com.aicaddy.ai.fsm.FsmState;
import com.aicaddy.ai.fsm.StateMachine;
import com.aicaddy.ai.fsm.TransitionGuard;
import com.aicaddy.ai.session.PlayerSessionManager;
import com.aicaddy.entity.AiCompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adım 2: CompanionMoodFsm — Formal FSM implementation for Kedi's emotions.
 * Enforces transition rules (e.g. no direct jumps from SAD/ANGRY to HAPPY/EXCITED without CALM bridge)
 * and automatically triggers in-game effects (badges, particles, HUD banners) on Enter/Exit hooks.
 */
public final class CompanionMoodFsm {

    private static final Map<UUID, StateMachine<CompanionMoodState, MoodTrigger, ServerPlayer>> USER_FSMS =
            new ConcurrentHashMap<>();

    private CompanionMoodFsm() {}

    /**
     * Retrieves or creates the emotional FSM for the given player UUID.
     */
    public static synchronized StateMachine<CompanionMoodState, MoodTrigger, ServerPlayer> getFsm(UUID playerUuid) {
        if (playerUuid == null) return null;
        return USER_FSMS.computeIfAbsent(playerUuid, uuid -> {
            String playerName = "Player-" + uuid.toString().substring(0, 6);
            PlayerSessionManager.PlayerSession session = PlayerSessionManager.getSessionIfExists(uuid);
            if (session != null && session.playerName != null) {
                playerName = session.playerName;
            }
            return buildFsm(playerName);
        });
    }

    /**
     * Clears cached FSM for a player (e.g. on disconnect).
     */
    public static synchronized void removeFsm(UUID playerUuid) {
        if (playerUuid != null) {
            USER_FSMS.remove(playerUuid);
        }
    }

    private static final TransitionGuard<CompanionMoodState, MoodTrigger, ServerPlayer> NO_DIRECT_POSITIVE_JUMP_GUARD =
            (context, from, to, event) -> {
                boolean isFromNegative = (from == CompanionMoodState.SAD ||
                                          from == CompanionMoodState.FRUSTRATED);
                boolean isToPositive = (to == CompanionMoodState.EXCITED ||
                                        to == CompanionMoodState.PROUD);
                if (isFromNegative && isToPositive) {
                    ExampleMod.LOGGER.debug("🛑 [MoodFsm] Guarded: Cannot jump directly {} -> {}. Must pass through CURIOUS.", from, to);
                    return false;
                }
                return true;
            };

    /**
     * Builds the StateMachine with all 8 mood states and enter hooks.
     */
    private static StateMachine<CompanionMoodState, MoodTrigger, ServerPlayer> buildFsm(String playerName) {
        StateMachine.Builder<CompanionMoodState, MoodTrigger, ServerPlayer> builder =
                StateMachine.builder("MoodFsm-" + playerName, CompanionMoodState.CURIOUS);

        for (CompanionMoodState state : CompanionMoodState.values()) {
            builder.state(new MoodFsmState(state));
        }

        return builder.build();
    }

    /**
     * Tries to transition the player's mood FSM to the target mood triggered by the given trigger.
     *
     * @return The actual resulting state after guard evaluation (may be unchanged if blocked by guard).
     */
    public static CompanionMoodState tryTransition(UUID playerUuid,
                                                   CompanionMoodState targetMood,
                                                   MoodTrigger trigger,
                                                   ServerPlayer player) {
        StateMachine<CompanionMoodState, MoodTrigger, ServerPlayer> fsm = getFsm(playerUuid);
        if (fsm == null || targetMood == null) return targetMood;

        CompanionMoodState current = fsm.getCurrentState();
        if (current == targetMood) {
            return current;
        }

        boolean canTransition = NO_DIRECT_POSITIVE_JUMP_GUARD.canTransition(player, current, targetMood, trigger);
        if (canTransition) {
            fsm.forceState(player, targetMood);
        } else {
            ExampleMod.LOGGER.info("🌉 [MoodFsm] Bridging transition {} -> CURIOUS before allowing positive mood.", current);
            fsm.forceState(player, CompanionMoodState.CURIOUS);
        }

        return fsm.getCurrentState();
    }

    /**
     * Internal State implementation for each CompanionMoodState enum.
     */
    private static class MoodFsmState implements FsmState<CompanionMoodState, MoodTrigger, ServerPlayer> {
        private final CompanionMoodState moodState;

        MoodFsmState(CompanionMoodState moodState) {
            this.moodState = moodState;
        }

        @Override
        public CompanionMoodState getId() {
            return moodState;
        }

        @Override
        public void onEnter(ServerPlayer player, CompanionMoodState fromState, MoodTrigger triggerEvent) {
            if (player == null) return;
            UUID uuid = player.getUUID();

            // 1. Update Companion cat name badge
            AiCompanionEntity.updateMoodBadge(player, moodState);

            // 2. Spawn emotion particles
            AiCompanionEntity.spawnEmotionParticles(player, moodState);

            // 3. Display client HUD banner (Action bar)
            player.displayClientMessage(
                    Component.literal(moodState.getActionBarNotification()),
                    true
            );

            // 4. Log debug & telemetry
            CompanionDebugLogger.logMoodChange(player, triggerEvent != null ? triggerEvent : MoodTrigger.PLAYER_IDLE, moodState);
        }

        @Override
        public void onExit(ServerPlayer player, CompanionMoodState toState, MoodTrigger triggerEvent) {
            // Clean exit hook for any temporary mood visual effects
        }
    }
}
