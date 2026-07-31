package com.aicaddy.ai.debug;

import com.aicaddy.ai.memory.MemoryConsolidator;
import com.aicaddy.ai.memory.SemanticEpisodeMemory;
import com.aicaddy.ai.mood.CompanionMoodEngine;
import com.aicaddy.ai.mood.CompanionMoodState;
import com.aicaddy.ai.provider.ProviderRouter;
import com.aicaddy.ai.resilience.CircuitBreaker;
import com.aicaddy.ai.utility.UtilityActionEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise AI Inspector HUD & Telemetry Overlay for AI Caddy.
 * Displays real-time FSM states, Valence-Arousal coordinates, Provider CircuitBreaker status,
 * Semantic Memory size, and Utility AI scoring decisions.
 */
public final class AiInspectorHud {

	private static final Set<UUID> DEBUG_PLAYERS = ConcurrentHashMap.newKeySet();

	private AiInspectorHud() {}

	public static boolean toggleDebug(ServerPlayer player) {
		if (player == null) return false;
		UUID uuid = player.getUUID();
		boolean enabled;
		if (DEBUG_PLAYERS.contains(uuid)) {
			DEBUG_PLAYERS.remove(uuid);
			enabled = false;
		} else {
			DEBUG_PLAYERS.add(uuid);
			enabled = true;
		}
		return enabled;
	}

	public static boolean isDebugEnabled(UUID playerUuid) {
		return playerUuid != null && DEBUG_PLAYERS.contains(playerUuid);
	}

	/**
	 * Formats and sends the real-time AI Inspector telemetry table to the player.
	 */
	public static void sendDebugOverlay(ServerPlayer player) {
		if (player == null) return;
		UUID uuid = player.getUUID();

		CompanionMoodState mood = CompanionMoodEngine.getCurrentMood(uuid);
		if (mood == null) mood = CompanionMoodState.CURIOUS;

		CircuitBreaker geminiBreaker = ProviderRouter.getBreaker("gemini");
		CircuitBreaker groqBreaker = ProviderRouter.getBreaker("groq");

		String geminiState = (geminiBreaker != null) ? geminiBreaker.getState().name() : "N/A";
		String groqState = (groqBreaker != null) ? groqBreaker.getState().name() : "N/A";

		int epCount = SemanticEpisodeMemory.getEpisodeCount(uuid);
		int personaCount = MemoryConsolidator.getPersonaNotes(uuid).size();

		float hpRatio = player.getHealth() / player.getMaxHealth();
		UtilityActionEngine.UtilityDecision decision = UtilityActionEngine.decideBestAction(
				hpRatio, 0.20f, 0.20, 0.40, false);

		StringBuilder sb = new StringBuilder();
		sb.append("§6========== [ AI CADDY v2 - TELEMETRY HUD ] ==========\n");
		sb.append(String.format("§e[Mood FSM]: §f%s §7(Valence: %.2f, Arousal: %.2f)\n",
				mood.name(), 0.20, 0.40));
		sb.append(String.format("§e[Provider Health]: §bGemini (%s) §7| §cGroq (%s)\n",
				geminiState, groqState));
		sb.append(String.format("§e[Semantic Memory]: §a%d Epizod §7| §d%d Persona Notu\n",
				epCount, personaCount));
		sb.append(String.format("§e[Utility AI Best]: §a%s §7(Skor: %.2f)\n",
				decision.action().name(), decision.score()));
		sb.append("§6=====================================================");

		player.sendSystemMessage(Component.literal(sb.toString()));
	}

	public static void clear(UUID playerUuid) {
		if (playerUuid != null) {
			DEBUG_PLAYERS.remove(playerUuid);
		}
	}
}
