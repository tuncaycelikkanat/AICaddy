package com.example.ai.mood;

import com.example.ai.debug.CompanionDebugLogger;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton engine that manages the companion's current emotional state
 * and short-term emotional memory (last 5 events).
 *
 * Thread-safe via AtomicReference for mood and synchronized Deque for memory.
 */
public class CompanionMoodEngine {

	private static final int MAX_MEMORY_SIZE = 5;

	private static final AtomicReference<CompanionMoodVector> currentVector =
			new AtomicReference<>(CompanionMoodVector.defaultHappy());

	private static final Deque<String> emotionalMemory = new ArrayDeque<>();

	// Per-event-key cooldown: same event key won't fire proactive speech twice within SAME_EVENT_COOLDOWN_MS
	private static final Map<String, Long> lastEventFireTime = new ConcurrentHashMap<>();
	private static final long SAME_EVENT_COOLDOWN_MS = 30_000; // 30 seconds per unique event type

	// --- Public API ---

	public static CompanionMoodState getCurrentMood() {
		return CompanionMoodState.fromVector(currentVector.get());
	}

	public static CompanionMoodVector getCurrentVector() {
		return currentVector.get();
	}

	/**
	 * Updates the companion's mood via continuous Valence-Arousal exponential smoothing
	 * and records the event in emotional memory.
	 */
	public static void processTrigger(MoodTrigger trigger) {
		processTrigger(trigger, null);
	}

	public static void processTrigger(MoodTrigger trigger, ServerPlayer debugPlayer) {
		CompanionMoodState oldMood = getCurrentMood();
		CompanionMoodVector targetVec = resolveVector(trigger);
		CompanionMoodVector newVec = currentVector.updateAndGet(curr -> curr.smoothToward(targetVec, 0.4));
		CompanionMoodState newMood = CompanionMoodState.fromVector(newVec);

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
		CompanionMoodVector targetVec = currentVector.get();
		if (lower.contains("öldüm") || lower.contains("battım") || lower.contains("mahvoldum")) {
			targetVec = new CompanionMoodVector(-0.6, 0.4);
			recordMemoryEvent("Oyuncu hayal kırıklığını dile getirdi.");
		} else if (lower.contains("bulduk") || lower.contains("buldum") || lower.contains("efsane") || lower.contains("yaptım")) {
			targetVec = new CompanionMoodVector(0.85, 0.90);
			recordMemoryEvent("Oyuncu bir şeyi başardığını söyledi.");
		} else if (lower.contains("sıkıldım") || lower.contains("ne yapacağım") || lower.contains("bıktım")) {
			targetVec = new CompanionMoodVector(0.0, 0.15);
			recordMemoryEvent("Oyuncu sıkıldığını belirtti.");
		}
		final CompanionMoodVector t = targetVec;
		currentVector.updateAndGet(curr -> curr.smoothToward(t, 0.4));
	}

	/**
	 * Generates the mood context string to inject into the AI prompt.
	 */
	public static String getMoodContext() {
		CompanionMoodVector vec = currentVector.get();
		CompanionMoodState mood = CompanionMoodState.fromVector(vec);
		StringBuilder sb = new StringBuilder();
		sb.append("[MEVCUT RUH HALİN (Valence-Arousal Vektörü)]: valence=").append(String.format(java.util.Locale.US, "%.2f", vec.valence()))
				.append(", arousal=").append(String.format(java.util.Locale.US, "%.2f", vec.arousal()))
				.append(" (").append(mood.getLabel()).append(" ağırlıklı)\n");
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
	 * Returns true if the companion is allowed to speak proactively for this event key.
	 * Different event types can each fire once per SAME_EVENT_COOLDOWN_MS.
	 */
	public static boolean canSpeakProactively(String eventKey) {
		long now = System.currentTimeMillis();
		Long last = lastEventFireTime.get(eventKey);
		if (last == null || now - last >= SAME_EVENT_COOLDOWN_MS) {
			lastEventFireTime.put(eventKey, now);
			return true;
		}
		return false;
	}

	public static void resetAllCooldowns() {
		lastEventFireTime.clear();
	}

	// --- Internal ---

	private static synchronized void recordMemoryEvent(String description) {
		if (emotionalMemory.size() >= MAX_MEMORY_SIZE) {
			emotionalMemory.pollFirst(); // Remove oldest
		}
		emotionalMemory.addLast(description);
	}

	private static CompanionMoodVector resolveVector(MoodTrigger trigger) {
		switch (trigger) {
			case PLAYER_DIED:         return new CompanionMoodVector(-0.7, 0.4);
			case FOUND_DIAMOND:
			case FOUND_NETHERITE:
			case BOSS_KILLED:         return new CompanionMoodVector(0.9, 0.95);
			case NIGHT_FELL:          return new CompanionMoodVector(-0.4, 0.8);
			case LOW_HEALTH:
			case CREEPER_NEARBY:      return new CompanionMoodVector(-0.8, 0.95);
			case WARDEN_ZONE:
			case NETHER_ENTERED:
			case END_ENTERED:         return new CompanionMoodVector(-0.3, 0.75);
			case BUILDING_DETECTED:   return new CompanionMoodVector(0.6, 0.6);
			case NEW_BIOME:
			case NEW_ITEM:            return new CompanionMoodVector(0.4, 0.5);
			case PLAYER_IDLE:         return new CompanionMoodVector(0.0, 0.15);
			case REPEATED_MISTAKE:    return new CompanionMoodVector(-0.3, 0.4);
			case DAY_CAME:            return new CompanionMoodVector(0.5, 0.3);
			default:                  return currentVector.get();
		}
	}
}
