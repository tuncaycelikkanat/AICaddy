package com.aicaddy.entity;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.context.ThreatAssessor;
import com.aicaddy.ai.fsm.FsmState;
import com.aicaddy.ai.fsm.StateMachine;
import com.aicaddy.ai.fsm.TransitionGuard;
import com.aicaddy.ai.mood.CompanionMoodState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Cat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adım 3: Formal FSM implementation for Kedi's physical in-game behaviors (P10.3).
 * Manages transitions between FOLLOW_PLAYER, SIT_AND_WAIT, FLEE_DANGER, CELEBRATE, and INSPECT_BLOCK.
 */
public final class CompanionBehaviorFsm {

    private static final Map<UUID, StateMachine<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer>> FSMS =
            new ConcurrentHashMap<>();

    private CompanionBehaviorFsm() {}

    public static synchronized StateMachine<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer> getFsm(ServerPlayer player) {
        if (player == null) return null;
        return FSMS.computeIfAbsent(player.getUUID(), uuid -> buildFsm(player));
    }

    public static synchronized void removeFsm(UUID playerUuid) {
        if (playerUuid != null) {
            FSMS.remove(playerUuid);
        }
    }

    private static StateMachine<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer> buildFsm(ServerPlayer player) {
        StateMachine.Builder<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer> builder =
                StateMachine.builder("BehaviorFsm-" + player.getScoreboardName(), CompanionBehaviorState.FOLLOW_PLAYER);

        // 1. Register States with Enter/Exit behavior hooks
        for (CompanionBehaviorState state : CompanionBehaviorState.values()) {
            builder.state(new BehaviorFsmState(state));
        }

        // 2. Define Guards
        // Guard: Do not flee from SIT_AND_WAIT unless threat is CRITICAL (score >= 0.85)
        TransitionGuard<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer> sitFleeGuard =
                (context, from, to, event) -> {
                    if (from == CompanionBehaviorState.SIT_AND_WAIT && to == CompanionBehaviorState.FLEE_DANGER) {
                        return event == CompanionBehaviorEvent.THREAT_CRITICAL;
                    }
                    return true;
                };

        // 3. Define Transitions
        builder.anyTransition(CompanionBehaviorState.SIT_AND_WAIT, CompanionBehaviorEvent.COMMAND_SIT)
               .anyTransition(CompanionBehaviorState.FOLLOW_PLAYER, CompanionBehaviorEvent.COMMAND_FOLLOW)
               .anyTransition(CompanionBehaviorState.FLEE_DANGER, CompanionBehaviorEvent.THREAT_HIGH, sitFleeGuard)
               .anyTransition(CompanionBehaviorState.FLEE_DANGER, CompanionBehaviorEvent.THREAT_CRITICAL, sitFleeGuard)
               .anyTransition(CompanionBehaviorState.CELEBRATE, CompanionBehaviorEvent.PLAYER_ACHIEVEMENT,
                       (c, from, to, e) -> from != CompanionBehaviorState.FLEE_DANGER)
               .transition(CompanionBehaviorState.FLEE_DANGER, CompanionBehaviorState.FOLLOW_PLAYER, CompanionBehaviorEvent.THREAT_CLEAR)
               .transition(CompanionBehaviorState.CELEBRATE, CompanionBehaviorState.FOLLOW_PLAYER, CompanionBehaviorEvent.TASK_COMPLETE)
               .transition(CompanionBehaviorState.INSPECT_BLOCK, CompanionBehaviorState.FOLLOW_PLAYER, CompanionBehaviorEvent.TASK_COMPLETE);

        return builder.build();
    }

    public static boolean fireEvent(ServerPlayer player, CompanionBehaviorEvent event) {
        StateMachine<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer> fsm = getFsm(player);
        return fsm != null && fsm.fireEvent(player, event);
    }

    public static CompanionBehaviorState getCurrentState(ServerPlayer player) {
        StateMachine<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer> fsm = getFsm(player);
        return fsm != null ? fsm.getCurrentState() : CompanionBehaviorState.FOLLOW_PLAYER;
    }

    private static class BehaviorFsmState implements FsmState<CompanionBehaviorState, CompanionBehaviorEvent, ServerPlayer> {
        private final CompanionBehaviorState state;

        BehaviorFsmState(CompanionBehaviorState state) {
            this.state = state;
        }

        @Override
        public CompanionBehaviorState getId() {
            return state;
        }

        @Override
        public void onEnter(ServerPlayer player, CompanionBehaviorState fromState, CompanionBehaviorEvent triggerEvent) {
            Cat cat = AiCompanionEntity.getOrCreateCompanion(player);
            if (cat == null) return;

            switch (state) {
                case SIT_AND_WAIT:
                    cat.setOrderedToSit(true);
                    ExampleMod.LOGGER.info("🤝 [BehaviorFsm] Yoldaş oturdu (SIT_AND_WAIT)");
                    break;

                case FOLLOW_PLAYER:
                    cat.setOrderedToSit(false);
                    cat.getNavigation().moveTo(player, 1.35D);
                    ExampleMod.LOGGER.info("🤝 [BehaviorFsm] Yoldaş takipte (FOLLOW_PLAYER)");
                    break;

                case FLEE_DANGER:
                    cat.setOrderedToSit(false);
                    net.minecraft.world.phys.Vec3 fleePos = AiCompanionEntity.calculateSmartFleePosition(player, cat);
                    cat.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.6D);
                    if (cat.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, cat.getX(), cat.getY() + 0.8, cat.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
                    }
                    ExampleMod.LOGGER.info("🤝 [BehaviorFsm] Yoldaş tehlikeden kaçıyor (FLEE_DANGER -> {})", fleePos);
                    break;

                case CELEBRATE:
                    cat.setOrderedToSit(false);
                    cat.setDeltaMovement(0, 0.45, 0); // Joy jump
                    AiCompanionEntity.spawnEmotionParticles(player, CompanionMoodState.EXCITED);
                    ExampleMod.LOGGER.info("🤝 [BehaviorFsm] Yoldaş başarıyı kutluyor (CELEBRATE)");
                    break;

                case INSPECT_BLOCK:
                case IDLE:
                default:
                    break;
            }
        }

        @Override
        public void onExit(ServerPlayer player, CompanionBehaviorState toState, CompanionBehaviorEvent triggerEvent) {
            // Cleanup on state transition if needed
        }
    }
}
