package com.aicaddy;

import com.aicaddy.ai.AiBrainManager;
import com.aicaddy.ai.mood.EmotionalEventDetector;
import com.aicaddy.registry.ModEntities;
import com.aicaddy.registry.ModItems;
import com.aicaddy.voice.VoskSttManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "aicaddy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static MinecraftServer SERVER_INSTANCE;

	@Override
	public void onInitialize() {
		LOGGER.info("AI Companion 'Arkadaş / Yoldaş' initialized.");
		ModEntities.register();
		ModItems.register();

		// Initialize STT manager asynchronously.
		com.aicaddy.voice.SttManager.initialize();

		// Initialize SQLite persistent player memory store.
		com.aicaddy.ai.memory.PlayerMemoryStore.init();

		// Register emotional event detector (tick-based game event listener).
		EmotionalEventDetector.register();

		// Cache server instance for in-game chat broadcasting.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SERVER_INSTANCE = server;
			LOGGER.info("✔ AI Caddy v2 server instance registered.");
		});

		// Graceful shutdown: close all per-player TTS executors
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			com.aicaddy.ai.session.PlayerSessionManager.shutdownAll();
		});

		// Register Player Activity Tracker events (P10.2 Layer 2)
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			com.aicaddy.ai.context.PlayerActivityTracker.recordBlockBreak(player.getUUID(), state.getBlock());
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClientSide() && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				net.minecraft.world.level.block.state.BlockState state = world.getBlockState(hitResult.getBlockPos());
				if (state.hasBlockEntity() || state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
					com.aicaddy.ai.context.PlayerActivityTracker.recordContainerOpen(player.getUUID());
				} else {
					com.aicaddy.ai.context.PlayerActivityTracker.recordBlockPlace(player.getUUID(), state.getBlock());
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide()) {
				com.aicaddy.ai.context.PlayerActivityTracker.recordCombatAttack(player.getUUID());
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		// Spawn companion entity on player join (P10.3 Layer 3)
		// Also pre-create PlayerSession to load memory cache early
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			com.aicaddy.ai.session.PlayerSessionManager.getSession(handler.player);
			com.aicaddy.entity.AiCompanionEntity.getOrCreateCompanion(handler.player);
		});

		// Clean up session and FSMs on player disconnect
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			com.aicaddy.entity.AiCompanionEntity.removeCompanion(handler.player);
			com.aicaddy.ai.session.PlayerSessionManager.removeSession(handler.player.getUUID());
			com.aicaddy.ai.mood.CompanionMoodFsm.removeFsm(handler.player.getUUID());
			com.aicaddy.entity.CompanionBehaviorFsm.removeFsm(handler.player.getUUID());
			com.aicaddy.ai.context.GamePhaseFsm.removeFsm(handler.player.getUUID());
			com.aicaddy.voice.ConversationFlowFsm.removeFsm(handler.player.getUUID());
		});

		// Register in-game chat commands
		com.aicaddy.command.ModCommands.register();
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
