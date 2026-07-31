package com.aicaddy.ai.provider;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GeminiAiProvider implements AiProvider {

	private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	@Override
	public String getId() {
		return "gemini";
	}

	private static final com.aicaddy.ai.resilience.CircuitBreaker GEMINI_BREAKER =
			new com.aicaddy.ai.resilience.CircuitBreaker("GeminiAPI", 3, 60_000L);

	@Override
	public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
		if (GEMINI_BREAKER.isOpen()) {
			ExampleMod.LOGGER.warn("Gemini API devre kesicisi açık - istek engellendi (cooldown bekleniyor). Canned Fallback devrede.");
			return CompletableFuture.completedFuture(com.aicaddy.ai.resilience.CannedFallbackProvider.getCannedFallback());
		}

		String key = ConfigManager.getGeminiApiKey();
		if (key == null || key.isEmpty()) {
			ExampleMod.LOGGER.warn("Gemini API key is missing. Add it to config/gemini_api_key.txt.");
			return CompletableFuture.completedFuture("Miyav? (Gemini API anahtarı bulunamadı)");
		}

		JsonObject requestBody = new JsonObject();
		
		// system_instruction
		JsonObject sysPart = new JsonObject();
		sysPart.addProperty("text", systemPrompt);
		JsonArray sysPartsArray = new JsonArray();
		sysPartsArray.add(sysPart);
		JsonObject sysInstruction = new JsonObject();
		sysInstruction.add("parts", sysPartsArray);
		requestBody.add("system_instruction", sysInstruction);

		// contents
		JsonArray contentsArray = new JsonArray();
		if (userMessage != null && !userMessage.trim().isEmpty()) {
		    JsonObject textPart = new JsonObject();
		    textPart.addProperty("text", userMessage);
		    JsonArray partsArray = new JsonArray();
		    partsArray.add(textPart);
		    JsonObject contentObj = new JsonObject();
		    contentObj.add("parts", partsArray);
		    contentsArray.add(contentObj);
		} else {
		    JsonObject textPart = new JsonObject();
		    textPart.addProperty("text", systemPrompt);
		    JsonArray partsArray = new JsonArray();
		    partsArray.add(textPart);
		    JsonObject contentObj = new JsonObject();
		    contentObj.add("parts", partsArray);
		    contentsArray.add(contentObj);
		}
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
						if (response.statusCode() == 429) {
							GEMINI_BREAKER.recordFailure();
							GEMINI_BREAKER.recordFailure(); // HTTP 429 (Quota Exceeded) durumunda devreyi hemen aç, Groq yedeğine geç
						}
						ExampleMod.LOGGER.error("Gemini API error code " + response.statusCode() + ": " + response.body());
						return com.aicaddy.ai.resilience.CannedFallbackProvider.getCannedFallback();
					}
					GEMINI_BREAKER.recordSuccess();
					return extractTextFromJson(response.body());
				})
				.exceptionally(ex -> {
					GEMINI_BREAKER.recordFailure();
					ExampleMod.LOGGER.error("Gemini API request failed.", ex);
					return com.aicaddy.ai.resilience.CannedFallbackProvider.getCannedFallback();
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
						String rawText = parts.get(0).getAsJsonObject().get("text").getAsString().trim();
						com.aicaddy.ai.action.CompanionActionExecutor.queueActionsFromJson(rawText);
						return rawText;
					}
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Error parsing Gemini API JSON response.", e);
		}
		return "Miyav!";
	}
}
