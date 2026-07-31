package com.aicaddy.ai.innovation;

/**
 * Baritone-inspired Threat-Aware Voxel Safety Grid.
 * Evaluates the terrain surrounding the cat and player, assigning movement costs
 * to hazardous voxels (Lava, Cliffs, Sonic Warden zones) to prevent suicidal pathfinding.
 */
public final class TacticalVoxelMap {

	public enum HazardType {
		CLEAR(1.0f, "Temiz Blok - Güvenli geçiş"),
		WATER_OBSTACLE(2.5f, "Su Bloğu - Yavaşlatıcı engel"),
		FALL_CLIFF(50.0f, "Yüksek Uçurum - Düşme tehlikesi"),
		WARDEN_SONIC_ZONE(80.0f, "Warden Sonik Alanı - Ölümcül akustik risk"),
		LAVA_POOL(100.0f, "Lav Havuzu - Geçilemez ölüm riski");

		private final float cost;
		private final String description;

		HazardType(float cost, String description) {
			this.cost = cost;
			this.description = description;
		}

		public float getCost() { return cost; }
		public String getDescription() { return description; }
	}

	public record VoxelCell(int dx, int dy, int dz, HazardType hazardType, float calculatedCost) {}

	private TacticalVoxelMap() {}

	/**
	 * Computes path safety score (0.0 = lethal, 1.0 = perfectly safe) for a target offset.
	 */
	public static float evaluatePathSafety(int targetX, int targetY, int targetZ, HazardType detectedHazard) {
		float baseCost = detectedHazard.getCost();
		if (baseCost >= 100.0f) {
			return 0.0f; // Completely unsafe
		}
		// Scale safety inversely with cost
		return Math.max(0.0f, 1.0f - (baseCost / 100.0f));
	}

	public static String toPromptSafetyReport(HazardType forwardHazard) {
		if (forwardHazard == HazardType.CLEAR) {
			return "[VOXEL GÜVENLİK HARİTASI]: İleri yön temiz (Maliyet: 1.0) - Güvenli ilerleyiş.";
		}
		return "[VOXEL GÜVENLİK ALARMI (BARITONE MATRIX)]: İleri yönde " + forwardHazard.name() +
				" (" + forwardHazard.getDescription() + ") tespit edildi! Rota maliyeti: " + forwardHazard.getCost();
	}
}
