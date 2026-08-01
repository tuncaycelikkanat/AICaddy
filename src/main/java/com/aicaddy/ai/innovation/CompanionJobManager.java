package com.aicaddy.ai.innovation;

import java.util.Optional;

/**
 * Minecraft Comes Alive (MCA) & Millénaire-inspired Persistent Autonomous Job System.
 * Allows the player to assign long-running jobs (Guard Area, Mining Assistant, Scout Ahead)
 * that Kedi executes autonomously until cancelled or changed.
 */
public final class CompanionJobManager {

	public enum CompanionJob {
		IDLE("SERBEST_TAKIL", "Serbest Mod — Oyuncuyu takip et veya dinlen."),
		GUARD_AREA("ALAN_NOBETCISI", "Alan Nöbetçisi — Bulunduğun 10 blok çapında nöbet tut, yaklaşan mobları avla."),
		MINING_ASSISTANT("MADEN_YAVERI", "Maden Yaveri — Oyuncu kazarken arkasında dur, yere düşen ganimetleri topla ve meşale at."),
		SCOUT_AHEAD("ONCU_KESIFCI", "Öncü Keşifçi — 15 blok önden ilerle, mağara köşelerini tara ve tehlikeleri önceden bildir."),
		ARCHER_DEFENDER("OKCU_SAVUNMACI", "Okçu Savunmacı — Yüksek bir noktada konuşlan, oyuncuya yaklaşan moblara yayla uzaktan koruma ateşi aç."),
		AUTO_TORCHER("OTOMATIK_TORCCU", "Otomatik Torççu — Çevredeki karanlık (ışık seviyesi <= 7) blokları tespit et ve meşale koyarak canavar doğmasını engelle."),
		BUILDER_ASSISTANT("INSAATCI", "İnşaatçı — Acil durum barınağı şablonuna göre oyuncunun etrafını bloklarla koruma altına al ve sığınak inşa et.");

		private final String id;
		private final String description;

		CompanionJob(String id, String description) {
			this.id = id;
			this.description = description;
		}

		public String getId() { return id; }
		public String getDescription() { return description; }

		public static Optional<CompanionJob> fromId(String input) {
			for (CompanionJob job : values()) {
				if (job.getId().equalsIgnoreCase(input) || job.name().equalsIgnoreCase(input)) {
					return Optional.of(job);
				}
			}
			return Optional.empty();
		}
	}

	private static volatile CompanionJob activeJob = CompanionJob.IDLE;

	private CompanionJobManager() {}

	public static synchronized void setActiveJob(CompanionJob job) {
		activeJob = job;
		// Record in Social Affinity Diary when player assigns a new job!
		SocialAffinityEngine.addDiaryEntry(
				"Yeni Görev: " + job.getId(),
				"Oyuncu bana yeni bir otonom görev atadı: " + job.getDescription(),
				0.05f
		);
	}

	public static CompanionJob getActiveJob() {
		return activeJob;
	}

	public static String toPromptInjection() {
		return "[OTONOM GÖREV / İŞ ATAMASI (MCA JOB SYSTEM)]: Aktif Görev: " +
				activeJob.getId() + " (" + activeJob.getDescription() + ")";
	}
}
