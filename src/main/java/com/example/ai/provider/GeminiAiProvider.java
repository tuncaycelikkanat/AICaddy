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

	@Override
	public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
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
						ExampleMod.LOGGER.error("Gemini API error code " + response.statusCode() + ": " + response.body());
						if (response.statusCode() == 429) {
							return "Miyav! (Çok hızlı konuştuk, Google kotam birkaç saniyelik doldu, biraz bekle!)";
						} else if (response.statusCode() == 400 || response.statusCode() == 403 || response.statusCode() == 404) {
							return "Miyav? (API anahtarımla veya model adıyla ilgili bir sıkıntı var, kontrol eder misin?)";
						}
						return "Miyav... (Google sunucusuna ulaşırken bir aksilik oldu: " + response.statusCode() + ")";
					}
					return extractTextFromJson(response.body());
				})
				.exceptionally(ex -> {
					ExampleMod.LOGGER.error("Gemini API request failed.", ex);
					return "Miyav! (İnternet bağlantımız kesilmiş olabilir)";
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
