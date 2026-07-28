package com.example.ai.mood;

import com.example.ai.debug.CompanionDebugLogger;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton engine that manages the companion's current emotional state
 * and short-term emotional memory (last 5 events).
 *
 * Thread-safe via AtomicReference for mood and synchronized Deque for memory.
 */
public class CompanionMoodEngine {

	private static final int MAX_MEMORY_SIZE = 5;

	private static final AtomicReference<CompanionMoodState> currentMood =
			new AtomicReference<>(CompanionMoodState.EXCITED); // Start happy on first join

	private static final Deque<String> emotionalMemory = new ArrayDeque<>();

	// Timestamp of last proactive speech to enforce cooldown
	private static volatile long lastProactiveSpeechMs = 0;
	private static final long PROACTIVE_COOLDOWN_MS = 90_000; // 90 seconds

	// --- Public API ---

	public static CompanionMoodState getCurrentMood() {
		return currentMood.get();
	}

	/**
	 * Updates the companion's mood and records the event in emotional memory.
	 */
	public static void processTrigger(MoodTrigger trigger) {
		processTrigger(trigger, null);
	}

	public static void processTrigger(MoodTrigger trigger, ServerPlayer debugPlayer) {
		CompanionMoodState newMood = resolveMood(trigger);
		CompanionMoodState oldMood = currentMood.getAndSet(newMood);
		recordMemoryEvent(trigger.getMemoryDescription());
		if (debugPlayer != null && oldMood != newMood) {
			CompanionDebugLogger.logMoodChange(debugPlayer, trigger, newMood);
		}
	}

	/**
	 * Records a custom free-text event into emotional memory without changing mood.
	 */
	public static void recordEvent(String description) {
		recordMemoryEvent(description);
	}

	/**
	 * Updates mood based on player's speech sentiment (called after player speaks).
	 */
	public static void processPlayerSpeech(String speech) {
		String lower = speech.toLowerCase();
		// Detect if player seems distressed or excited from their words
		if (lower.contains("öldüm") || lower.contains("battım") || lower.contains("mahvoldum")) {
			currentMood.set(CompanionMoodState.SAD);
			recordMemoryEvent("Oyuncu hayal kırıklığını dile getirdi.");
		} else if (lower.contains("bulduk") || lower.contains("buldum") || lower.contains("efsane") || lower.contains("yaptım")) {
			currentMood.set(CompanionMoodState.EXCITED);
			recordMemoryEvent("Oyuncu bir şeyi başardığını söyledi.");
		} else if (lower.contains("sıkıldım") || lower.contains("ne yapacağım") || lower.contains("bıktım")) {
			currentMood.set(CompanionMoodState.BORED);
			recordMemoryEvent("Oyuncu sıkıldığını belirtti.");
		}
	}

	/**
	 * Generates the mood context string to inject into the AI prompt.
	 */
	public static String getMoodContext() {
		CompanionMoodState mood = currentMood.get();
		StringBuilder sb = new StringBuilder();
		sb.append("[MEVCUT RUH HALİN]: ").append(mood.getLabel()).append("\n");
		sb.append("[BU RUH HALİNDE NASIL KONUŞMALISIN]: ").append(mood.getSpeakingInstruction()).append("\n");

		if (!emotionalMemory.isEmpty()) {
			sb.append("[SON YAŞANAN OLAYLAR (Bunları hatırlıyorsun)]:\n");
			for (String event : emotionalMemory) {
				sb.append("  - ").append(event).append("\n");
			}
		}

		return sb.toString();
	}

	/**
	 * Returns true if the companion is allowed to speak proactively right now.
	 */
	public static boolean canSpeakProactively() {
		long now = System.currentTimeMillis();
		if (now - lastProactiveSpeechMs >= PROACTIVE_COOLDOWN_MS) {
			lastProactiveSpeechMs = now;
			return true;
		}
		return false;
	}

	public static void resetProactiveCooldown() {
		lastProactiveSpeechMs = 0;
	}

	// --- Internal ---

	private static synchronized void recordMemoryEvent(String description) {
		if (emotionalMemory.size() >= MAX_MEMORY_SIZE) {
			emotionalMemory.pollFirst(); // Remove oldest
		}
		emotionalMemory.addLast(description);
	}

	private static CompanionMoodState resolveMood(MoodTrigger trigger) {
		switch (trigger) {
			case PLAYER_DIED:         return CompanionMoodState.SAD;
			case FOUND_DIAMOND:       return CompanionMoodState.EXCITED;
			case FOUND_NETHERITE:     return CompanionMoodState.EXCITED;
			case NIGHT_FELL:          return CompanionMoodState.SCARED;
			case LOW_HEALTH:          return CompanionMoodState.SCARED;
			case CREEPER_NEARBY:      return CompanionMoodState.SCARED;
			case WARDEN_ZONE:         return CompanionMoodState.TENSE;
			case NETHER_ENTERED:      return CompanionMoodState.TENSE;
			case END_ENTERED:         return CompanionMoodState.TENSE;
			case BUILDING_DETECTED:   return CompanionMoodState.PROUD;
			case NEW_BIOME:           return CompanionMoodState.CURIOUS;
			case NEW_ITEM:            return CompanionMoodState.CURIOUS;
			case PLAYER_IDLE:         return CompanionMoodState.BORED;
			case REPEATED_MISTAKE:    return CompanionMoodState.FRUSTRATED;
			case DAY_CAME:            return CompanionMoodState.EXCITED;
			case BOSS_KILLED:         return CompanionMoodState.EXCITED;
			default:                  return currentMood.get(); // Keep current
		}
	}
}
