package com.example.voice;

import com.example.ExampleMod;
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

	@Override
	public String getPluginId() {
		return ExampleMod.MOD_ID + "_voice";
	}

	@Override
	public void initialize(VoicechatApi api) {
		VOICECHAT_API = api;
		this.opusDecoder = api.createDecoder();
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
			byte[] opusData = event.getPacket().getOpusEncodedData();
			short[] pcmData = opusDecoder.decode(opusData);

			String transcribedText = VoskSttManager.transcribe(pcmData);

			if (transcribedText != null && !transcribedText.isEmpty()) {
				ExampleMod.LOGGER.info("🎙️ [Player Spoke -> Vosk STT]: \"" + transcribedText + "\"");

				// Broadcast transcribed voice to in-game chat.
				if (ExampleMod.SERVER_INSTANCE != null) {
					ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
							Component.literal("§a🎙️ [Sen -> AI Kedi]: §f" + transcribedText),
							false
					);
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Error processing microphone audio packet:", e);
		}
	}
}
