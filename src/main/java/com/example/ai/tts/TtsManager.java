package com.example.ai.tts;

import com.example.ExampleMod;
import javazoom.jl.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TtsManager {

	private static final ExecutorService TTS_EXECUTOR = Executors.newSingleThreadExecutor();
	private static boolean ttsEnabled = true;

	public static synchronized void setTtsEnabled(boolean enabled) {
		ttsEnabled = enabled;
		ExampleMod.LOGGER.info("✔ AI Companion TTS Enabled: " + ttsEnabled);
	}

	public static synchronized boolean isTtsEnabled() {
		return ttsEnabled;
	}

	/**
	 * Speaks the given text in Turkish asynchronously without blocking the game thread.
	 * Also plays an in-game cute cat purr/meow sound effect.
	 */
	public static void speakTurkishAsync(ServerPlayer player, String text) {
		if (!ttsEnabled || text == null || text.trim().isEmpty()) {
			return;
		}

		// Play cute in-game cat sound with high pitch (1.25f = anime/cute cat pitch)
		if (player != null && ExampleMod.SERVER_INSTANCE != null) {
			ExampleMod.SERVER_INSTANCE.execute(() -> {
				try {
					player.level().playSound(
							null,
							player.blockPosition(),
							SoundEvents.CAT_PURREOW,
							SoundSource.NEUTRAL,
							1.0f,
							1.25f
					);
				} catch (Exception e) {
					ExampleMod.LOGGER.debug("Could not play cat sound effect: " + e.getMessage());
				}
			});
		}

		// Clean up markdown or formatting tags before sending to TTS
		final String cleanText = cleanTextForTts(text);

		TTS_EXECUTOR.submit(() -> {
			try {
				// Split long texts into smaller sentences (Google TTS supports ~180 chars per request)
				String[] sentences = splitIntoSentences(cleanText, 160);
				for (String sentence : sentences) {
					if (sentence.trim().isEmpty()) continue;
					playSentenceMp3(sentence.trim());
				}
			} catch (Exception e) {
				ExampleMod.LOGGER.error("TTS playback error: " + e.getMessage(), e);
			}
		});
	}

	private static String cachedElevenLabsKey = null;

	private static synchronized String getElevenLabsApiKey() {
		if (cachedElevenLabsKey != null) {
			return cachedElevenLabsKey;
		}
		String[] possiblePaths = {
				"config/elevenlabs_api_key.txt",
				"../config/elevenlabs_api_key.txt",
				"/home/tuncay/Projects/mc/config/elevenlabs_api_key.txt"
		};
		for (String path : possiblePaths) {
			java.io.File file = new java.io.File(path);
			if (file.exists()) {
				try {
					String val = java.nio.file.Files.readString(file.toPath()).trim();
					if (!val.isEmpty()) {
						cachedElevenLabsKey = val;
						ExampleMod.LOGGER.info("✔ Loaded ElevenLabs API Key for voice ID EXAVITQu4vr4xnSDxMaL (Bella)");
						break;
					}
				} catch (Exception e) {
					ExampleMod.LOGGER.error("Failed to read ElevenLabs key from: " + path, e);
				}
			}
		}
		return cachedElevenLabsKey == null ? "" : cachedElevenLabsKey;
	}

	private static boolean playSentenceElevenLabs(String sentence, String apiKey) {
		try {
			String urlStr = "https://api.elevenlabs.io/v1/text-to-speech/EXAVITQu4vr4xnSDxMaL";
			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("xi-api-key", apiKey);
			conn.setRequestProperty("Accept", "audio/mpeg");
			conn.setDoOutput(true);
			conn.setConnectTimeout(6000);
			conn.setReadTimeout(6000);

			String safeText = sentence.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
			String jsonBody = "{\"text\": \"" + safeText + "\", \"model_id\": \"eleven_multilingual_v2\"}";

			try (java.io.OutputStream os = conn.getOutputStream()) {
				os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
			}

			if (conn.getResponseCode() == 200) {
				try (InputStream in = conn.getInputStream()) {
					Player mp3Player = new Player(in);
					mp3Player.play();
					mp3Player.close();
				}
				return true;
			} else {
				ExampleMod.LOGGER.warn("ElevenLabs TTS error code: " + conn.getResponseCode());
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("ElevenLabs TTS failed: " + e.getMessage());
		}
		return false;
	}

	private static void playSentenceMp3(String sentence) {
		try {
			// 1. Try ElevenLabs Anime Voice (lhTvHflPVOqgSWyuWQry) if API key is configured
			String elevenKey = getElevenLabsApiKey();
			if (!elevenKey.isEmpty() && playSentenceElevenLabs(sentence, elevenKey)) {
				return;
			}

			String encoded = URLEncoder.encode(sentence, StandardCharsets.UTF_8);
			// 2. Try Twitch/StreamElements Amazon Polly 'Filiz' (Natural Turkish Female Neural Voice)
			String urlStr = "https://api.streamelements.com/kappa/v2/speech?voice=Filiz&text=" + encoded;
			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
			conn.setConnectTimeout(4000);
			conn.setReadTimeout(4000);

			if (conn.getResponseCode() == 200) {
				try (InputStream in = conn.getInputStream()) {
					Player mp3Player = new Player(in);
					mp3Player.play();
					mp3Player.close();
				}
				return;
			}

			// 3. Fallback to Google Translate TTS if StreamElements returns error
			String fallbackUrl = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=tr&q=" + encoded;
			HttpURLConnection fallbackConn = (HttpURLConnection) new URL(fallbackUrl).openConnection();
			fallbackConn.setRequestMethod("GET");
			fallbackConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
			if (fallbackConn.getResponseCode() == 200) {
				try (InputStream in = fallbackConn.getInputStream()) {
					Player mp3Player = new Player(in);
					mp3Player.play();
					mp3Player.close();
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("Failed to synthesize speech for sentence: \"" + sentence + "\" -> " + e.getMessage());
		}
	}

	private static String cleanTextForTts(String text) {
		// Remove emojis, markdown asterisks, brackets, and extra spaces
		return text.replaceAll("[*#_`~]", "")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static String[] splitIntoSentences(String text, int maxLen) {
		if (text.length() <= maxLen) {
			return new String[]{text};
		}
		// Simple sentence splitting by punctuation
		return text.split("(?<=[.!?])\\s+");
	}
}
