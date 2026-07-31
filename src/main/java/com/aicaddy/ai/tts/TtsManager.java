package com.aicaddy.ai.tts;

import com.aicaddy.ExampleMod;
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
	private static volatile boolean ttsEnabled = true;

	// Path to the edge-tts binary (installed in venv)
	private static final String EDGE_TTS_BIN = "/home/tuncay/.venvs/tts/bin/edge-tts";
	// Microsoft Edge TTS voice — natural Turkish female neural voice
	private static final String EDGE_TTS_VOICE = "tr-TR-EmelNeural";
	// Player binary for piped audio
	private static final String MPV_BIN = "/usr/bin/mpv";

	public static void setTtsEnabled(boolean enabled) {
		ttsEnabled = enabled;
		ExampleMod.LOGGER.info("TTS {}", enabled ? "açıldı" : "kapatıldı");
	}

	public static boolean isTtsEnabled() {
		return ttsEnabled;
	}

	private static final com.aicaddy.ai.resilience.CircuitBreaker EDGE_TTS_BREAKER =
			new com.aicaddy.ai.resilience.CircuitBreaker("EdgeTTS", 3, 60_000L);
	private static final com.aicaddy.ai.resilience.CircuitBreaker ELEVEN_LABS_BREAKER =
			new com.aicaddy.ai.resilience.CircuitBreaker("ElevenLabs", 3, 60_000L);
	private static final com.aicaddy.ai.resilience.CircuitBreaker STREAM_ELEMENTS_BREAKER =
			new com.aicaddy.ai.resilience.CircuitBreaker("StreamElements", 3, 60_000L);

	/**
	 * Splits a complete or partial reply into sentences and speaks them sequentially via speakSentenceAsync.
	 * This ensures first-audio latency is minimized by synthesizing shorter sentence chunks.
	 */
	public static void speakStreamingSentencesAsync(ServerPlayer player, String message) {
		if (!ttsEnabled || message == null || message.isBlank()) return;
		java.util.List<String> sentences = com.aicaddy.ai.provider.PartialJsonExtractor.extractCompletedSentences(message);
		if (sentences.isEmpty()) {
			speakSentenceAsync(player, message, true);
		} else {
			boolean isFirst = true;
			for (String sentence : sentences) {
				speakSentenceAsync(player, sentence, isFirst);
				isFirst = false;
			}
		}
	}

	/**
	 * Speaks a single sentence immediately via the FIFO single-thread TTS executor.
	 * Used by streaming LLM responses so the first sentence plays while the second is still generating.
	 */
	public static void speakSentenceAsync(ServerPlayer player, String sentence, boolean isFirstSentence) {
		if (!ttsEnabled || sentence == null || sentence.isBlank()) return;

		if (isFirstSentence) {
			playCatSound(player);
		}

		final String clean = cleanTextForTts(sentence);
		if (clean.isEmpty()) return;

		TTS_EXECUTOR.submit(() -> {
			try {
				if (!EDGE_TTS_BREAKER.isOpen()) {
					if (speakEdgeTts(clean)) {
						EDGE_TTS_BREAKER.recordSuccess();
						return;
					} else {
						EDGE_TTS_BREAKER.recordFailure();
					}
				}

				String elevenKey = getElevenLabsApiKey();
				if (!elevenKey.isEmpty() && !ELEVEN_LABS_BREAKER.isOpen()) {
					if (speakElevenLabs(clean, elevenKey)) {
						ELEVEN_LABS_BREAKER.recordSuccess();
						return;
					} else {
						ELEVEN_LABS_BREAKER.recordFailure();
					}
				}

				if (!STREAM_ELEMENTS_BREAKER.isOpen()) {
					if (speakStreamElements(clean)) {
						STREAM_ELEMENTS_BREAKER.recordSuccess();
						return;
					} else {
						STREAM_ELEMENTS_BREAKER.recordFailure();
					}
				}

				// All providers failed or circuit breakers are open -> fallback sound
				playCannedFallback(player);
			} catch (Exception e) {
				ExampleMod.LOGGER.error("Streaming TTS oynatma hatası: {}", e.getMessage());
				playCannedFallback(player);
			}
		});
	}

	/**
	 * Plays a canned backup purr/meow response sound when all external TTS providers fail or circuit breakers open.
	 */
	private static void playCannedFallback(ServerPlayer player) {
		if (ExampleMod.SERVER_INSTANCE == null) return;
		ExampleMod.SERVER_INSTANCE.execute(() -> {
			try {
				ServerPlayer target = player;
				if (target == null && !ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().isEmpty()) {
					target = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().get(0);
				}
				if (target != null) {
					target.level().playSound(
							null,
							target.blockPosition(),
							SoundEvents.CAT_PURR,
							SoundSource.NEUTRAL,
							1.0f, 1.0f
					);
				}
			} catch (Exception ignored) {}
		});
	}

	/**
	 * Speaks the given text asynchronously using Microsoft Edge TTS (Emel Neural).
	 * Falls back to ElevenLabs → StreamElements → Google TTS on failure.
	 */
	public static void speakTurkishAsync(ServerPlayer player, String text) {
		speakSentenceAsync(player, text, true);
	}

	// ── Microsoft Edge TTS (Primary) ──────────────────────────────────────────

	/**
	 * Synthesizes speech via edge-tts and pipes stdout directly to mpv.
	 * No temp files, no API keys, completely free.
	 */
	private static boolean speakEdgeTts(String text) {
		try {
			// edge-tts --voice tr-TR-EmelNeural --text "..." --write-media - | mpv - --no-video
			ProcessBuilder edgePb = new ProcessBuilder(
					EDGE_TTS_BIN,
					"--voice", EDGE_TTS_VOICE,
					"--text", text,
					"--write-media", "-"
			);
			edgePb.redirectErrorStream(false);
			Process edgeProcess = edgePb.start();

			// Pipe edge-tts stdout → mpv stdin
			ProcessBuilder mpvPb = new ProcessBuilder(
					MPV_BIN, "-",
					"--no-video",
					"--really-quiet",
					"--audio-display=no"
			);
			mpvPb.redirectErrorStream(false);
			Process mpvProcess = mpvPb.start();

			// Stream edge-tts output → mpv input in a thread
			Thread pipeThread = new Thread(() -> {
				try (InputStream edgeOut = edgeProcess.getInputStream();
					 var mpvIn = mpvProcess.getOutputStream()) {
					byte[] buf = new byte[4096];
					int n;
					while ((n = edgeOut.read(buf)) != -1) {
						mpvIn.write(buf, 0, n);
					}
				} catch (Exception ignored) {}
			});
			pipeThread.setDaemon(true);
			pipeThread.start();

			int edgeExit = edgeProcess.waitFor();
			pipeThread.join(8000);
			mpvProcess.waitFor();

			if (edgeExit == 0) {
				return true;
			}
			ExampleMod.LOGGER.warn("edge-tts çıkış kodu: {}", edgeExit);
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("Microsoft Edge TTS başarısız: {}", e.getMessage());
		}
		return false;
	}

	// ── ElevenLabs (Secondary) ────────────────────────────────────────────────

	private static String cachedElevenLabsKey = null;

	private static synchronized String getElevenLabsApiKey() {
		if (cachedElevenLabsKey != null) return cachedElevenLabsKey;
		String[] paths = {
				"config/elevenlabs_api_key.txt",
				"../config/elevenlabs_api_key.txt",
				"/home/tuncay/Projects/mc/config/elevenlabs_api_key.txt"
		};
		for (String path : paths) {
			java.io.File file = new java.io.File(path);
			if (file.exists()) {
				try {
					String val = java.nio.file.Files.readString(file.toPath()).trim();
					if (!val.isEmpty()) {
						cachedElevenLabsKey = val;
						return val;
					}
				} catch (Exception e) {
					ExampleMod.LOGGER.error("ElevenLabs key okunamadı: {}", path);
				}
			}
		}
		return "";
	}

	private static boolean speakElevenLabs(String text, String apiKey) {
		try {
			URL url = new URL("https://api.elevenlabs.io/v1/text-to-speech/EXAVITQu4vr4xnSDxMaL");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("xi-api-key", apiKey);
			conn.setRequestProperty("Accept", "audio/mpeg");
			conn.setDoOutput(true);
			conn.setConnectTimeout(6000);
			conn.setReadTimeout(6000);

			String safeText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
			String body = "{\"text\": \"" + safeText + "\", \"model_id\": \"eleven_multilingual_v2\"}";
			conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

			if (conn.getResponseCode() == 200) {
				playWithMpv(conn.getInputStream());
				return true;
			}
			ExampleMod.LOGGER.warn("ElevenLabs hata kodu: {}", conn.getResponseCode());
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("ElevenLabs başarısız: {}", e.getMessage());
		}
		return false;
	}

	// ── StreamElements Fallback ───────────────────────────────────────────────

	private static boolean speakStreamElements(String text) {
		try {
			String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
			URL url = new URL("https://api.streamelements.com/kappa/v2/speech?voice=Filiz&text=" + encoded);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "Mozilla/5.0");
			conn.setConnectTimeout(4000);
			conn.setReadTimeout(4000);
			if (conn.getResponseCode() == 200) {
				playWithMpv(conn.getInputStream());
				return true;
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("StreamElements fallback başarısız: {}", e.getMessage());
		}
		return false;
	}

	// ── Shared Helpers ────────────────────────────────────────────────────────

	private static void playWithMpv(InputStream audioStream) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(
				MPV_BIN, "-",
				"--no-video",
				"--really-quiet",
				"--audio-display=no"
		);
		pb.redirectErrorStream(false);
		Process process = pb.start();
		try (var out = process.getOutputStream()) {
			byte[] buf = new byte[4096];
			int n;
			while ((n = audioStream.read(buf)) != -1) {
				out.write(buf, 0, n);
			}
		}
		process.waitFor();
	}

	private static void playCatSound(ServerPlayer player) {
		if (ExampleMod.SERVER_INSTANCE == null) return;
		ExampleMod.SERVER_INSTANCE.execute(() -> {
			try {
				ServerPlayer target = player;
				if (target == null && !ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().isEmpty()) {
					target = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().get(0);
				}
				if (target != null) {
					target.level().playSound(
							null,
							target.blockPosition(),
							SoundEvents.CAT_PURREOW,
							SoundSource.NEUTRAL,
							1.0f, 1.25f
					);
				}
			} catch (Exception ignored) {}
		});
	}

	private static String cleanTextForTts(String text) {
		return text
				.replaceAll("[*#_`~]", "")
				.replaceAll("\\p{So}|\\p{Sm}|[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", "") // emojis
				.replaceAll("\\s+", " ")
				.trim();
	}
}
