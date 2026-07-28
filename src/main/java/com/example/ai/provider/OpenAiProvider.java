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

public class OpenAiProvider implements AiProvider {

	private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
	private static final String MODEL_NAME = "gpt-4o-mini";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private String apiKey = null;

	@Override
	public String getId() {
		return "openai";
	}

	private synchronized String getApiKey() {
		if (apiKey != null) {
			return apiKey;
		}
		String[] possiblePaths = {
				"config/openai_api_key.txt",
				"../config/openai_api_key.txt",
				"/home/tuncay/Projects/mc/config/openai_api_key.txt"
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
					ExampleMod.LOGGER.error("Failed to read OpenAI API key from: " + path, e);
				}
			}
		}
		return null;
	}

	@Override
	public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
		String key = getApiKey();
		if (key == null || key.isEmpty()) {
			ExampleMod.LOGGER.warn("OpenAI API key is missing. Add it to config/openai_api_key.txt.");
			return CompletableFuture.completedFuture("Miyav? (OpenAI API anahtarı bulunamadı)");
		}

		JsonObject systemMsg = new JsonObject();
		systemMsg.addProperty("role", "system");
		systemMsg.addProperty("content", systemPrompt);

		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content", userMessage);

		JsonArray messages = new JsonArray();
		messages.add(systemMsg);
		messages.add(userMsg);

		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("model", MODEL_NAME);
		requestBody.add("messages", messages);
		requestBody.addProperty("max_tokens", 150);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(OPENAI_API_URL))
				.header("Authorization", "Bearer " + key)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
				.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						ExampleMod.LOGGER.error("OpenAI API error code " + response.statusCode() + ": " + response.body());
						return "Miyav... (OpenAI bağlantı hatası: " + response.statusCode() + ")";
					}
					return extractTextFromJson(response.body());
				})
				.exceptionally(ex -> {
					ExampleMod.LOGGER.error("OpenAI API request failed.", ex);
					return "Miyav! (OpenAI sunucusuna ulaşılamadı)";
				});
	}

	private String extractTextFromJson(String jsonBody) {
		try {
			JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
			if (root.has("choices")) {
				JsonArray choices = root.getAsJsonArray("choices");
				if (choices.size() > 0) {
					JsonObject firstChoice = choices.get(0).getAsJsonObject();
					JsonObject msgObj = firstChoice.getAsJsonObject("message");
					return msgObj.get("content").getAsString().trim();
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Error parsing OpenAI API JSON response.", e);
		}
		return "Miyav!";
	}
}
