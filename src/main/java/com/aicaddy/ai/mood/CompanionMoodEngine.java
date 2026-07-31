package com.aicaddy.ai.mood;

import com.aicaddy.ai.debug.CompanionDebugLogger;
import com.aicaddy.ai.session.PlayerSessionManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Singleton engine that manages the companion's current emotional state
 * and short-term emotional memory (last 5 events).
 *
 * Thread-safe via AtomicReference for mood and synchronized Deque for memory.
 */
public class CompanionMoodEngine {

	private static final int MAX_MEMORY_SIZE = 5;

	private static final long SAME_EVENT_COOLDOWN_MS = 30_000; // 30 seconds per unique event type

	// --- Helper ---
	private static UUID getFallbackUuid() {
		return PlayerSessionManager.getActiveSessions().stream().findFirst().orElse(null);
	}

	private static PlayerSessionManager.PlayerSession getSession(UUID uuid) {
		if (uuid == null) return null;
		return PlayerSessionManager.getSessionIfExists(uuid);
	}

	// --- Deprecated No-Arg Methods ---

	@Deprecated
	public static CompanionMoodState getCurrentMood() {
		return getCurrentMood(getFallbackUuid());
	}

	@Deprecated
	public static CompanionMoodVector getCurrentVector() {
		return getCurrentVector(getFallbackUuid());
	}

	@Deprecated
	public static void processTrigger(MoodTrigger trigger) {
		processTrigger(trigger, getFallbackUuid(), null);
	}

	@Deprecated
	public static void processTrigger(MoodTrigger trigger, ServerPlayer player) {
		processTrigger(trigger, player != null ? player.getUUID() : getFallbackUuid(), player);
	}

	@Deprecated
	public static void recordEvent(String description) {
		recordEvent(description, getFallbackUuid());
	}

	@Deprecated
	public static void processPlayerSpeech(String speech) {
		processPlayerSpeech(speech, getFallbackUuid());
	}

	@Deprecated
	public static String getMoodContext() {
		return getMoodContext(getFallbackUuid());
	}

	@Deprecated
	public static boolean canSpeakProactively(String eventKey) {
		return canSpeakProactively(eventKey, getFallbackUuid());
	}

	@Deprecated
	public static void resetAllCooldowns() {
		UUID uuid = getFallbackUuid();
		if (uuid != null) {
			PlayerSessionManager.PlayerSession session = getSession(uuid);
			if (session != null) {
				session.resetProactiveCooldowns();
			}
		}
	}

	// --- Public API ---

	public static CompanionMoodState getCurrentMood(UUID playerUuid) {
		com.aicaddy.ai.fsm.StateMachine<CompanionMoodState, MoodTrigger, ServerPlayer> fsm =
				CompanionMoodFsm.getFsm(playerUuid);
		if (fsm != null) {
			return fsm.getCurrentState();
		}
		CompanionMoodVector vec = getCurrentVector(playerUuid);
		return vec != null ? CompanionMoodState.fromVector(vec) : CompanionMoodState.fromVector(CompanionMoodVector.defaultHappy());
	}

	public static CompanionMoodVector getCurrentVector(UUID playerUuid) {
		PlayerSessionManager.PlayerSession session = getSession(playerUuid);
		if (session != null) {
			return session.moodVector.get();
		}
		return CompanionMoodVector.defaultHappy();
	}

	public static void processTrigger(MoodTrigger trigger, UUID playerUuid, ServerPlayer player) {
		PlayerSessionManager.PlayerSession session = getSession(playerUuid);
		if (session == null) return;

		CompanionMoodState oldMood = getCurrentMood(playerUuid);
		CompanionMoodVector targetVec = resolveVector(trigger, playerUuid);
		CompanionMoodVector newVec = session.moodVector.updateAndGet(curr -> curr.smoothToward(targetVec, 0.4));
		CompanionMoodState targetMood = CompanionMoodState.fromVector(newVec);

		recordMemoryEvent(trigger.getMemoryDescription(), playerUuid);
		
		CompanionMoodState newMood = CompanionMoodFsm.tryTransition(playerUuid, targetMood, trigger, player);

		if (oldMood != newMood) {
			int affinity = session.getAffinityScore();
			com.aicaddy.ai.debug.CompanionTelemetryLogger.logMoodTransitionAsync(
				playerUuid.toString(),
				(player != null) ? player.getScoreboardName() : "SYSTEM",
				trigger.name(),
				oldMood,
				newMood,
				affinity
			);
		}
	}

