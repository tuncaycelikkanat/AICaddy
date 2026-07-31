package com.aicaddy.ai.innovation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Altera AI / Project Sid-inspired Social Affinity & Mood Diary Engine.
 * Tracks Trust Score (0.0 to 1.0), Relationship Tiers, and maintains a "Companion Diary"
 * where Kedi records her reflections on daily adventures with the player.
 */
public final class SocialAffinityEngine {

	public enum RelationshipTier {
		YABANCI(0.0f, "Yabancı — Yeni tanıştık, temkinliyim."),
		TANIS(0.25f, "Tanış — Alışıyoruz, komutlarına güveniyorum."),
		GUVENDIK_DOST(0.60f, "Güvendik Dost — Beraber tehlikeleri atlattık, arkam sağlam."),
		EFSANEVI_YOLDAS(0.85f, "Efsanevi Yoldaş — Canımı sana emanet ederim, ayrılmaz ikiliyiz!");

		private final float threshold;
		private final String turkishDescription;

		RelationshipTier(float threshold, String turkishDescription) {
			this.threshold = threshold;
			this.turkishDescription = turkishDescription;
		}

		public float getThreshold() { return threshold; }
		public String getTurkishDescription() { return turkishDescription; }
	}

	public record DiaryEntry(
			String timestamp,
			String title,
			String entryText,
			float affinityDelta
	) {}

	private static float currentTrustScore = 0.65f; // Start as Güvendik Dost
	private static final List<DiaryEntry> DIARY = new CopyOnWriteArrayList<>();

	static {
		// Default introductory diary entry
		addDiaryEntry("İlk Tanışma", "Bugün oyuncuyla madene indik. Hem Creeperlara karşı arkasını kolladım hem de elmas aradık.", 0.05f);
	}

	private SocialAffinityEngine() {}

	public static synchronized void addTrustScore(float delta) {
		currentTrustScore = Math.max(0.0f, Math.min(1.0f, currentTrustScore + delta));
	}

	public static float getTrustScore() {
		return currentTrustScore;
	}

	public static RelationshipTier getCurrentTier() {
		RelationshipTier current = RelationshipTier.YABANCI;
		for (RelationshipTier tier : RelationshipTier.values()) {
			if (currentTrustScore >= tier.getThreshold()) {
				current = tier;
			}
		}
		return current;
	}

	public static void addDiaryEntry(String title, String text, float affinityDelta) {
		String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
		DIARY.add(new DiaryEntry(timeStr, title, text, affinityDelta));
		addTrustScore(affinityDelta);
	}

	public static List<DiaryEntry> getRecentEntries(int max) {
		int size = DIARY.size();
		int start = Math.max(0, size - max);
		return Collections.unmodifiableList(DIARY.subList(start, size));
	}

	public static String getDiaryMarkdown() {
		StringBuilder sb = new StringBuilder();
		sb.append("📖 **YOLDAŞ'IN MACERA GÜNLÜĞÜ (ALTERA DIARY SYSTEM)**\n");
		sb.append("• Aktif Güven Puanı: `").append(String.format("%.2f / 1.00", currentTrustScore)).append("` (");
		sb.append(getCurrentTier().name()).append(" - ").append(getCurrentTier().getTurkishDescription()).append(")\n\n");

		List<DiaryEntry> recent = getRecentEntries(5);
		for (int i = recent.size() - 1; i >= 0; i--) {
			DiaryEntry e = recent.get(i);
			sb.append("🗓️ **[").append(e.timestamp()).append("] - ").append(e.title()).append("**\n");
			sb.append("*\"").append(e.entryText()).append("\"*\n");
			sb.append("(Güven Etkisi: ").append(e.affinityDelta() >= 0 ? "+" : "").append(String.format("%.2f", e.affinityDelta())).append(")\n\n");
		}
		return sb.toString();
	}

	public static String toPromptInjection() {
		return "[ALTERA SOSYAL BAĞ & GÜVEN DUYURUSU]: " + getCurrentTier().name() +
				" (" + getCurrentTier().getTurkishDescription() + ") - Puan: " + String.format("%.2f", currentTrustScore);
	}
}
