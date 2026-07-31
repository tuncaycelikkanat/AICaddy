package com.aicaddy.voice;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.config.ConfigManager;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SttManager {

	public interface SpeechListener {
		void onSpeechRecognized(String text);
	}

	private static SpeechListener speechListener = null;
	private static String activeProviderId = null;

	// Buffer for accumulating PCM samples during a PTT session when using Groq Whisper
	private static final ConcurrentLinkedQueue<short[]> PCM_BUFFER_QUEUE = new ConcurrentLinkedQueue<>();
	private static volatile int totalBufferedSamples = 0;

	public static void setSpeechListener(SpeechListener listener) {
		speechListener = listener;
		VoskSttManager.setSpeechListener(text -> {
			if (speechListener != null && "default".equals(getActiveProviderId())) {
				speechListener.onSpeechRecognized(text);
			}
		});
	}

	public static void initialize() {
		// Initialize Vosk local STT engine asynchronously
		VoskSttManager.initialize();

		// Load configured STT provider
		String provider = getActiveProviderId();
		ExampleMod.LOGGER.info("✔ STT Motoru yöneticisi hazır. Etkin motor: " + getProviderDisplayName(provider));
	}

	public static synchronized String getActiveProviderId() {
		if (activeProviderId != null) {
			return activeProviderId;
		}
		// Use ConfigManager (no hardcoded paths)
		String val = ConfigManager.getActiveSttProvider().trim().toLowerCase();
		if (val.equals("groq") || val.equals("groq_whisper") || val.equals("bulut")) {
			activeProviderId = "groq_whisper";
		} else {
			activeProviderId = "default";
		}
		return activeProviderId;
	}

	public static synchronized void setActiveProviderId(String providerId) {
		String normalized = "default";
		if (providerId != null) {
			String lower = providerId.trim().toLowerCase();
			if (lower.equals("groq") || lower.equals("groq_whisper") || lower.equals("bulut")) {
				normalized = "groq_whisper";
			}
		}
		activeProviderId = normalized;
		// Save via ConfigManager (no hardcoded paths)
		ConfigManager.saveActiveSttProvider(normalized);
		ExampleMod.LOGGER.info("✔ Yeni STT motoru ayarlandı ve kaydedildi: " + getProviderDisplayName(normalized));
	}

	public static String getProviderDisplayName(String providerId) {
		if ("groq_whisper".equalsIgnoreCase(providerId)) {
			return "Groq Bulut Whisper (%99 Türkçe)";
		}
		return "Varsayılan Yerel Vosk (%85-90 Türkçe)";
	}

	public static boolean isReady() {
		String provider = getActiveProviderId();
		if ("groq_whisper".equals(provider)) {
			return true;
		}
		return VoskSttManager.isReady();
	}

	/**
	 * Feeds a 16-bit PCM audio packet from VoiceChat into the active STT engine.
	 */
	public static void onAudioPacket(short[] pcmData) {
		if (pcmData == null || pcmData.length == 0) return;

		String provider = getActiveProviderId();
		if ("groq_whisper".equals(provider)) {
			// Buffer samples in queue
			short[] copy = Arrays.copyOf(pcmData, pcmData.length);
			PCM_BUFFER_QUEUE.add(copy);
			totalBufferedSamples += copy.length;
		} else {
			// Feed real-time Vosk stream
			VoskSttManager.transcribe(pcmData);
		}
	}

	/**
	 * Flushes the current speaking session after silence is detected.
	 */
	public static synchronized void flushSpeech() {
		String provider = getActiveProviderId();
		if ("groq_whisper".equals(provider)) {
			int sampleCount = totalBufferedSamples;
			if (sampleCount == 0) return;

			short[] combined = new short[sampleCount];
			int idx = 0;
			while (!PCM_BUFFER_QUEUE.isEmpty()) {
				short[] chunk = PCM_BUFFER_QUEUE.poll();
				if (chunk != null) {
					System.arraycopy(chunk, 0, combined, idx, Math.min(chunk.length, sampleCount - idx));
					idx += chunk.length;
				}
			}
			totalBufferedSamples = 0;

			// Check minimum speech duration (~0.25 seconds at 48000 Hz = 12000 samples)
			if (sampleCount < 12000) {
				return;
			}

			ExampleMod.LOGGER.info("☁️ Groq Bulut Whisper'a ses gönderiliyor ({} örnek)...", sampleCount);
			GroqWhisperSttClient.transcribeAsync(combined).thenAccept(text -> {
				if (text != null && !text.isBlank() && speechListener != null) {
					speechListener.onSpeechRecognized(text);
				}
			});

		} else {
			String text = VoskSttManager.flushPartial();
			if (text != null && !text.isBlank() && speechListener != null) {
				speechListener.onSpeechRecognized(text);
			}
		}
	}
}