	public static void recordEvent(String description, UUID playerUuid) {
		recordMemoryEvent(description, playerUuid);
	}

	public static void processPlayerSpeech(String speech, UUID playerUuid) {
		PlayerSessionManager.PlayerSession session = getSession(playerUuid);
		if (session == null) return;

		String lower = speech.toLowerCase();
		CompanionMoodVector targetVec = session.moodVector.get();
		if (lower.contains("öldüm") || lower.contains("battım") || lower.contains("mahvoldum")) {
			targetVec = new CompanionMoodVector(-0.6, 0.4);
			recordMemoryEvent("Oyuncu hayal kırıklığını dile getirdi.", playerUuid);
		} else if (lower.contains("bulduk") || lower.contains("buldum") || lower.contains("efsane") || lower.contains("yaptım")) {
			targetVec = new CompanionMoodVector(0.85, 0.90);
			recordMemoryEvent("Oyuncu bir şeyi başardığını söyledi.", playerUuid);
		} else if (lower.contains("sıkıldım") || lower.contains("ne yapacağım") || lower.contains("bıktım")) {
			targetVec = new CompanionMoodVector(0.0, 0.15);
			recordMemoryEvent("Oyuncu sıkıldığını belirtti.", playerUuid);
		}
		final CompanionMoodVector t = targetVec;
		session.moodVector.updateAndGet(curr -> curr.smoothToward(t, 0.4));
	}

	public static String getMoodContext(UUID playerUuid) {
		PlayerSessionManager.PlayerSession session = getSession(playerUuid);
		if (session == null) return "";

		CompanionMoodVector vec = session.moodVector.get();
		CompanionMoodState mood = CompanionMoodState.fromVector(vec);
		StringBuilder sb = new StringBuilder();
		sb.append("[MEVCUT RUH HALİN (Valence-Arousal Vektörü)]: valence=").append(String.format(java.util.Locale.US, "%.2f", vec.valence()))
				.append(", arousal=").append(String.format(java.util.Locale.US, "%.2f", vec.arousal()))
				.append(" (").append(mood.getLabel()).append(" ağırlıklı)\n");
		sb.append("[BU RUH HALİNDE NASIL KONUŞMALISIN]: ").append(mood.getSpeakingInstruction()).append("\n");

		java.util.List<String> emotionalMemory = session.getEmotionalMemorySnapshot();
		if (emotionalMemory != null && !emotionalMemory.isEmpty()) {
			sb.append("[SON YAŞANAN OLAYLAR (Bunları hatırlıyorsun)]:\n");
			for (String event : emotionalMemory) {
				sb.append("  - ").append(event).append("\n");
			}
		}

		return sb.toString();
	}

	public static boolean canSpeakProactively(String eventKey, UUID playerUuid) {
		PlayerSessionManager.PlayerSession session = getSession(playerUuid);
		if (session != null) {
			return session.canSpeakProactively(eventKey);
		}
		return false;
	}

	// --- Internal ---

	private static void recordMemoryEvent(String description, UUID playerUuid) {
		PlayerSessionManager.PlayerSession session = getSession(playerUuid);
		if (session != null) {
			session.recordEmotionalEvent(description);
		}
	}

	private static CompanionMoodVector resolveVector(MoodTrigger trigger, UUID playerUuid) {
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
			case REPEATED_MISTAKE:    return new CompanionMoodVector(-0.5, 0.6);
			case DAY_CAME:            return new CompanionMoodVector(0.5, 0.3);
			default:                  return getCurrentVector(playerUuid);
		}
	}
}
