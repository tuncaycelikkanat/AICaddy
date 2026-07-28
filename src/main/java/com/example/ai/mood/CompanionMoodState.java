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
}
