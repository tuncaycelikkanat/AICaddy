package com.example.ai.provider;

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
}
