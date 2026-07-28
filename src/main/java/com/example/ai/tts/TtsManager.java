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

	private static void playSentenceMp3(String sentence) {
		try {
			String encoded = URLEncoder.encode(sentence, StandardCharsets.UTF_8);
			// 1. Try Twitch/StreamElements Amazon Polly 'Filiz' (Natural Turkish Female Neural Voice)
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

			// 2. Fallback to Google Translate TTS if StreamElements returns error
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
