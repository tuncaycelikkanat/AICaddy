package com.example;

import com.example.ai.AiBrainManager;
import com.example.ai.mood.EmotionalEventDetector;
import com.example.registry.ModEntities;
import com.example.registry.ModItems;
import com.example.voice.VoskSttManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static MinecraftServer SERVER_INSTANCE;

	@Override
	public void onInitialize() {
		LOGGER.info("AI Companion 'Kedi' initialized.");
		ModEntities.register();
		ModItems.register();

		// Initialize Vosk STT engine asynchronously.
		VoskSttManager.initialize();

		// Initialize SQLite persistent player memory store.
		com.example.ai.memory.PlayerMemoryStore.init();

		// Register emotional event detector (tick-based game event listener).
		EmotionalEventDetector.register();

		// Cache server instance for in-game chat broadcasting.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SERVER_INSTANCE = server;
		});

		// Register in-game chat commands
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// /kedi <mesaj> — Talk to companion
			dispatcher.register(net.minecraft.commands.Commands.literal("kedi")
					.then(net.minecraft.commands.Commands.argument("mesaj", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
							.executes(context -> {
								String mesaj = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mesaj");
								net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
								ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
										net.minecraft.network.chat.Component.literal("§a💬 [" + player.getScoreboardName() + "]: §f" + mesaj),
										false
								);
								ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
										net.minecraft.network.chat.Component.literal("§7⏳ [Kedi düşünüyor...]"),
										false
								);
								AiBrainManager.processAndRespond(player, mesaj);
								return 1;
							}))
			);

			// /aikedi unut — Clear memory
			dispatcher.register(net.minecraft.commands.Commands.literal("aikedi")
					.then(net.minecraft.commands.Commands.literal("unut").executes(context -> {
						AiBrainManager.clearHistory();
						context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [Kedi]: §fTamam, ne konuşmuşsak sildim. Sıfırdan başlıyoruz!"), false);
						return 1;
					}))
					.then(net.minecraft.commands.Commands.literal("ses")
							.then(net.minecraft.commands.Commands.literal("aç").executes(context -> {
								com.example.ai.tts.TtsManager.setTtsEnabled(true);
								context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [Kedi]: §f🔊 Sesli konuşma açıldı!"), false);
								return 1;
							}))
							.then(net.minecraft.commands.Commands.literal("kapat").executes(context -> {
								com.example.ai.tts.TtsManager.setTtsEnabled(false);
								context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [Kedi]: §f🔇 Yazıyla konuşacağım."), false);
								return 1;
							}))
					)
					.then(net.minecraft.commands.Commands.literal("debug")
							.then(net.minecraft.commands.Commands.literal("aç").executes(context -> {
								com.example.ai.debug.CompanionDebugLogger.setEnabled(true);
								context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
									"§8[DEBUG] §a✅ Debug modu AÇILDl! §7Olaylar, ruh hali değişimleri ve proaktif konuşmalar chat'e basılacak."
								), false);
								return 1;
							}))
							.then(net.minecraft.commands.Commands.literal("kapat").executes(context -> {
								com.example.ai.debug.CompanionDebugLogger.setEnabled(false);
								context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
									"§8[DEBUG] §c❌ Debug modu kapatıldı."
								), false);
								return 1;
							}))
					)
			);
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
