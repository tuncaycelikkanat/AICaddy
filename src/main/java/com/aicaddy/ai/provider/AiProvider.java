package com.aicaddy.ai.provider;

import java.util.concurrent.CompletableFuture;

public interface AiProvider {
	/**
	 * Returns the unique identifier of the AI provider (e.g., "gemini", "openai", "ollama").
	 */
	String getId();

	/**
	 * Sends the system prompt and user speech to the AI provider asynchronously.
	 */
	CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage);

	/**
	 * Sends the prompt asynchronously with player context for per-player isolated TTS queues (K-07 fix).
	 */
	default CompletableFuture<String> generateResponseAsync(net.minecraft.server.level.ServerPlayer player, String systemPrompt, String userMessage) {
		return generateResponseAsync(systemPrompt, userMessage);
	}
}
