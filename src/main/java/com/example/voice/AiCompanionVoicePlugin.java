package com.example.voice;

import com.example.ExampleMod;
import com.example.ai.AiBrainManager;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AiCompanionVoicePlugin implements VoicechatPlugin {

	public static VoicechatApi VOICECHAT_API;
	private OpusDecoder opusDecoder;
	private UUID lastSpeakingPlayerUuid = null;

	// Flush timer: waits for silence after last audio packet before considering speech done
	private static final ScheduledExecutorService FLUSH_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> pendingFlush = null;

	// Buffer holding the accumulated partial transcript for current PTT session
	private volatile String currentPartial = "";
	// Timestamp of last received audio packet
	private volatile long lastPacketMs = 0;

	// Silence gap after which we consider speech done (ms)
	private static final int SILENCE_GAP_MS = 500;

	@Override
	public String getPluginId() {
		return ExampleMod.MOD_ID + "_voice";
	}

	@Override
	public void initialize(VoicechatApi api) {
		VOICECHAT_API = api;
		this.opusDecoder = api.createDecoder();
		VoskSttManager.setSpeechListener(this::onSpeechRecognized);
		ExampleMod.LOGGER.info("AI Companion Voice Plugin başlatıldı.");
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
	}

	private void onMicrophonePacket(MicrophonePacketEvent event) {
		if (!VoskSttManager.isReady() || opusDecoder == null) return;

		try {
			if (event.getSenderConnection() != null && event.getSenderConnection().getPlayer() != null) {
				this.lastSpeakingPlayerUuid = event.getSenderConnection().getPlayer().getUuid();
			}

			byte[] opusData = event.getPacket().getOpusEncodedData();
			short[] pcmData = opusDecoder.decode(opusData);

			lastPacketMs = System.currentTimeMillis();

			// Feed audio to Vosk
			String result = VoskSttManager.transcribe(pcmData);
			if (result != null && !result.isBlank()) {
				currentPartial = result;
			}

			// Cancel previous flush timer and restart it
			// This means: "speech is done SILENCE_GAP_MS after the last audio packet"
			if (pendingFlush != null && !pendingFlush.isDone()) {
				pendingFlush.cancel(false);
			}
			pendingFlush = FLUSH_SCHEDULER.schedule(this::flushCurrentSpeech, SILENCE_GAP_MS, TimeUnit.MILLISECONDS);

		} catch (Exception e) {
			ExampleMod.LOGGER.error("Ses paketi işlenirken hata:", e);
		}
	}

	/**
	 * Called after SILENCE_GAP_MS of silence.
	 * Flushes whatever Vosk has recognized as the final speech.
	 */
	private void flushCurrentSpeech() {
		String flushed = VoskSttManager.flushPartial();
		if (flushed != null && !flushed.isBlank()) {
			ExampleMod.LOGGER.info("🎙️ Konuşma tamamlandı: \"{}\"", flushed);
			onSpeechRecognized(flushed);
		}
	}

	private void onSpeechRecognized(String transcribedText) {
		if (transcribedText == null || transcribedText.isBlank()) return;

		ExampleMod.LOGGER.info("🎙️ [STT]: \"{}\"", transcribedText);

		if (ExampleMod.SERVER_INSTANCE != null) {
			ExampleMod.SERVER_INSTANCE.execute(() -> {
				ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
						Component.literal("§a🎙️ [Sen]: §f" + transcribedText),
						false
				);

				ServerPlayer player = resolvePlayer();
				if (player != null) {
					AiBrainManager.processAndRespond(player, transcribedText);
				}
			});
		}
	}

	private ServerPlayer resolvePlayer() {
		if (ExampleMod.SERVER_INSTANCE == null) return null;
		if (lastSpeakingPlayerUuid != null) {
			ServerPlayer p = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayer(lastSpeakingPlayerUuid);
			if (p != null) return p;
		}
		var players = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers();
		return players.isEmpty() ? null : players.get(0);
	}
}
