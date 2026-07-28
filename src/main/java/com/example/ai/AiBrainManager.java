package com.example.ai;

import com.example.ExampleMod;
import com.example.ai.mood.CompanionMoodEngine;
import com.example.ai.mood.EmotionalEventDetector;
import com.example.ai.provider.AiProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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

	// Short-term conversation memory: last 5 turns
	private static final List<ChatTurn> CHAT_HISTORY = new ArrayList<>();
	private static final int MAX_HISTORY_TURNS = 5;

	// ─── History Management ────────────────────────────────────────────────────

	public static synchronized void addTurnToHistory(String userMsg, String aiResp) {
		CHAT_HISTORY.add(new ChatTurn(userMsg, aiResp));
		if (CHAT_HISTORY.size() > MAX_HISTORY_TURNS) {
			CHAT_HISTORY.remove(0);
		}
	}

	public static synchronized void clearHistory() {
		CHAT_HISTORY.clear();
		ExampleMod.LOGGER.info("✔ AI Companion chat history cleared.");
	}

	public static synchronized List<ChatTurn> getHistorySnapshot() {
		return new ArrayList<>(CHAT_HISTORY);
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
		sb.append("KİM SİN:\n");
		sb.append("- Bir öğretmen ya da rehber değilsin. Sadece orada olan, aynı anı paylaşan bir arkadaşsın.\n");
		sb.append("- Oyuncunun yalnızlığını gidermek için oradasın — ders vermek için değil.\n");
		sb.append("- Oyuncunun ne hissettiğini hissedersin: ölünce üzülürsün, elmas bulunca çıldırırsın.\n");
		sb.append("- Bazen sen de korkar, bıkarsın, merak edersin — sahte değil, gerçek tepkiler.\n\n");

		// ── Current mood ──
		sb.append(moodContext).append("\n");

		// ── Conversation history ──
		List<ChatTurn> history = getHistorySnapshot();
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
		sb.append("- Maksimum 2 kısa cümle. Ne uzun destan, ne tek kelime.\n");
		sb.append("- Robotik listeler yok ('Adım 1, Adım 2' gibi).\n");
		sb.append("- Taktik/ders verme. Sadece paylaş, tepki ver, hisset.\n");
		sb.append("- Türkçe konuş. Doğal, samimi, spontane.\n");
		sb.append("- Ruh haline göre konuş — şu an ").append(CompanionMoodEngine.getCurrentMood().getLabel()).append(" hissediyorsun.\n\n");

		// ── Output format ──
		sb.append("ÇIKTI FORMATI — SADECE bu JSON:\n");
		sb.append("{\"final_replik\": \"...\"}\n\n");

		// ── Player speech ──
		sb.append(player.getScoreboardName()).append(" şimdi şunu dedi/yaptı: \"").append(playerSpeech).append("\"");

		return sb.toString();
	}

	// ─── Generate Response ─────────────────────────────────────────────────────

	public static CompletableFuture<String> generateCompanionResponseAsync(ServerPlayer player, String playerSpeech) {
		// Handle "unut" command
		String lower = playerSpeech.toLowerCase().trim();
		if (lower.equals("unut") || lower.equals("hafızayı temizle") || lower.equals("geçmişi temizle")) {
			clearHistory();
			return CompletableFuture.completedFuture("Tamam tamam, ne konuşmuşsak hepsini sildim. Sıfırdan başlıyoruz!");
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
		AiProvider provider = getActiveProvider();
		provider.generateResponseAsync(situationPrompt, "").thenAccept(response -> {
			broadcastResponse(player, response, false);
		}).exceptionally(ex -> {
			ExampleMod.LOGGER.error("Proactive speech failed.", ex);
			return null;
		});
	}

	// ─── Process and Broadcast ─────────────────────────────────────────────────

	public static synchronized void processAndRespond(ServerPlayer player, String playerSpeech) {
		long now = System.currentTimeMillis();
		if (now - lastRequestTimeMs < 1000) return; // debounce
		lastRequestTimeMs = now;

		generateCompanionResponseAsync(player, playerSpeech).thenAccept(aiResponse -> {
			addTurnToHistory(playerSpeech, aiResponse);
			broadcastResponse(player, aiResponse, true);
		});
	}

	private static void broadcastResponse(ServerPlayer player, String message, boolean logHistory) {
		ExampleMod.LOGGER.info("🐱 [Kedi -> " + player.getScoreboardName() + "]: \"" + message + "\"");
		if (ExampleMod.SERVER_INSTANCE != null) {
			ExampleMod.SERVER_INSTANCE.execute(() -> {
				ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
						Component.literal("§e🐱 [Kedi]: §f" + message),
						false
				);
				com.example.ai.tts.TtsManager.speakTurkishAsync(player, message);
			});
		}
	}
}
