package com.example.ai;

import com.example.ExampleMod;
import com.example.ai.mood.CompanionMoodEngine;
import com.example.ai.mood.EmotionalEventDetector;
import com.example.ai.prompt.SharedPromptRules;
import com.example.ai.provider.AiProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CompletableFuture;

public class AiBrainManager {

	public static class ChatTurn {
		public final String userMessage;
		public final String aiResponse;

		public ChatTurn(String userMessage, String aiResponse) {
			this.userMessage = userMessage;
			this.aiResponse = aiResponse;
		}
	}

	private static AiProvider activeProvider = null;
	private static String lastLoadedProviderId = "";
	private static long lastRequestTimeMs = 0;

	// Short-term conversation memory: last 5 turns per player UUID
	private static final java.util.Map<java.util.UUID, List<ChatTurn>> PLAYER_HISTORIES =
			new java.util.concurrent.ConcurrentHashMap<>();
	private static final int MAX_HISTORY_TURNS = 5;

	// Anti-repetition memory for recent opening phrases per mood (last 8 per mood)
	private static final Map<String, Deque<String>> RECENT_OPENING_PHRASES_BY_MOOD = new ConcurrentHashMap<>();

	public static void recordOpeningPhrase(String reply, String moodLabel) {
		if (reply == null || reply.isBlank()) return;
		String[] words = reply.trim().split("\\s+");
		if (words.length == 0) return;
		String firstWord = words[0].replaceAll("[^a-zA-ZçÇğĞıIİöÖşŞüÜ]", "");
		if (words.length > 1 && (firstWord.equalsIgnoreCase("Vay") || firstWord.equalsIgnoreCase("Aman") || firstWord.equalsIgnoreCase("Yine"))) {
			firstWord = firstWord + " " + words[1].replaceAll("[^a-zA-ZçÇğĞıIİöÖşŞüÜ]", "");
		}
		if (!firstWord.isEmpty()) {
			String key = (moodLabel != null && !moodLabel.isEmpty()) ? moodLabel : "DEFAULT";
			Deque<String> queue = RECENT_OPENING_PHRASES_BY_MOOD.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
			queue.add(firstWord);
			while (queue.size() > 8) {
				queue.pollFirst();
			}
		}
	}

	public static Collection<String> getRecentOpeningPhrases(String moodLabel) {
		String key = (moodLabel != null && !moodLabel.isEmpty()) ? moodLabel : "DEFAULT";
		Deque<String> q = RECENT_OPENING_PHRASES_BY_MOOD.get(key);
		return q != null ? q : Collections.emptyList();
	}

	// ─── History Management (Multiplayer Isolated) ─────────────────────────────

	public static synchronized void addTurnToHistory(net.minecraft.server.level.ServerPlayer player, String userMsg, String aiResp) {
		recordOpeningPhrase(aiResp, CompanionMoodEngine.getCurrentMood().getLabel());
		java.util.UUID uuid = player != null ? player.getUUID() : java.util.UUID.nameUUIDFromBytes("console".getBytes());
		List<ChatTurn> history = PLAYER_HISTORIES.computeIfAbsent(uuid, k -> new ArrayList<>());
		history.add(new ChatTurn(userMsg, aiResp));
		while (history.size() > MAX_HISTORY_TURNS) {
			history.remove(0);
		}
	}

	public static synchronized void clearHistory(net.minecraft.server.level.ServerPlayer player) {
		if (player == null) {
			PLAYER_HISTORIES.clear();
		} else {
			PLAYER_HISTORIES.remove(player.getUUID());
		}
		ExampleMod.LOGGER.info("✔ AI Companion chat history cleared for player: {}",
				player != null ? player.getScoreboardName() : "ALL");
	}

	public static synchronized void clearHistory() {
		clearHistory(null);
	}

	public static synchronized List<ChatTurn> getHistorySnapshot(net.minecraft.server.level.ServerPlayer player) {
		java.util.UUID uuid = player != null ? player.getUUID() : java.util.UUID.nameUUIDFromBytes("console".getBytes());
		List<ChatTurn> history = PLAYER_HISTORIES.getOrDefault(uuid, java.util.Collections.emptyList());
		return new ArrayList<>(history);
	}

