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
		requestBody.addProperty("stream", true);
		requestBody.add("response_format", responseFormat);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GROQ_API_URL))
				.header("Authorization", "Bearer " + key)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
				.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						String errBody = response.body().reduce("", (a, b) -> a + "\n" + b);
						ExampleMod.LOGGER.error("Groq API hata kodu {}: {}", response.statusCode(), errBody);
						return "Miyav... (Groq bağlantı hatası: " + response.statusCode() + ")";
					}
					return processSseStream(response.body());
				})
				.exceptionally(ex -> {
					ExampleMod.LOGGER.error("Groq API isteği başarısız.", ex);
					return "Miyav! (Groq sunucusuna ulaşılamadı)";
				});
	}

	/**
	 * Processes the Groq Server-Sent Events (SSE) line by line, extracting sentences
	 * on the fly and dispatching them to TtsManager immediately for ultra-low latency.
	 */
	private String processSseStream(java.util.stream.Stream<String> lines) {
		StringBuilder accumulatedJson = new StringBuilder();
		int[] spokenCount = {0};
		boolean[] isFirst = {true};

		lines.forEach(line -> {
			String trimmed = line.trim();
			if (trimmed.startsWith("data: ")) {
				String data = trimmed.substring(6).trim();
				if (data.equals("[DONE]") || data.isEmpty()) return;
				try {
					JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
					JsonArray choices = chunk.getAsJsonArray("choices");
					if (choices != null && !choices.isEmpty()) {
						JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
						if (delta != null && delta.has("content")) {
							String contentChunk = delta.get("content").getAsString();
							accumulatedJson.append(contentChunk);

							// Check if new completed sentences have formed in "final_replik"
							String currentReplik = PartialJsonExtractor.extractPartialReplik(accumulatedJson.toString());
							java.util.List<String> sentences = PartialJsonExtractor.extractCompletedSentences(currentReplik);

							while (spokenCount[0] < sentences.size()) {
								String sentenceToSpeak = sentences.get(spokenCount[0]);
								com.example.ai.tts.TtsManager.speakSentenceAsync(null, sentenceToSpeak, isFirst[0]);
								spokenCount[0]++;
								isFirst[0] = false;
							}
						}
					}
				} catch (Exception ignored) {
					// Incomplete JSON chunk in SSE
				}
			}
		});

		String fullReplik = PartialJsonExtractor.extractPartialReplik(accumulatedJson.toString());
		if (fullReplik.isEmpty()) {
			// Fallback if model didn't return proper final_replik schema
			fullReplik = accumulatedJson.toString().trim();
		}

		// Check if there is any remaining text that wasn't spoken by sentence endings
		java.util.List<String> allSentences = PartialJsonExtractor.extractCompletedSentences(fullReplik);
		if (allSentences.isEmpty() && !fullReplik.isBlank()) {
			com.example.ai.tts.TtsManager.speakSentenceAsync(null, fullReplik, isFirst[0]);
		} else if (!allSentences.isEmpty()) {
			String lastSpoken = allSentences.get(allSentences.size() - 1);
			int lastIdx = fullReplik.lastIndexOf(lastSpoken);
			if (lastIdx >= 0) {
				String remainder = fullReplik.substring(lastIdx + lastSpoken.length()).trim();
				if (!remainder.isEmpty()) {
					com.example.ai.tts.TtsManager.speakSentenceAsync(null, remainder, isFirst[0]);
				}
			}
		}

		return fullReplik;
	}
}
