package com.example.ai.debug;

import com.example.ExampleMod;
import com.example.ai.mood.CompanionMoodState;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * P2.3 Enterprise-grade telemetry and audit logger for AI Caddy.
 * Records LLM turn latency, model IDs, TTS Time-To-First-Audio (TTFA), mood transitions,
 * and affinity scores to logs/ai_caddy_telemetry.jsonl without blocking game threads.
 */
public class CompanionTelemetryLogger {

	private static final File LOGS_DIR = new File("logs");
	private static final File TELEMETRY_FILE = new File(LOGS_DIR, "ai_caddy_telemetry.jsonl");

	/**
	 * Asynchronously logs a complete LLM turn to ai_caddy_telemetry.jsonl.
	 */
	public static void logTurnAsync(
			String playerUuid,
			String playerName,
			String turnType,
			String modelId,
			long latencyMs,
			long ttfaMs,
			CompanionMoodState moodState,
			int affinityScore,
			boolean fallbackTriggered,
			String userMsg,
			String aiResp) {

		CompletableFuture.runAsync(() -> {
			try {
				if (!LOGS_DIR.exists()) {
					LOGS_DIR.mkdirs();
				}

				JsonObject obj = new JsonObject();
				obj.addProperty("timestamp", Instant.now().toString());
				obj.addProperty("event_type", "TURN_TELEMETRY");
				obj.addProperty("player_uuid", playerUuid != null ? playerUuid : "UNKNOWN");
				obj.addProperty("player_name", playerName != null ? playerName : "PLAYER");
				obj.addProperty("turn_type", turnType != null ? turnType : "CHAT");
				obj.addProperty("model_id", modelId != null ? modelId : "unknown");
				obj.addProperty("latency_ms", latencyMs);
				obj.addProperty("ttfa_ms", ttfaMs);
				obj.addProperty("mood_state", moodState != null ? moodState.name() : "CURIOUS");
				obj.addProperty("affinity_score", affinityScore);
				obj.addProperty("fallback_triggered", fallbackTriggered);
				obj.addProperty("user_message_len", userMsg != null ? userMsg.length() : 0);
				obj.addProperty("response_len", aiResp != null ? aiResp.length() : 0);

				String line = obj.toString() + "\n";
				Files.writeString(
						TELEMETRY_FILE.toPath(),
						line,
						StandardOpenOption.CREATE,
						StandardOpenOption.APPEND
				);

				ExampleMod.LOGGER.info("[Telemetry] Turn: {} | Model: {} | Latency: {}ms | Mood: {} | Affinity: {} | Fallback: {}",
						turnType, modelId, latencyMs, (moodState != null ? moodState.name() : "?"), affinityScore, fallbackTriggered);
			} catch (Exception e) {
				ExampleMod.LOGGER.warn("Failed to write AI Caddy telemetry log: {}", e.getMessage());
			}
		});
	}

	/**
	 * Asynchronously logs a mood transition event to ai_caddy_telemetry.jsonl.
	 */
	public static void logMoodTransitionAsync(
			String playerUuid,
			String playerName,
			String triggerName,
			CompanionMoodState oldMood,
			CompanionMoodState newMood,
			int currentAffinity) {

		CompletableFuture.runAsync(() -> {
			try {
				if (!LOGS_DIR.exists()) {
					LOGS_DIR.mkdirs();
				}

				JsonObject obj = new JsonObject();
				obj.addProperty("timestamp", Instant.now().toString());
				obj.addProperty("event_type", "MOOD_TRANSITION");
				obj.addProperty("player_uuid", playerUuid != null ? playerUuid : "UNKNOWN");
				obj.addProperty("player_name", playerName != null ? playerName : "PLAYER");
				obj.addProperty("trigger_name", triggerName != null ? triggerName : "UNKNOWN");
				obj.addProperty("old_mood", oldMood != null ? oldMood.name() : "CURIOUS");
				obj.addProperty("new_mood", newMood != null ? newMood.name() : "CURIOUS");
				obj.addProperty("affinity_score", currentAffinity);

				String line = obj.toString() + "\n";
				Files.writeString(
						TELEMETRY_FILE.toPath(),
						line,
						StandardOpenOption.CREATE,
						StandardOpenOption.APPEND
				);

				ExampleMod.LOGGER.info("[Telemetry] Mood Transition: {} -> {} (Trigger: {}) | Affinity: {}",
						(oldMood != null ? oldMood.name() : "?"),
						(newMood != null ? newMood.name() : "?"),
						triggerName,
						currentAffinity);
			} catch (Exception e) {
				ExampleMod.LOGGER.warn("Failed to write AI Caddy mood transition telemetry log: {}", e.getMessage());
			}
		});
	}
}
