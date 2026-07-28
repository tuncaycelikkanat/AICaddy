package com.example;

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
		LOGGER.info("AI Companion Pet Mod initialized.");
		ModEntities.register();
		ModItems.register();

		// Initialize Vosk STT engine asynchronously.
		VoskSttManager.initialize();

		// Cache server instance for in-game chat broadcasting.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SERVER_INSTANCE = server;
		});

		// Register in-game chat commands to change AI Companion personality modes.
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(net.minecraft.commands.Commands.literal("aikedi")
					.then(net.minecraft.commands.Commands.literal("survival").executes(context -> {
						com.example.ai.AiBrainManager.setPlaystyleMode(com.example.ai.AiPlaystyleMode.SURVIVAL);
						context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [AI Kedi]: §fArtık §6HAYATTA KALMA (SURVIVAL) §fmodundayım!"), false);
						return 1;
					}))
					.then(net.minecraft.commands.Commands.literal("speedrun").executes(context -> {
						com.example.ai.AiBrainManager.setPlaystyleMode(com.example.ai.AiPlaystyleMode.SPEEDRUN);
						context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [AI Kedi]: §fArtık §6EJDERHA AVCISI (SPEEDRUN) §fmodundayım!"), false);
						return 1;
					}))
					.then(net.minecraft.commands.Commands.literal("explorer").executes(context -> {
						com.example.ai.AiBrainManager.setPlaystyleMode(com.example.ai.AiPlaystyleMode.EXPLORER);
						context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [AI Kedi]: §fArtık §6KAŞİF & MACERACI (EXPLORER) §fmodundayım!"), false);
						return 1;
					}))
					.then(net.minecraft.commands.Commands.literal("builder").executes(context -> {
						com.example.ai.AiBrainManager.setPlaystyleMode(com.example.ai.AiPlaystyleMode.BUILDER);
						context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [AI Kedi]: §fArtık §6MİMAR (BUILDER) §fmodundayım!"), false);
						return 1;
					}))
					.then(net.minecraft.commands.Commands.literal("unut").executes(context -> {
						com.example.ai.AiBrainManager.clearHistory();
						context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§e🐱 [AI Kedi]: §fGeçmiş konuşmalarımızı unuttum, yepyeni bir sayfayla hazırım!"), false);
						return 1;
					}))
					.then(net.minecraft.commands.Commands.literal("sor")
							.then(net.minecraft.commands.Commands.argument("mesaj", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
									.executes(context -> {
										String mesaj = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mesaj");
										net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
										ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
												net.minecraft.network.chat.Component.literal("§a💬 [Sen -> AI Kedi]: §f" + mesaj),
												false
										);
										ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
												net.minecraft.network.chat.Component.literal("§7⏳ [AI Kedi düşünüyor...]"),
										false
										);
										com.example.ai.AiBrainManager.processAndRespond(player, mesaj);
										return 1;
									})))
			);

			dispatcher.register(net.minecraft.commands.Commands.literal("kedi")
					.then(net.minecraft.commands.Commands.argument("mesaj", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
							.executes(context -> {
								String mesaj = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mesaj");
								net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
								ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
										net.minecraft.network.chat.Component.literal("§a💬 [Sen -> AI Kedi]: §f" + mesaj),
										false
								);
								ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
										net.minecraft.network.chat.Component.literal("§7⏳ [AI Kedi düşünüyor...]"),
										false
								);
								com.example.ai.AiBrainManager.processAndRespond(player, mesaj);
								return 1;
							}))
			);
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
