package com.aicaddy.ai.debug;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.mood.CompanionMoodState;
import com.aicaddy.ai.mood.MoodTrigger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug overlay that prints companion internals to in-game chat.
 * Toggle with /aiarkadas debug aç | kapat
 */
public class CompanionDebugLogger {

	private static volatile boolean enabled = false;

	public static void setEnabled(boolean val) {
		enabled = val;
		ExampleMod.LOGGER.info("[CompanionDebug] Debug mode " + (val ? "ENABLED" : "DISABLED"));
	}

	public static boolean isEnabled() {
		return enabled;
	}

	private static final java.io.File LOGS_DIR = new java.io.File("logs");
	private static final java.io.File JSONL_FILE = new java.io.File(LOGS_DIR, "ai_caddy_events.jsonl");

	public static void logJsonl(String type, ServerPlayer player, String event, String key, String val) {
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			try {
				if (!LOGS_DIR.exists()) {
					LOGS_DIR.mkdirs();
				}
				com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
				obj.addProperty("timestamp", java.time.Instant.now().toString());
				obj.addProperty("type", type);
				obj.addProperty("player", player != null ? player.getScoreboardName() : "SYSTEM");
				obj.addProperty("event", event);
				if (key != null && !key.isEmpty()) {
					obj.addProperty(key, val);
				}
				String line = obj.toString() + "\n";
				java.nio.file.Files.writeString(
						JSONL_FILE.toPath(),
						line,
						java.nio.file.StandardOpenOption.CREATE,
						java.nio.file.StandardOpenOption.APPEND
				);
			} catch (Exception ignored) {}
		});
	}

	// ── Mood Events ────────────────────────────────────────────────────────────

	public static void logMoodChange(ServerPlayer player, MoodTrigger trigger, CompanionMoodState newMood) {
		logJsonl("MOOD_CHANGE", player, trigger.name(), "new_mood", newMood.getLabel());
		if (!enabled) return;
		broadcast(player,
			"§8[DEBUG] §6🎭 MOOD DEĞİŞTİ§r | Tetik: §e" + trigger.name()
			+ "§r → Yeni Ruh Hali: §b" + newMood.getLabel()
		);
	}

	public static void logEventDetected(ServerPlayer player, String eventName, String detail) {
		logJsonl("EVENT_DETECTED", player, eventName, "detail", detail);
		if (!enabled) return;
		broadcast(player,
			"§8[DEBUG] §a⚡ OLAY§r | §e" + eventName + "§r: " + detail
		);
	}

	public static void logProactiveSpeechBlocked(ServerPlayer player, String reason) {
		if (!enabled) return;
		broadcast(player,
			"§8[DEBUG] §c⏳ PROAKTİF ENGEL§r | " + reason
		);
	}

	public static void logProactiveSpeechFired(ServerPlayer player, String eventKey) {
		logJsonl("PROACTIVE_SPEECH", player, eventKey, "", "");
		if (!enabled) return;
		broadcast(player,
			"§8[DEBUG] §d🗣 PROAKTİF KONUŞMA§r | Tetikleyen: §e" + eventKey
		);
	}

	// ── Prompt / Response ─────────────────────────────────────────────────────

	public static void logPromptSent(ServerPlayer player, String prompt) {
		if (!enabled) return;
		broadcast(player, "§8[DEBUG] §3📤 YAPAY ZEKAYA GİDEN TAM PROMPT:");
		for (String line : prompt.split("\n")) {
			if (!line.trim().isEmpty()) {
				broadcast(player, "§7" + line);
			}
		}
	}

	public static void logResponseReceived(ServerPlayer player, String rawJson, String extracted) {
		if (!enabled) return;
		broadcast(player, "§8[DEBUG] §2📥 HAM JSON§r: " + rawJson);
		broadcast(player, "§8[DEBUG] §2✅ ÇIKARILAN§r: " + extracted);
	}

	// ── Memory ────────────────────────────────────────────────────────────────

	public static void logMemoryState(ServerPlayer player, java.util.Deque<String> memory) {
		if (!enabled) return;
		StringBuilder sb = new StringBuilder("§8[DEBUG] §5🧠 HAFIZA§r:\n");
		if (memory.isEmpty()) {
			sb.append("  (boş)");
		} else {
			int i = 1;
			for (String event : memory) {
				sb.append("  §7").append(i++).append(".§r ").append(event).append("\n");
			}
		}
		broadcast(player, sb.toString().trim());
	}

	// ── Game Context ──────────────────────────────────────────────────────────

	public static void logGameContext(ServerPlayer player, String context) {
		if (!enabled) return;
		broadcast(player, "§8[DEBUG] §9🌍 OYUN BAĞLAMI§r:\n" + context);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private static void broadcast(ServerPlayer player, String message) {
		if (ExampleMod.SERVER_INSTANCE == null) return;
		ExampleMod.SERVER_INSTANCE.execute(() ->
			player.sendSystemMessage(Component.literal(message))
		);
	}
}
