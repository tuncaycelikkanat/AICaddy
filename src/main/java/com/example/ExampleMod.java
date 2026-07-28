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
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
