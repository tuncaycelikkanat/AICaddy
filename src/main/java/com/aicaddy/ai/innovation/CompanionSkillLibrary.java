package com.aicaddy.ai.innovation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NVIDIA Voyager-inspired Skill Library for AI Caddy.
 * Stores reusable, verified multi-step tactical skill macros that Kedi can invoke
 * instead of re-deriving basic survival/combat sequences from scratch every time.
 */
public final class CompanionSkillLibrary {

	public record SkillMacro(
			String skillId,
			String name,
			String description,
			List<String> actionSequence,
			String requiredGamePhase
	) {}

	private static final Map<String, SkillMacro> SKILLS = new ConcurrentHashMap<>();

	static {
		registerDefaultSkills();
	}

	private CompanionSkillLibrary() {}

	private static void registerDefaultSkills() {
		SKILLS.put("EMERGENCY_CREEPER_DEFENSE", new SkillMacro(
				"EMERGENCY_CREEPER_DEFENSE",
				"Creeper Acil Savunma Taktik Sekansı",
				"Creeper temas mesafesine girdiğinde kalkan açıp geri sıçrayarak patlamayı savuşturma.",
				List.of("RAISE_SHIELD", "DODGE_BACKWARD_8M", "WAIT_FOR_EXPLOSION", "ATTACK_REMAINING"),
				"EARLY"
		));

		SKILLS.put("SAFE_DIAMOND_MINING", new SkillMacro(
				"SAFE_DIAMOND_MINING",
				"Güvenli Elmas Kazı Sekansı",
				"Y=-58 katmanında elması kırmadan önce çevre blokları ve altındaki lav ihtimalini denetleme.",
				List.of("SCAN_SURROUNDING_VOXELS", "CHECK_LAVA_BELOW", "PLACE_TORCH", "MINE_BLOCK_SAFELY"),
				"MID"
		));

		SKILLS.put("NETHER_FORTRESS_ASSAULT", new SkillMacro(
				"NETHER_FORTRESS_ASSAULT",
				"Nether Kalesi Blaze Taarruzu",
				"Ateş topundan korunarak koridor ağızlarını siper alıp Blaze çubuğu avlama.",
				List.of("TAKE_CORRIDOR_COVER", "DEFLECT_FIREBALL", "ENGAGE_MELEE", "COLLECT_RODS"),
				"MID"
		));

		SKILLS.put("SCOUT_AND_LIGHT", new SkillMacro(
				"SCOUT_AND_LIGHT",
				"Öncü Mağara Aydınlatma",
				"10 blok önden giderek karanlık mağara köşelerine meşale yerleştirme.",
				List.of("ADVANCE_10M", "DETECT_DARK_CORNERS", "PLACE_TORCH", "REPORT_MOB_SIGNALS"),
				"EARLY"
		));
	}

	public static Optional<SkillMacro> getSkill(String skillId) {
		return Optional.ofNullable(SKILLS.get(skillId.toUpperCase()));
	}

	public static Collection<SkillMacro> getAllSkills() {
		return Collections.unmodifiableCollection(SKILLS.values());
	}

	public static void registerCustomSkill(SkillMacro macro) {
		SKILLS.put(macro.skillId().toUpperCase(), macro);
	}

	public static String toPromptSummary() {
		StringBuilder sb = new StringBuilder("[ÖĞRENİLEN OTOMATİK BECERİ KÜTÜPHANESİ (VOYAGER SKILLS)]:\n");
		for (SkillMacro sm : SKILLS.values()) {
			sb.append("- ").append(sm.skillId()).append(" (").append(sm.name()).append("): ")
					.append(sm.description()).append("\n");
		}
		return sb.toString();
	}
}
