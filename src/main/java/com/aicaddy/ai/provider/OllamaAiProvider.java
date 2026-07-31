package com.aicaddy.ai.provider;

import com.aicaddy.ExampleMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class OllamaAiProvider implements AiProvider {

	private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
	private static final String DEFAULT_MODEL = "llama3";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	private String configuredModel = null;

	@Override
	public String getId() {
		return "ollama";
	}

	private synchronized String getModelName() {
		if (configuredModel != null) {
			return configuredModel;
		}
		String[] possiblePaths = {
				"config/ollama_model.txt",
				"../config/ollama_model.txt",
				"/home/tuncay/Projects/mc/config/ollama_model.txt"
		};
		for (String path : possiblePaths) {
			File file = new File(path);
			if (file.exists()) {
				try {
					String val = Files.readString(file.toPath()).trim();
					if (!val.isEmpty()) {
						configuredModel = val;
						return configuredModel;
					}
				} catch (IOException e) {
					ExampleMod.LOGGER.error("Failed to read Ollama model from: " + path, e);
				}
			}
		}
		configuredModel = DEFAULT_MODEL;
		return configuredModel;
	}

	private static final com.aicaddy.ai.resilience.CircuitBreaker OLLAMA_BREAKER =
			new com.aicaddy.ai.resilience.CircuitBreaker("Ollama", 3, 60_000L);

	@Override
	public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
		if (OLLAMA_BREAKER.isOpen()) {
			ExampleMod.LOGGER.warn("Ollama devre kesicisi açık - istek engellendi (cooldown bekleniyor). Canned Fallback devrede.");
			return CompletableFuture.completedFuture(com.aicaddy.ai.resilience.CannedFallbackProvider.getCannedFallback());
		}

		String model = getModelName();
		String fullPrompt = systemPrompt + "\n\n[Oyuncunun Konuşması]: \"" + userMessage + "\"";

		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("model", model);
		requestBody.addProperty("prompt", fullPrompt);
		requestBody.addProperty("stream", false);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(OLLAMA_API_URL))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
				.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						OLLAMA_BREAKER.recordFailure();
						ExampleMod.LOGGER.error("Ollama API error code " + response.statusCode() + ": " + response.body());
						return com.aicaddy.ai.resilience.CannedFallbackProvider.getCannedFallback();
					}
					OLLAMA_BREAKER.recordSuccess();
					return extractTextFromJson(response.body());
				})
				.exceptionally(ex -> {
					OLLAMA_BREAKER.recordFailure();
					ExampleMod.LOGGER.error("Ollama API request failed.", ex);
					return com.aicaddy.ai.resilience.CannedFallbackProvider.getCannedFallback();
				});
	}

	private String extractTextFromJson(String jsonBody) {
		try {
			JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
			if (root.has("response")) {
				return root.get("response").getAsString().trim();
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Error parsing Ollama JSON response.", e);
		}
		return "Miyav!";
	}
}
