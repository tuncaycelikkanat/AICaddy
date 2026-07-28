package com.example.ai.debug;

import com.example.ExampleMod;
import com.example.ai.mood.CompanionMoodState;
import com.example.ai.mood.MoodTrigger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug overlay that prints companion internals to in-game chat.
 * Toggle with /aikedi debug aç | kapat
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

	// ── Mood Events ────────────────────────────────────────────────────────────

	public static void logMoodChange(ServerPlayer player, MoodTrigger trigger, CompanionMoodState newMood) {
		if (!enabled) return;
		broadcast(player,
			"§8[DEBUG] §6🎭 MOOD DEĞİŞTİ§r | Tetik: §e" + trigger.name()
			+ "§r → Yeni Ruh Hali: §b" + newMood.getLabel()
		);
	}

	public static void logEventDetected(ServerPlayer player, String eventName, String detail) {
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
		if (!enabled) return;
		broadcast(player,
			"§8[DEBUG] §d🗣 PROAKTİF KONUŞMA§r | Tetikleyen: §e" + eventKey
		);
	}

	// ── Prompt / Response ─────────────────────────────────────────────────────

	public static void logPromptSent(ServerPlayer player, String prompt) {
		if (!enabled) return;
		// Only show first 200 chars to keep chat readable
		String preview = prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt;
		broadcast(player, "§8[DEBUG] §3📤 PROMPT (ilk 200 char)§r:\n" + preview);
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
