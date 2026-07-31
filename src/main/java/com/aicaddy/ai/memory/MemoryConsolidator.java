package com.aicaddy.ai.memory;

import com.aicaddy.ExampleMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enterprise Memory Consolidator for AI Caddy.
 * Condenses old chat messages and behavioral events into persistent Player Persona Notes
 * so Kedi remembers the player's preferences across long gaming sessions.
 */
public final class MemoryConsolidator {

	private static final Map<UUID, List<String>> PERSONA_NOTES = new ConcurrentHashMap<>();

	private MemoryConsolidator() {}

	/**
	 * Adds a unique persona note for the player.
	 */
	public static synchronized void addPersonaNote(UUID playerUuid, String note) {
		if (playerUuid == null || note == null || note.isBlank()) return;

		List<String> notes = PERSONA_NOTES.computeIfAbsent(playerUuid, u -> new CopyOnWriteArrayList<>());
		for (String existing : notes) {
			if (existing.equalsIgnoreCase(note.trim())) {
				return; // Prevent duplicate notes
			}
		}

		notes.add(note.trim());
		ExampleMod.LOGGER.info("📝 [MemoryConsolidator] Added persona note for {}: '{}'", playerUuid, note.trim());
	}

	/**
	 * Returns all persona notes for the player.
	 */
	public static List<String> getPersonaNotes(UUID playerUuid) {
		if (playerUuid == null) return Collections.emptyList();
		List<String> list = PERSONA_NOTES.get(playerUuid);
		return list != null ? new ArrayList<>(list) : Collections.emptyList();
	}

	/**
	 * Analyzes a completed chat turn to extract long-term persona insights.
	 */
	public static void consolidateChatTurn(UUID playerUuid, String userMsg, String aiMsg) {
		if (playerUuid == null || userMsg == null) return;

		String lower = userMsg.toLowerCase();
		if (lower.contains("kork") || lower.contains("creeper") || lower.contains("patla")) {
			addPersonaNote(playerUuid, "Oyuncu patlayıcı canavarlardan (Creeper) çekiniyor.");
		}
		if (lower.contains("ev ") || lower.contains("inşa") || lower.contains("mimari") || lower.contains("yaptım")) {
			addPersonaNote(playerUuid, "Oyuncu mimari inşaatlar yapmayı ve üretmeyi seviyor.");
		}
		if (lower.contains("elmas") || lower.contains("maden") || lower.contains("kazı")) {
			addPersonaNote(playerUuid, "Oyuncu madencilik yapmayı ve değerli cevher aramayı önemsiyor.");
		}
		if (lower.contains("nether") || lower.contains("ejderha") || lower.contains("end ")) {
			addPersonaNote(playerUuid, "Oyuncu zorlu boyutlarda maceraya atılmaya hevesli.");
		}
	}

	/**
	 * Formats the player's consolidated persona notes into a prompt section for LLM injection.
	 */
	public static String toPromptSection(UUID playerUuid) {
		List<String> notes = getPersonaNotes(playerUuid);
		if (notes.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("[OYUNCU PROFİL NOTLARI]:\n");
		for (String note : notes) {
			sb.append("- ").append(note).append("\n");
		}
		return sb.toString();
	}

	public static void clear(UUID playerUuid) {
		if (playerUuid != null) {
			PERSONA_NOTES.remove(playerUuid);
		}
	}
}
