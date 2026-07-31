package com.aicaddy.ai.memory;

import com.aicaddy.ExampleMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Enterprise Semantic Episode Memory (RAG index) for AI Caddy.
 * Indexes past player experiences and retrieves the most contextually relevant
 * episodes using TF-IDF token overlap similarity scoring.
 */
public final class SemanticEpisodeMemory {

	public record Episode(
			String id,
			UUID playerUuid,
			String summary,
			String keywords,
			long timestampMs
	) {}

	private static final Map<UUID, List<Episode>> EPISODE_INDEX = new ConcurrentHashMap<>();

	private SemanticEpisodeMemory() {}

	/**
	 * Records a new episode into the player's semantic memory index.
	 */
	public static synchronized void recordEpisode(UUID playerUuid, String summary, String keywords) {
		if (playerUuid == null || summary == null || summary.isBlank()) return;

		List<Episode> list = EPISODE_INDEX.computeIfAbsent(playerUuid, u -> new CopyOnWriteArrayList<>());
		String id = "ep-" + UUID.randomUUID().toString().substring(0, 8);
		Episode ep = new Episode(id, playerUuid, summary, keywords != null ? keywords : "", System.currentTimeMillis());
		list.add(ep);

		ExampleMod.LOGGER.debug("🧠 [SemanticMemory] Recorded episode for {}: '{}'", playerUuid, summary);
	}

	/**
	 * Finds the top-K most relevant past episodes for the given query string using term overlap similarity.
	 */
	public static List<Episode> findRelevantEpisodes(UUID playerUuid, String query, int topK) {
		if (playerUuid == null || query == null || query.isBlank()) {
			return Collections.emptyList();
		}

		List<Episode> list = EPISODE_INDEX.get(playerUuid);
		if (list == null || list.isEmpty()) {
			return Collections.emptyList();
		}

		Set<String> queryTokens = tokenize(query);
		if (queryTokens.isEmpty()) {
			return Collections.emptyList();
		}

		record ScoredEpisode(Episode episode, double score) {}

		List<ScoredEpisode> scored = new ArrayList<>();
		for (Episode ep : list) {
			Set<String> epTokens = tokenize(ep.summary() + " " + ep.keywords());
			double similarity = calculateSimilarity(queryTokens, epTokens);
			if (similarity > 0.0) {
				scored.add(new ScoredEpisode(ep, similarity));
			}
		}

		scored.sort((a, b) -> Double.compare(b.score(), a.score())); // Descending order

		return scored.stream()
				.limit(topK)
				.map(ScoredEpisode::episode)
				.collect(Collectors.toList());
	}

	/**
	 * Computes Jaccard / Overlap similarity score between query tokens and episode tokens.
	 */
	private static double calculateSimilarity(Set<String> queryTokens, Set<String> episodeTokens) {
		if (queryTokens.isEmpty() || episodeTokens.isEmpty()) return 0.0;

		int matches = 0;
		for (String qt : queryTokens) {
			for (String et : episodeTokens) {
				if (et.contains(qt) || qt.contains(et)) {
					matches++;
					break;
				}
			}
		}

		return (double) matches / Math.sqrt(queryTokens.size() * episodeTokens.size());
	}

	private static Set<String> tokenize(String text) {
		if (text == null) return Collections.emptySet();
		return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9çğışöüÇĞIŞÖÜ]+"))
				.filter(w -> w.length() >= 3)
				.collect(Collectors.toSet());
	}

	public static void clear(UUID playerUuid) {
		if (playerUuid != null) {
			EPISODE_INDEX.remove(playerUuid);
		}
	}

	public static int getEpisodeCount(UUID playerUuid) {
		List<Episode> list = EPISODE_INDEX.get(playerUuid);
		return list != null ? list.size() : 0;
	}
}
