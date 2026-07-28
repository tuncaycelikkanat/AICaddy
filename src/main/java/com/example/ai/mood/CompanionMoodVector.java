package com.example.ai.mood;

/**
 * Continuous emotional mood representation based on psychological Valence-Arousal model.
 *
 * @param valence -1.0 (very unpleasant / sad / scared) to 1.0 (very pleasant / happy / excited)
 * @param arousal  0.0 (calm / bored / sleepy) to 1.0 (very excited / panicked / high energy)
 */
public record CompanionMoodVector(double valence, double arousal) {

	public CompanionMoodVector {
		valence = Math.max(-1.0, Math.min(1.0, valence));
		arousal = Math.max(0.0, Math.min(1.0, arousal));
	}

	public static CompanionMoodVector defaultHappy() {
		return new CompanionMoodVector(0.5, 0.4);
	}

	/**
	 * Applies exponential smoothing toward a target mood vector.
	 *
	 * @param target The target mood triggered by a game event
	 * @param alpha  Smoothing factor (0.0 to 1.0; e.g. 0.4 means 40% target, 60% previous)
	 */
	public CompanionMoodVector smoothToward(CompanionMoodVector target, double alpha) {
		double v = this.valence * (1.0 - alpha) + target.valence * alpha;
		double a = this.arousal * (1.0 - alpha) + target.arousal * alpha;
		return new CompanionMoodVector(v, a);
	}
}