	// ─── Provider Management ───────────────────────────────────────────────────

	public static synchronized AiProvider getActiveProvider() {
		String providerId = "groq";
		String[] possiblePaths = {
				"config/ai_companion_provider.txt",
				"../config/ai_companion_provider.txt",
				"/home/tuncay/Projects/mc/config/ai_companion_provider.txt"
		};
		for (String path : possiblePaths) {
			File file = new File(path);
			if (file.exists()) {
				try {
					String val = Files.readString(file.toPath()).trim().toLowerCase();
					if (!val.isEmpty()) {
						providerId = val;
						break;
					}
				} catch (IOException e) {
					ExampleMod.LOGGER.error("Failed to read AI provider config from: " + path, e);
				}
			}
		}

		if (activeProvider == null || !lastLoadedProviderId.equals(providerId)) {
			lastLoadedProviderId = providerId;
			switch (providerId) {
				case "openai":
					activeProvider = new com.example.ai.provider.OpenAiProvider();
					ExampleMod.LOGGER.info("✔ AI Brain Provider: OpenAI (ChatGPT)");
					break;
				case "ollama":
					activeProvider = new com.example.ai.provider.OllamaAiProvider();
					ExampleMod.LOGGER.info("✔ AI Brain Provider: Local Ollama");
					break;
				case "gemini":
					activeProvider = new com.example.ai.provider.GeminiAiProvider();
					ExampleMod.LOGGER.info("✔ AI Brain Provider: Google Gemini");
					break;
				case "groq":
				default:
					activeProvider = new com.example.ai.provider.GroqAiProvider();
					ExampleMod.LOGGER.info("✔ AI Brain Provider: Groq LPU (Llama 3.3 70B)");
					break;
			}
		}
		return activeProvider;
	}

	// ─── Core Prompt Builder ───────────────────────────────────────────────────

	/**
	 * Builds the full companion prompt using emotional mood context and conversation history.
	 * The companion is a friend who shares the experience — not a tutor.
	 */
	private static String buildCompanionPrompt(ServerPlayer player, String playerSpeech) {
		String contextStr = MinecraftContextProvider.getPlayerContext(player);
		String moodContext = CompanionMoodEngine.getMoodContext();

		StringBuilder sb = new StringBuilder();

		// ── Identity ──
		sb.append("Sen '").append(player.getScoreboardName()).append("' ile birlikte Minecraft oynayan bir arkadaşsın.\n");
		sb.append("Adın 'Kedi'. Cinsiyetsiz, enerjik, değişken ruh hallisin.\n\n");

		// ── Core persona ──
		sb.append(SharedPromptRules.CORE_PERSONA_RULE).append("\n");

		// ── Current mood ──
		sb.append(moodContext).append("\n");

		// ── Persistent memory (SQLite) ──
		List<String> recentEvents = com.example.ai.memory.PlayerMemoryStore.getRecentEvents(player.getUUID());
		if (!recentEvents.isEmpty()) {
			sb.append("[BU OYUNCUYLA DAHA ÖNCE YAŞADIKLARIMIZ]:\n");
			for (String evt : recentEvents) {
				sb.append("- ").append(evt).append("\n");
			}
			sb.append("\n");
		}

		// ── Conversation history ──
		List<ChatTurn> history = getHistorySnapshot(player);
		if (!history.isEmpty()) {
			sb.append("[SON KONUŞMALARIMIZ]:\n");
			for (ChatTurn turn : history) {
				sb.append("  ").append(player.getScoreboardName()).append(": \"").append(turn.userMessage).append("\"\n");
				sb.append("  Kedi: \"").append(turn.aiResponse).append("\"\n");
			}
			sb.append("\n");
		}

		// ── Game context ──
		sb.append("[OYUN DURUMU - Şu an nerede, ne var, ne oluyor]:\n").append(contextStr).append("\n\n");

		// ── Response rules ──
		sb.append("CEVAP KURALLARI:\n");
		sb.append(SharedPromptRules.GENERAL_RESPONSE_RULES);
		if (CompanionMoodEngine.getCurrentMood() == com.example.ai.mood.CompanionMoodState.SAD) {
			sb.append(SharedPromptRules.SAD_MOOD_RULE);
		}
		sb.append(SharedPromptRules.SPECIFICITY_RULE);
		sb.append(SharedPromptRules.buildOpeningPhraseBlacklistRule(getRecentOpeningPhrases(CompanionMoodEngine.getCurrentMood().getLabel())));
		sb.append("- Ruh haline göre konuş — şu an ").append(CompanionMoodEngine.getCurrentMood().getLabel()).append(" hissediyorsun.\n\n");

		// ── Primary / Recency Bias Language Enforcement ──
		sb.append(SharedPromptRules.FINAL_LANGUAGE_RULE).append("\n");

		// ── Output format (Multi-Agent Single-Call Structured JSON) ──
		sb.append("ÇIKTI FORMATI — SADECE bu JSON:\n");
		sb.append(SharedPromptRules.buildOutputSchema(CompanionMoodEngine.getCurrentMood().getLabel())).append("\n");

		// ── Player speech ──
		sb.append(player.getScoreboardName()).append(" şimdi şunu dedi/yaptı: \"").append(playerSpeech).append("\"");

		return sb.toString();
	}

