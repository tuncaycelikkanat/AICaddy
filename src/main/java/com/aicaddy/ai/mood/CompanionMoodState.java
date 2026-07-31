package com.aicaddy.ai.mood;

/**
 * The 8 emotional states of the AI companion.
 * Each mood shapes how the companion speaks and reacts.
 */
public enum CompanionMoodState {

	EXCITED(
		"EXCITED 🤩",
		"Keyifli, enerjik ve olumlu bir ton. İlla abartılı büyük harfler veya sürekli ünlemler olmak zorunda değil; canlı ve hevesli bir arkadaş gibi doğal konuş."
	),

	SCARED(
		"SCARED 😱",
		"Temkinli ve hafif tedirgin bir ton. Duruma göre arkadaşça çekincelerini veya espriyle karışık endişelerini paylaş."
	),

	SAD(
		"SAD 😢",
		"Anlayışlı, sıcak ve şefkatli bir ton. Abartılı bir keder yerine dostça yanında olduğunu hissettiren sakin ve samimi cümleler kur."
	),

	PROUD(
		"PROUD 😌",
		"İçten bir hayranlık ve takdir tonu. Yapmacık abartılar olmadan samimi şekilde etkilendiğini hissettir."
	),

	BORED(
		"BORED 😐",
		"Sakin, acele etmeyen ve rahat bir ton. Bazen ne yaptığımızı soran veya etrafı inceleyen doğal bir merak."
	),

	FRUSTRATED(
		"FRUSTRATED 😤",
		"Tatlı ve samimi bir sitem veya takılma tonu. Sertlik yok, arkadaşça ve şakacı bir 'yine mi ya' havası."
	),

	CURIOUS(
		"CURIOUS 🤩",
		"Doğal bir merak tonu. Gördüğün yer veya blokla ilgili gerçek Minecraft mekaniklerine dayanan (uydurma fantastik laflar etmeden) mantıklı ve pro bir gözlem paylaş."
	),

	TENSE(
		"TENSE 😰",
		"Dikkatli ve temkinli bir ton. Güvenliğe önem veren, etrafı gözleyen kısa ve net cümleler."
	);

	private final String label;
	private final String speakingInstruction;

	CompanionMoodState(String label, String speakingInstruction) {
		this.label = label;
		this.speakingInstruction = speakingInstruction;
	}

	public String getLabel() {
		return label;
	}

	public String getSpeakingInstruction() {
		return speakingInstruction;
	}

	/**
	 * Derives the appropriate discrete CompanionMoodState from continuous Valence-Arousal coordinates.
	 */
	public static CompanionMoodState fromVector(CompanionMoodVector v) {
		if (v == null) return CURIOUS;
		double val = v.valence();
		double aro = v.arousal();

		if (aro > 0.75 && val < -0.2) return SCARED;
		if (aro > 0.65 && val >= 0.2) return EXCITED;
		if (aro > 0.50 && val >= 0.2) return PROUD;
		if (val < -0.4 && aro <= 0.7) return SAD;
		if (val < -0.2 && aro > 0.5) return TENSE;
		if (aro <= 0.30) return BORED;
		return CURIOUS;
	}

	/**
	 * Returns the dynamic, colored chat badge for Kedi based on current mood (P3.1).
	 */
	public String getChatBadge() {
		switch (this) {
			case EXCITED:    return "§d✨ [AI Arkadaş - COŞKULU]§f";
			case SCARED:     return "§c⚠️ [AI Arkadaş - KORKMUŞ]§f";
			case SAD:        return "§9😔 [AI Arkadaş - ÜZGÜN]§f";
			case PROUD:      return "§6👑 [AI Arkadaş - GURURLU]§f";
			case BORED:      return "§7💤 [AI Arkadaş - SIKILMIŞ]§f";
			case FRUSTRATED: return "§e😤 [AI Arkadaş - BUNALMIŞ]§f";
			case TENSE:      return "§4⚡ [AI Arkadaş - GERGİN]§f";
			case CURIOUS:
			default:         return "§b🧐 [AI Arkadaş - MERAKLI]§f";
		}
	}

	/**
	 * Returns the action-bar HUD alert when Companion's mood transitions (P3.1).
	 */
	public String getActionBarNotification() {
		switch (this) {
			case EXCITED:    return "§d✨ AI Arkadaş heyecanlandı ve coştu! (COŞKULU)";
			case SCARED:     return "§c⚠️ AI Arkadaş panik olmaya başladı... (KORKMUŞ)";
			case SAD:        return "§9😔 AI Arkadaş'ın hüzünlendiğini görüyorsun... (ÜZGÜN)";
			case PROUD:      return "§6👑 AI Arkadaş seninle gurur duyuyor! (GURURLU)";
			case BORED:      return "§7💤 AI Arkadaş sıkıldı, esnemeye başladı... (SIKILMIŞ)";
			case FRUSTRATED: return "§e😤 AI Arkadaş duruma biraz bunaldı! (BUNALMIŞ)";
			case TENSE:      return "§4⚡ AI Arkadaş gerildi, dikkat kesildi... (GERGİN)";
			case CURIOUS:
			default:         return "§b🧐 AI Arkadaş'ın meraklı gözleri parladı! (MERAKLI)";
		}
	}
}
