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

public class GeminiAiProvider implements AiProvider {

	private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private String apiKey = null;

	@Override
	public String getId() {
		return "gemini";
	}

	private synchronized String getApiKey() {
		if (apiKey != null) {
			return apiKey;
		}
		String[] possiblePaths = {
				"config/gemini_api_key.txt",
				"../config/gemini_api_key.txt",
				"/home/tuncay/Projects/mc/config/gemini_api_key.txt"
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
					ExampleMod.LOGGER.error("Failed to read Gemini API key from: " + path, e);
				}
			}
		}
		return null;
	}

	private static final com.example.ai.resilience.CircuitBreaker GEMINI_BREAKER =
			new com.example.ai.resilience.CircuitBreaker("GeminiAPI", 3, 60_000L);

	@Override
	public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
		if (GEMINI_BREAKER.isOpen()) {
			ExampleMod.LOGGER.warn("Gemini API devre kesicisi açık - istek engellendi (cooldown bekleniyor). Canned Fallback devrede.");
			return CompletableFuture.completedFuture(com.example.ai.resilience.CannedFallbackProvider.getCannedFallback());
		}

		String key = getApiKey();
		if (key == null || key.isEmpty()) {
			ExampleMod.LOGGER.warn("Gemini API key is missing. Add it to config/gemini_api_key.txt.");
			return CompletableFuture.completedFuture("Miyav? (Gemini API anahtarı bulunamadı)");
		}

		String fullPrompt = systemPrompt + "\n\n[Oyuncunun Konuşması]: \"" + userMessage + "\"";

		JsonObject textPart = new JsonObject();
		textPart.addProperty("text", fullPrompt);

		JsonArray partsArray = new JsonArray();
		partsArray.add(textPart);

		JsonObject contentObj = new JsonObject();
		contentObj.add("parts", partsArray);

		JsonArray contentsArray = new JsonArray();
		contentsArray.add(contentObj);

		JsonObject requestBody = new JsonObject();
		requestBody.add("contents", contentsArray);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GEMINI_API_URL + key))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
				.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						GEMINI_BREAKER.recordFailure();
						ExampleMod.LOGGER.error("Gemini API error code " + response.statusCode() + ": " + response.body());
						return com.example.ai.resilience.CannedFallbackProvider.getCannedFallback();
					}
					GEMINI_BREAKER.recordSuccess();
					return extractTextFromJson(response.body());
				})
				.exceptionally(ex -> {
					GEMINI_BREAKER.recordFailure();
					ExampleMod.LOGGER.error("Gemini API request failed.", ex);
					return com.example.ai.resilience.CannedFallbackProvider.getCannedFallback();
				});
	}

	private String extractTextFromJson(String jsonBody) {
		try {
			JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
			if (root.has("candidates")) {
				JsonArray candidates = root.getAsJsonArray("candidates");
				if (candidates.size() > 0) {
					JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
					JsonObject contentObj = firstCandidate.getAsJsonObject("content");
					JsonArray parts = contentObj.getAsJsonArray("parts");
					if (parts.size() > 0) {
						return parts.get(0).getAsJsonObject().get("text").getAsString().trim();
					}
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Error parsing Gemini API JSON response.", e);
		}
		return "Miyav!";
	}
}