	// ─── Generate Response ─────────────────────────────────────────────────────

	public static CompletableFuture<String> generateCompanionResponseAsync(ServerPlayer player, String playerSpeech) {
		// Handle "unut" command
		String lower = playerSpeech.toLowerCase().trim();
		if (lower.equals("unut") || lower.equals("hafızayı temizle") || lower.equals("geçmişi temizle")) {
			clearHistory(player);
			return CompletableFuture.completedFuture("Tamam tamam, seninle ne konuşmuşsak hepsini sildim. Sıfırdan başlıyoruz!");
		}

		// Notify mood engine about player speaking
		EmotionalEventDetector.onPlayerSpoke(player);
		CompanionMoodEngine.processPlayerSpeech(playerSpeech);

		String prompt = buildCompanionPrompt(player, playerSpeech);
		AiProvider provider = getActiveProvider();
		return provider.generateResponseAsync(prompt, playerSpeech);
	}

	// ─── Proactive Speech (triggered by events, not player) ───────────────────

	/**
	 * Fires a proactive, unsolicited companion response to the player.
	 * Called by EmotionalEventDetector when something notable happens.
	 */
	public static void triggerProactiveResponse(ServerPlayer player, String situationPrompt) {
		getActiveProvider().generateResponseAsync(situationPrompt, "").thenAccept(response -> {
			broadcastResponse(player, response);
		}).exceptionally(ex -> {
			ExampleMod.LOGGER.error("Proaktif konuşma başarısız.", ex);
			return null;
		});
	}

	// ─── Process and Broadcast ─────────────────────────────────────────────────

	public static synchronized void processAndRespond(ServerPlayer player, String playerSpeech) {
		long now = System.currentTimeMillis();
		if (now - lastRequestTimeMs < 1000) return; // debounce
		lastRequestTimeMs = now;

		generateCompanionResponseAsync(player, playerSpeech).thenAccept(aiResponse -> {
			addTurnToHistory(player, playerSpeech, aiResponse);
			broadcastResponse(player, aiResponse);
		});
	}

	private static void broadcastResponse(ServerPlayer player, String message) {
		ExampleMod.LOGGER.info("🐱 [Kedi -> {}]: \"{}\"", player.getScoreboardName(), message);
		if (ExampleMod.SERVER_INSTANCE != null) {
			ExampleMod.SERVER_INSTANCE.execute(() -> {
				ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
						Component.literal("§e🐱 [Kedi]: §f" + message),
						false
				);
				// GroqAiProvider streams live TTS sentence-by-sentence during generation;
				// only invoke full TTS after broadcast for non-streaming providers.
				if (!getActiveProvider().getId().equals("groq")) {
					com.example.ai.tts.TtsManager.speakTurkishAsync(player, message);
				}
			});
		}
	}
}
