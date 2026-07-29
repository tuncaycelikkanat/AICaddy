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

	private static final com.example.ai.resilience.CircuitBreaker GROQ_BREAKER =
			new com.example.ai.resilience.CircuitBreaker("GroqAPI", 3, 60_000L);

	@Override
	public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
		if (GROQ_BREAKER.isOpen()) {
			ExampleMod.LOGGER.warn("Groq API devre kesicisi açık - istek engellendi (cooldown bekleniyor). Canned Fallback devrede.");
			return CompletableFuture.completedFuture(com.example.ai.resilience.CannedFallbackProvider.getCannedFallback());
		}

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
		requestBody.addProperty("max_tokens", 250);
		requestBody.addProperty("temperature", 0.72);
		requestBody.addProperty("frequency_penalty", 0.45);
		requestBody.addProperty("stream", true);
		requestBody.add("response_format", responseFormat);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GROQ_API_URL))
				.timeout(java.time.Duration.ofMillis(2500))
				.header("Authorization", "Bearer " + key)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
				.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
				.thenApply(response -> {
					if (response.statusCode() != 200) {
						GROQ_BREAKER.recordFailure();
						String errBody = response.body().reduce("", (a, b) -> a + "\n" + b);
						ExampleMod.LOGGER.error("Groq API hata kodu {}: {}", response.statusCode(), errBody);
						return com.example.ai.resilience.CannedFallbackProvider.getCannedFallback();
					}
					GROQ_BREAKER.recordSuccess();
					return processSseStream(response.body());
				})
				.exceptionally(ex -> {
					GROQ_BREAKER.recordFailure();
					ExampleMod.LOGGER.error("Groq API isteği başarısız.", ex);
					return com.example.ai.resilience.CannedFallbackProvider.getCannedFallback();
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
		try {
			JsonObject parsed = JsonParser.parseString(accumulatedJson.toString()).getAsJsonObject();
			if (parsed.has("durum_analizi")) {
				ExampleMod.LOGGER.info("🔍 [Taktiksel Analiz]: {}", parsed.get("durum_analizi").getAsString());
			}
			if (parsed.has("ic_dusunce")) {
				ExampleMod.LOGGER.info("💭 [Kedi İç Düşünce]: {}", parsed.get("ic_dusunce").getAsString());
			}
		} catch (Exception ignored) {}

		String fullReplik = PartialJsonExtractor.extractPartialReplik(accumulatedJson.toString());
		if (fullReplik.isEmpty()) {
			fullReplik = accumulatedJson.toString().trim();
		}

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

		fullReplik = sanitizeTurkishText(fullReplik);
		return fullReplik;
	}

	public static boolean containsForeignOrHallucinatedWords(String text) {
		if (text == null || text.isBlank()) return false;
		String lower = " " + text.toLowerCase() + " ";
		String[] blacklist = {
				" means ", " outside ", " company ", " needed ", " nhanh ",
				" iets ", " thật ", " completely ", " really ", " actually ",
				" bencecraft ", " oynamalık ", " oyuncucomplex "
		};
		for (String bad : blacklist) {
			if (lower.contains(bad)) return true;
		}
		return false;
	}

	public static String sanitizeTurkishText(String input) {
		return sanitizeTurkishText(input, "", 0);
	}

	public static String sanitizeTurkishText(String input, String mood, int scenarioId) {
		if (input == null) return "";
		String clean = input
				.replace(" means ", " demek ")
				.replace(" outside ", " dış ")
				.replace(" company ", " yoldaşlık ")
				.replace(" Needed", " gerekli")
				.replace(" needed", " gerekli")
				.replace(" nhanh ", " hızlıca ")
				.replace(" BenceCraft", " Çakmak")
				.replace(" bencecraft", " çakmak")
				.replace(" oynamalık", " kazmalık")
				.replace(" iets ", " bir şey ")
				.replace(" thật ", " ")
				.replace(" OyuncuComplex", " Oyuncu")
				.replace(" completely ", " tamamen ")
				.replace(" really ", " gerçekten ")
				.replace(" actually ", " aslında ")
				.replace("Oh no", "Eyvah")
				.replace("oh no", "eyvah")
				.replace("Oh No", "Eyvah")
				.replace("AmanTanrım", "Aman Allah'ım")
				.replace("Aman Allahım", "Aman Allah'ım")
				.replace("完全", "tamamen ");

		clean = clean.replaceAll("[^a-zA-Z0-9çÇğĞıIİöÖşŞüÜ.,!?'\"\\s\\-—:\\(\\)]", "").trim();

		String[] SCARED_OPENINGS = {
				"Dikkat et, ", "Aman dur, ", "Sakın kımıldama, ", "Koş koş, ", "Tehlike büyük, ",
				"Uzak dur, ", "Yüreğim hopladı, ", "Arkana bakma, ", "Hadi kaçalım, ", "Sakin ol, "
		};
		String[] SAD_OPENINGS = {
				"Canın sağ olsun, ", "Çok üzüldüm, ", "Olsun be dostum, ", "İçim parçalandı, ", "Geçecek, ",
				"Takma kafana, ", "Biliyorum çok zor, ", "Yanındayım, ", "Moralini bozma, ", "Pes etmek yok, "
		};
		String[] EXCITED_OPENINGS = {
				"Harika bir an, ", "Helal olsun, ", "Yaşasın, ", "Muazzam iş, ", "İşte bu, ", "Süper, "
		};

		if ("SCARED".equalsIgnoreCase(mood) && (clean.startsWith("Eyvah") || clean.startsWith("eyvah") || clean.startsWith("Oh no"))) {
			clean = clean.replaceFirst("^(?i)(Eyvah|Oh no)[, ]*", SCARED_OPENINGS[Math.abs(scenarioId) % SCARED_OPENINGS.length]);
		} else if ("SAD".equalsIgnoreCase(mood) && (clean.startsWith("Eyvah") || clean.startsWith("eyvah") || clean.startsWith("Oh no"))) {
			clean = clean.replaceFirst("^(?i)(Eyvah|Oh no)[, ]*", SAD_OPENINGS[Math.abs(scenarioId) % SAD_OPENINGS.length]);
		} else if ("EXCITED".equalsIgnoreCase(mood) && (clean.startsWith("Vay be") || clean.startsWith("Vay canına") || clean.startsWith("Vay"))) {
			clean = clean.replaceFirst("^(?i)(Vay be|Vay canına|Vay)[, ]*", EXCITED_OPENINGS[Math.abs(scenarioId) % EXCITED_OPENINGS.length]);
		}

		return clean.trim();
	}
}
