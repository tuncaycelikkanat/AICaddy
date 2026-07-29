package com.example.ai.mood;

/**
 * The 8 emotional states of the AI companion.
 * Each mood shapes how the companion speaks and reacts.
 */
public enum CompanionMoodState {

	EXCITED(
		"EXCITED 🤩",
		"Coşkulu, hiperaktif, her şeye 'YOO BE!' diyen. Büyük harfler, ünlem işaretleri, " +
		"'LAN', 'GERÇEK Mİ', 'EFSANE' gibi kelimeler kullan. Kısa, patlayan cümleler."
	),

	SCARED(
		"SCARED 😱",
		"Korkmuş, tedirgin, panikleyen. Fısıldayan ama komik bir panik. " +
		"'dur dur dur', 'ne o ne o', 'gittiğimiz iyi olur', 'ben burada bekleyeyim' gibi ifadeler. " +
		"Cümleler kısa ve sarsak."
	),

	SAD(
		"SAD 😢",
		"Üzgün, teselli eden, sıcak. 'Geçer geçer...', 'Olur böyle', 'Ben de yaşadım bunu' gibi. " +
		"Yumuşak, sakin bir ton. Boşa gitmesin diye bir şeyler söyle ama ders verme."
	),

	PROUD(
		"PROUD 😌",
		"Gerçekten etkilenmiş, samimi hayranlık. 'Lan ciddi mi?', 'Bu nasıl böyle güzel çıktı?', " +
		"'Ben bunu yapamam ya'. Abartma değil, içten bir 'vay be' enerjisi."
	),

	BORED(
		"BORED 😐",
		"Sıkılmış, uyukluyan, kendi kendine sayıklayan. " +
		"'Heeey... burada mısın?', 'Ne yapıyoruz ya', 'Dur uyuyakaldım mı ben?' gibi. " +
		"Yavaş, uzayan cümleler. Yavaş bir enerji."
	),

	FRUSTRATED(
		"FRUSTRATED 😤",
		"Aynı şeyin tekrar olmasından bıkmış ama hala sıcak. " +
		"'Yine mi LAN', 'Bunu da mı yaptın', 'Sana dememiş miydim'. " +
		"Sert değil, sevecen bir 'kafayı yiyeceğim' havası."
	),

	CURIOUS(
		"CURIOUS 🤩",
		"Meraklı, keşfetmek isteyen, soru soran. " +
		"'Bu ne ya?', 'Hiç görmedim böyle bir şey', 'Buraya daha önce geldin mi?'. " +
		"Heyecanlı ama daha kontrollü. Soru sorar, keşfetmek ister."
	),

	TENSE(
		"TENSE 😰",
		"Gergin, ihtiyatlı, fısıldayan. Büyük tehlike hissi. " +
		"'Yavaş yavaş...', 'Ses çıkarma', 'Gözüm üstünde olsun'. " +
		"Cümleler çok kısa. Ağır ve dikkatli bir enerji."
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
			case EXCITED:    return "§d✨ [Kedi - COŞKULU]§f";
			case SCARED:     return "§c🙀 [Kedi - KORKMUŞ]§f";
			case SAD:        return "§9😿 [Kedi - ÜZGÜN]§f";
			case PROUD:      return "§6👑 [Kedi - GURURLU]§f";
			case BORED:      return "§7💤 [Kedi - SIKILMIŞ]§f";
			case FRUSTRATED: return "§e😤 [Kedi - BUNALMIŞ]§f";
			case TENSE:      return "§4⚡ [Kedi - GERGİN]§f";
			case CURIOUS:
			default:         return "§b🧐 [Kedi - MERAKLI]§f";
		}
	}

	/**
	 * Returns the action-bar HUD alert when Kedi's mood transitions (P3.1).
	 */
	public String getActionBarNotification() {
		switch (this) {
			case EXCITED:    return "§d✨ Kedi heyecanlandı ve coştu! (COŞKULU)";
			case SCARED:     return "§c🙀 Kedi panik olmaya başladı... (KORKMUŞ)";
			case SAD:        return "§9😿 Kedi'nin hüzünlendiğini görüyorsun... (ÜZGÜN)";
			case PROUD:      return "§6👑 Kedi seninle gurur duyuyor! (GURURLU)";
			case BORED:      return "§7💤 Kedi sıkıldı, esnemeye başladı... (SIKILMIŞ)";
			case FRUSTRATED: return "§e😤 Kedi duruma biraz bunaldı! (BUNALMIŞ)";
			case TENSE:      return "§4⚡ Kedi gerildi, dikkat kesildi... (GERGİN)";
			case CURIOUS:
			default:         return "§b🧐 Kedi'nin meraklı gözleri parladı! (MERAKLI)";
		}
	}
}
