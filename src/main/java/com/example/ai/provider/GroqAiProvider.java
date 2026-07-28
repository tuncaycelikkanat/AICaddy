package com.example.ai.provider;

import com.example.ExampleMod;
import com.google.gson.JsonArray;
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

public class GroqAiProvider implements AiProvider {

	private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
	private static final String MODEL_NAME = "llama-3.3-70b-versatile";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private String apiKey = null;

	@Override
	public String getId() {
		return "groq";
	}

	private synchronized String getApiKey() {
		if (apiKey != null) {
			return apiKey;
		}
		String[] possiblePaths = {
				"config/groq_api_key.txt",
				"../config/groq_api_key.txt",
				"/home/tuncay/Projects/mc/config/groq_api_key.txt"
		};
		for (String path : possiblePaths) {
			File file = new File(path);
			if (file.exists()) {
				try {
					apiKey = Files.readString(file.toPath()).trim();
					if (!apiKey.isEmpty()) {
						return apiKey;
					}
				} catch (IOException e) {
					ExampleMod.LOGGER.error("Failed to read Groq API key from: " + path, e);
				}
			}
		}
		return null;
	}

	@Override
	public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
		String key = getApiKey();
		if (key == null || key.isEmpty()) {
			ExampleMod.LOGGER.warn("Groq API anahtarı eksik. config/groq_api_key.txt dosyasına ekle.");
			return CompletableFuture.completedFuture("Miyav? (API anahtarı bulunamadı)");
		}

		JsonArray messages = new JsonArray();

		JsonObject systemMsg = new JsonObject();
		systemMsg.addProperty("role", "system");
		systemMsg.addProperty("content", systemPrompt);
		messages.add(systemMsg);

		// Only add user message if it's non-empty (proactive speech sends empty string)
		if (userMessage != null && !userMessage.isBlank()) {
			JsonObject userMsg = new JsonObject();
			userMsg.addProperty("role", "user");
			userMsg.addProperty("content", userMessage);
			messages.add(userMsg);
		}

		JsonObject responseFormat = new JsonObject();
		responseFormat.addProperty("type", "json_object");

		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("model", MODEL_NAME);
		requestBody.add("messages", messages);
		requestBody.addProperty("max_tokens", 220);
		requestBody.addProperty("temperature", 0.75);
		requestBody.add("response_format", responseFormat);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GROQ_API_URL))
				.header("Authorization", "Bearer " + key)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
				.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						ExampleMod.LOGGER.error("Groq API hata kodu {}: {}", response.statusCode(), response.body());
						return "Miyav... (Groq bağlantı hatası: " + response.statusCode() + ")";
					}
					return extractFinalReplik(response.body());
				})
				.exceptionally(ex -> {
					ExampleMod.LOGGER.error("Groq API isteği başarısız.", ex);
					return "Miyav! (Groq sunucusuna ulaşılamadı)";
				});
	}

	/**
	 * Parses the Groq response JSON and extracts "final_replik" if present,
	 * otherwise falls back to the raw content string.
	 */
	private String extractFinalReplik(String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			JsonArray choices = root.getAsJsonArray("choices");
			if (choices != null && !choices.isEmpty()) {
				JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
				if (message != null && message.has("content")) {
					String raw = message.get("content").getAsString().trim();
					try {
						JsonObject structured = JsonParser.parseString(raw).getAsJsonObject();
						if (structured.has("final_replik")) {
							return structured.get("final_replik").getAsString().trim();
						}
					} catch (Exception ignored) {
						// Model didn't return JSON — use raw text directly
					}
					return raw;
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Groq yanıtı ayrıştırılamadı.", e);
		}
		return "Miyav... (yanıt anlaşılamadı)";
	}
}
