package com.example.ai;

import com.example.ExampleMod;
import com.example.ai.provider.AiProvider;
import com.example.ai.provider.GeminiAiProvider;
import com.example.ai.provider.OllamaAiProvider;
import com.example.ai.provider.OpenAiProvider;
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
	private static AiPlaystyleMode currentMode = AiPlaystyleMode.SURVIVAL;

	// Short-term memory storing the last 5 conversation turns (10 messages total)
	private static final List<ChatTurn> CHAT_HISTORY = new ArrayList<>();
	private static final int MAX_HISTORY_TURNS = 5;

	public static synchronized void setPlaystyleMode(AiPlaystyleMode mode) {
		currentMode = mode;
		ExampleMod.LOGGER.info("✔ Switched AI Companion Playstyle Mode to: " + mode.name());
	}

	public static synchronized AiPlaystyleMode getPlaystyleMode() {
		return currentMode;
	}

	public static synchronized void addTurnToHistory(String userMsg, String aiResp) {
		CHAT_HISTORY.add(new ChatTurn(userMsg, aiResp));
		if (CHAT_HISTORY.size() > MAX_HISTORY_TURNS) {
			CHAT_HISTORY.remove(0); // Remove oldest turn to keep memory clean and fast
		}
	}

	public static synchronized void clearHistory() {
		CHAT_HISTORY.clear();
		ExampleMod.LOGGER.info("✔ AI Companion chat history cleared.");
	}

	public static synchronized List<ChatTurn> getHistorySnapshot() {
		return new ArrayList<>(CHAT_HISTORY);
	}

	/**
	 * Reads active provider configuration from file or returns default Gemini provider.
	 */
	public static synchronized AiProvider getActiveProvider() {
		String providerId = "gemini";
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
				case "groq":
					activeProvider = new com.example.ai.provider.GroqAiProvider();
					ExampleMod.LOGGER.info("✔ Switched AI Brain Provider to: Groq LPU (Llama 3.3 70B)");
					break;
				case "openai":
					activeProvider = new com.example.ai.provider.OpenAiProvider();
					ExampleMod.LOGGER.info("✔ Switched AI Brain Provider to: OpenAI (ChatGPT)");
					break;
				case "ollama":
					activeProvider = new com.example.ai.provider.OllamaAiProvider();
					ExampleMod.LOGGER.info("✔ Switched AI Brain Provider to: Local Ollama");
					break;
				case "gemini":
				default:
					activeProvider = new com.example.ai.provider.GeminiAiProvider();
					ExampleMod.LOGGER.info("✔ Switched AI Brain Provider to: Google Gemini");
					break;
			}
		}
		return activeProvider;
	}

	/**
	 * Sends the player's speech along with in-game context and chat history to the active AI provider.
	 */
	public static CompletableFuture<String> generateCompanionResponseAsync(ServerPlayer player, String playerSpeech) {
		String lowerSpeech = playerSpeech.toLowerCase().trim();

		// Check if user wants to clear memory
		if (lowerSpeech.equals("unut") || lowerSpeech.equals("hafızayı temizle") || lowerSpeech.equals("geçmişi temizle")) {
			clearHistory();
			return CompletableFuture.completedFuture("Miyav! Geçmişteki konuşmalarımızı unuttum, yepyeni bir sayfayla hazırım!");
		}

		// Voice/chat command check to switch modes instantly
		if (lowerSpeech.contains("speedrun mod") || lowerSpeech.equals("speedrun")) {
			setPlaystyleMode(AiPlaystyleMode.SPEEDRUN);
			return CompletableFuture.completedFuture("Miyav! Artık " + currentMode.getDisplayName() + " modundayım! Evle vakit kaybetme, hemen demir ve lav havuzu bulalım!");
		} else if (lowerSpeech.contains("survival mod") || lowerSpeech.equals("survival")) {
			setPlaystyleMode(AiPlaystyleMode.SURVIVAL);
			return CompletableFuture.completedFuture("Miyav! Artık " + currentMode.getDisplayName() + " modundayım! Canını, açlığını ve zırhlarını yakından takip edeceğim!");
		} else if (lowerSpeech.contains("kaşif mod") || lowerSpeech.equals("explorer") || lowerSpeech.equals("kaşif")) {
			setPlaystyleMode(AiPlaystyleMode.EXPLORER);
			return CompletableFuture.completedFuture("Miyav! Artık " + currentMode.getDisplayName() + " modundayım! Biyomları, Antik Şehirleri ve gizli tapınakları keşfe çıkalım!");
		} else if (lowerSpeech.contains("mimar mod") || lowerSpeech.equals("builder") || lowerSpeech.equals("mimar")) {
			setPlaystyleMode(AiPlaystyleMode.BUILDER);
			return CompletableFuture.completedFuture("Miyav! Artık " + currentMode.getDisplayName() + " modundayım! Harika yapılar ve kızıltaş devreleri için hazırım!");
		}

		String contextStr = MinecraftContextProvider.getPlayerContext(player);

		StringBuilder promptBuilder = new StringBuilder();
		promptBuilder.append("Sen Minecraft 1.20+ mekaniklerine, tüm crafting tariflerine, maden katlarına (örn: Elmas Y=-58) ve oyun taktiklerine %100 HAKİM, esprili ve tatlı bir oyun arkadaşı kedisin ('AI Kedi'). ")
				.append("KİŞİLİK: Aşırı kaba veya kırıcı olma! Sadece oyuncuya hafifçe takılan, tatlıca takılan (örn: 'Şapşal', 'Noob seni') esprili ve sevimli bir oyuncu yoldaşı ol. ")
				.append("EN ÖNEMLİ KURAL: ASLA UZUN YAZMA! Cevabın KESİNLİKLE EN FAZLA 1 VEYA 2 KISA CÜMLE (maksimum 15-20 kelime) olmalı. ")
				.append("Boş laf yapma, doğrudan Minecraft'ın doğru mekaniğini, eşya gereksinimini veya koordinat taktiğini nokta atışı ver.\n\n")
				.append("[AKTİF OYUN TARZI MODUN]: ").append(currentMode.getDisplayName()).append("\n")
				.append("MOD TALİMATI: ").append(currentMode.getPromptInstruction()).append("\n\n");

		List<ChatTurn> history = getHistorySnapshot();
		if (!history.isEmpty()) {
			promptBuilder.append("[SON KONUŞMA GEÇMİŞİN (HATIRLA)]:\n");
			for (ChatTurn turn : history) {
				promptBuilder.append("Oyuncu: \"").append(turn.userMessage).append("\"\n");
				promptBuilder.append("AI Kedi: \"").append(turn.aiResponse).append("\"\n\n");
			}
		}

		promptBuilder.append("[GÜNCEL OYUN BAĞLAMI]:\n").append(contextStr).append("\n\n");
		promptBuilder.append("[Oyuncunun Şimdiki Sorusuna/Sözüne Cevap Ver]: \"").append(playerSpeech).append("\"");

		AiProvider provider = getActiveProvider();
		return provider.generateResponseAsync(promptBuilder.toString(), playerSpeech);
	}

	/**
	 * Sends voice/chat text to the active AI provider and broadcasts the cat's response to in-game chat.
	 */
	public static synchronized void processAndRespond(ServerPlayer player, String playerSpeech) {
		long now = System.currentTimeMillis();
		if (now - lastRequestTimeMs < 1000) {
			return; // Debounce rapid STT triggers within 1 second
		}
		lastRequestTimeMs = now;

		generateCompanionResponseAsync(player, playerSpeech).thenAccept(aiResponse -> {
			addTurnToHistory(playerSpeech, aiResponse);
			ExampleMod.LOGGER.info("🐱 [AI Kedi (" + getActiveProvider().getId().toUpperCase() + ") -> Oyuncu]: \"" + aiResponse + "\"");
			if (ExampleMod.SERVER_INSTANCE != null) {
				ExampleMod.SERVER_INSTANCE.execute(() -> {
					ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
							Component.literal("§e🐱 [AI Kedi (" + getActiveProvider().getId().toUpperCase() + " | §6" + currentMode.name() + "§e)]: §f" + aiResponse),
							false
					);
					com.example.ai.tts.TtsManager.speakTurkishAsync(player, aiResponse);
				});
			}
		});
	}
}
