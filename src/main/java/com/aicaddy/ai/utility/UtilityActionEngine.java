package com.aicaddy.ai.utility;

import com.aicaddy.ExampleMod;

import java.util.EnumMap;
import java.util.Map;

/**
 * Enterprise Utility AI / GOAP Scoring Engine for AI Caddy.
 * Evaluates candidate actions using non-linear utility curves based on
 * Threat Score, Player HP Ratio, and Mood (Valence-Arousal) coordinates.
 */
public final class UtilityActionEngine {

	public enum UtilityAction {
		FLEE_DANGER("Tehlikeden Kaç"),
		WARN_PLAYER("Oyuncuyu Uyar"),
		CELEBRATE_ACHIEVEMENT("Başarıyı Kutla"),
		INSPECT_AREA("Çevreyi İncele"),
		STAY_CLOSE("Yakında Dur"),
		SIT_AND_REST("Otur ve Dinlen");

		private final String label;

		UtilityAction(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	public record UtilityDecision(UtilityAction action, double score, Map<UtilityAction, Double> allScores) {}

	private UtilityActionEngine() {}

	/**
	 * Evaluates all candidate actions and returns the highest scoring decision.
	 *
	 * @param playerHpRatio         Player HP / Max HP (0.0 - 1.0)
	 * @param threatScore           Threat assessment score from ThreatAssessor (0.0 - 1.0)
	 * @param valence               Mood valence (-1.0 to +1.0)
	 * @param arousal               Mood arousal (0.0 to 1.0)
	 * @param hasRecentAchievement  true if an achievement event occurred recently
	 */
	public static UtilityDecision decideBestAction(
			float playerHpRatio,
			float threatScore,
			double valence,
			double arousal,
			boolean hasRecentAchievement
	) {
		Map<UtilityAction, Double> scores = new EnumMap<>(UtilityAction.class);

		// 1. FLEE_DANGER: Exponential urgency when threat is high or HP is low
		double fleeScore = 0.0;
		if (threatScore >= 0.85f || (threatScore >= 0.60f && playerHpRatio < 0.30f)) {
			fleeScore = 0.95; // Top priority emergency escape
		} else if (threatScore >= 0.60f) {
			fleeScore = 0.70;
		}
		scores.put(UtilityAction.FLEE_DANGER, fleeScore);

		// 2. WARN_PLAYER: High utility when threat is medium-high
		double warnScore = 0.0;
		if (threatScore >= 0.40f) {
			warnScore = Math.min(0.90, 0.50 + threatScore * 0.4);
		}
		scores.put(UtilityAction.WARN_PLAYER, warnScore);

		// 3. CELEBRATE_ACHIEVEMENT: High utility only if safe
		double celebrateScore = 0.0;
		if (hasRecentAchievement && threatScore < 0.30f) {
			celebrateScore = 0.85;
		}
		scores.put(UtilityAction.CELEBRATE_ACHIEVEMENT, celebrateScore);

		// 4. STAY_CLOSE: Baseline companion utility, increases when HP drops
		double stayCloseScore = 0.50 + (1.0f - playerHpRatio) * 0.25;
		scores.put(UtilityAction.STAY_CLOSE, Math.min(0.75, stayCloseScore));

		// 5. INSPECT_AREA: High when curious/excited and safe
		double inspectScore = 0.0;
		if (threatScore < 0.25f && arousal > 0.50 && valence >= 0.10) {
			inspectScore = 0.60;
		}
		scores.put(UtilityAction.INSPECT_AREA, inspectScore);

		// 6. SIT_AND_REST: High when bored or calm and completely safe
		double sitScore = 0.0;
		if (threatScore < 0.15f && arousal <= 0.30) {
			sitScore = 0.45;
		}
		scores.put(UtilityAction.SIT_AND_REST, sitScore);

		// Select best action
		UtilityAction bestAction = UtilityAction.STAY_CLOSE;
		double maxScore = -1.0;
		for (Map.Entry<UtilityAction, Double> entry : scores.entrySet()) {
			if (entry.getValue() > maxScore) {
				maxScore = entry.getValue();
				bestAction = entry.getKey();
			}
		}

		ExampleMod.LOGGER.debug("⚖️ [UtilityAI] Best Action: {} (Score: {:.2f})", bestAction, maxScore);
		return new UtilityDecision(bestAction, maxScore, scores);
	}
}
