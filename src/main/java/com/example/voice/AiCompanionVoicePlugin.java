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

public class AiCompanionVoicePlugin implements VoicechatPlugin {

	public static VoicechatApi VOICECHAT_API;
	private OpusDecoder opusDecoder;
	private UUID lastSpeakingPlayerUuid = null;

	@Override
	public String getPluginId() {
		return ExampleMod.MOD_ID + "_voice";
	}

	@Override
	public void initialize(VoicechatApi api) {
		VOICECHAT_API = api;
		this.opusDecoder = api.createDecoder();
		VoskSttManager.setSpeechListener(this::onSpeechRecognized);
		ExampleMod.LOGGER.info("AI Companion Simple Voice Chat Plugin initialized.");
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
	}

	private void onMicrophonePacket(MicrophonePacketEvent event) {
		if (!VoskSttManager.isReady() || opusDecoder == null) {
			return;
		}

		try {
			if (event.getSenderConnection() != null && event.getSenderConnection().getPlayer() != null) {
				this.lastSpeakingPlayerUuid = event.getSenderConnection().getPlayer().getUuid();
			}

			byte[] opusData = event.getPacket().getOpusEncodedData();
			short[] pcmData = opusDecoder.decode(opusData);

			// Transcribe updates buffer and automatically triggers onSpeechRecognized when sentence completes
			VoskSttManager.transcribe(pcmData);

		} catch (Exception e) {
			ExampleMod.LOGGER.error("Error processing microphone audio packet:", e);
		}
	}

	private void onSpeechRecognized(String transcribedText) {
		if (transcribedText == null || transcribedText.isEmpty()) {
			return;
		}
		ExampleMod.LOGGER.info("🎙️ [Player Spoke -> Vosk STT]: \"" + transcribedText + "\"");

		if (ExampleMod.SERVER_INSTANCE != null) {
			ExampleMod.SERVER_INSTANCE.execute(() -> {
				ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
						Component.literal("§a🎙️ [Sen -> AI Kedi]: §f" + transcribedText),
						false
				);
				ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
						Component.literal("§7⏳ [AI Kedi düşünüyor...]"),
						false
				);

				ServerPlayer player = null;
				if (lastSpeakingPlayerUuid != null) {
					player = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayer(lastSpeakingPlayerUuid);
				}
				if (player == null && !ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().isEmpty()) {
					player = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().get(0);
				}
				if (player != null) {
					AiBrainManager.processAndRespond(player, transcribedText);
				}
			});
		}
	}
}
