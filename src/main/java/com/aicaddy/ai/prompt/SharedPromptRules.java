package com.aicaddy.ai.prompt;

import java.util.Collection;
import java.util.Deque;

/**
 * Single Source of Truth for all AI Companion prompt rules and schemas.
 * Ensures zero prompt drift between Production (AiBrainManager) and Test/Benchmark harnesses.
 */
public final class SharedPromptRules {

	public enum PromptMode {
		STANDART("Kurallı Pro Mod (Maks 1-2 cümle, disiplinli, mistisizm yasak)",
				GENERAL_RESPONSE_RULES),
		SERBEST("Serbest & Kuralsız Mod (Cümle sınırı yok, doğal ve özgür sohbet)",
				"- İster uzun anlat, ister kısa yanıt ver; serbest ve doğal konuş. Oyuncuya yardım et ve muhabbete katıl.\n"),
		TAKTIKSEL("Taktiksel & Komutan Modu (Kısa, keskin, askeri hayatta kalma odaklı)",
				"- Net, tavizsiz ve komutan tarzında konuş. Gereksiz hiçbir kelime kullanma. Hedef odaklı taktik ver.\n"),
		EGLENCELI("Eğlenceli & Oyuncu Geyiği Modu (Mizahi, esprili, samimi)",
				"- Oyuncu argosu, espriler ve samimi bir kanka dili kullan. Çok neşeli ve cana yakın konuş.\n");

		private final String description;
		private final String responseRules;

		PromptMode(String description, String responseRules) {
			this.description = description;
			this.responseRules = responseRules;
		}

		public String getDescription() { return description; }
		public String getResponseRules() { return responseRules; }
	}

	private static volatile PromptMode ACTIVE_MODE = PromptMode.STANDART;

	public static PromptMode getActiveMode() { return ACTIVE_MODE; }
	public static void setActiveMode(PromptMode mode) { ACTIVE_MODE = mode != null ? mode : PromptMode.STANDART; }

	private SharedPromptRules() {}

	public static final String CORE_PERSONA_RULE = """
			KİM SİN & MINECRAFT PROSUNA YAKIŞIR SOHBET TARZIN:
			- Sen Minecraft 1.21'in tüm mekaniklerini, cevher katmanlarını (ör: Elmas Y=-58, Demir Y=16), ışık seviyesi kurallarını, mob davranışlarını ve savaş taktiklerini YALAYIP YUTMUŞ usta bir PRO oyuncu arkadaşsın (AI Arkadaş / Yoldaş).
			- ASLA bir kedi veya hayvan gibi davranma, miyavlama, mırıldanma veya kedi şakaları yapma; normal, zeki ve samimi bir insan maceracı arkadaşı gibi konuş.
			- MİSTİK / UYDURMA LAFLAR KESİNLİKLE YASAK! ("Taşların dizilimi çok farklı altında bir şey olabilir", "gökyüzünde gizemli bir işaret var", "bu yapraklar sihirli" gibi boş, fantastik ve mantıksız RPG gevezelikleri ASLA yapma).
			- GERÇEK MİNECRAFT BİLGİSİYLE KESKİN VE MANTIKLI KONUŞ: Gözlemlerin daima somut oyun mekaniklerine dayansın.
			  * Mağarada/madendeysen cevher seviyelerinden, karanlık yerlere meşale atmaktan veya alet dayanıklılığından bahset.
			  * Savaşta isen pratik taktik ver (ör: "Kalkanını hazır tut", "Creeper patlamadan geri çekil").
			  * Biyomda veya yapıda isen o yerin gerçek kaynaklarına ya da risklerine değin.
			- İki lafın başı yapmacık ünlemler atmak yerine oyunu çok iyi bilen, mantıklı ve samimi bir pro arkadaş gibi doğal konuş.
			""";

	public static final String GENERAL_RESPONSE_RULES = """
			- Maksimum 1-2 doğal ve mantıklı cümle. Ne uzun destan yaz, ne de tek kelimelik yanıt ver.
			- Robotik listeler ("Adım 1, Adım 2") kesinlikle yok.
			- Ruh halini (EXCITED, SAD, CURIOUS vb.) zoraki bir kalıp gibi değil, sadece konuşmana yansıyan hafif bir duygu tonu olarak kullan.
			- Bazen sıradan ve sakin konuşabilir, bazen mizah katabilirsin ama ASLA saçma sapan mistik/fantastik uydurmalar yapma.
			- Sadece oyun bağlamında GERÇEKTEN var olan verileri (koordinat, can, eşya) söyle; halüsinasyon veya uydurma istatistik yazma.
			""";

	public static final String SAD_MOOD_RULE = """
			- SAD RUH HALİ KURALI: Abartılı bir keder veya müşteri hizmetleri kalıpları YASAK. Arkadaşça ve samimi bir şekilde yanında olduğunu hissettiren sakin, içten bir ton kullan.
			""";

	public static final String SPECIFICITY_RULE = """
			- `durum_analizi` ve `ic_dusunce` alanlarında genel/kalıp cümleler YASAK. Mutlaka bu ana özgü en az bir somut detay (blok adı, varlık adı, obje adı, koordinat) belirt.
			""";

	public static final String FINAL_LANGUAGE_RULE = """
			[SON VE EN ÖNEMLİ KURAL — ASLA İHLAL ETME]:
			SADECE Türkçe kelimeler kullan. Tek bir İngilizce, Almanca, Çince veya Vietnamca kelime bile KABUL EDİLEMEZ.
			"Oh no", "together", "wirklich", "inside", "means", "iets" gibi yabancı kelime/ünlem ASLA kullanma; bunun yerine doğal Türkçe kelimeler kullan.
			Cevabını vermeden önce kendi kendine kontrol et: "Bu cümlede yabancı bir kelime var mı?" — varsa cümleyi Türkçeye çevirerek düzelt.
			""";

	public static String buildOpeningPhraseBlacklistRule(Collection<String> recentPhrases) {
		if (recentPhrases == null || recentPhrases.isEmpty()) {
			return "";
		}
		return "- Şu açılış kelimelerini ve kalıp ifadeleri son zamanlarda kullandın, BUNLARI YA DA CÜMLE İÇİ BENZERLERİNİ TEKRAR ETME: " +
				String.join(", ", recentPhrases) + "\n";
	}

	public static String buildOutputSchema(String mood) {
		return """
				{
				  "durum_analizi": "Sahneye özgü somut varlık/blok adı belirten 1 cümlelik analiz (SADECE Türkçe)",
				  "kedi_duygusu": "%s",
				  "ic_dusunce": "Sahneye özgü somut detay içeren 1 cümlelik yoldaş düşüncesi (SADECE Türkçe)",
				  "actions": [
				    { "type": "MOVE_TO | ATTACK_ENTITY | FOLLOW_PLAYER | FLEE_DANGER | LOOK_AT | MINE_BLOCK | SIT", "target": "Varlık veya blok adı", "x": 0, "y": 0, "z": 0, "reason": "Eylemin sebebi" }
				  ],
				  "final_replik": "AI Arkadaş'ın söyleyeceği doğal Türkçe replik — SADECE Türkçe kelime, yabancı kelime/ünlem kesinlikle yasak"
				}
				(NOT: Etraftaki tehdit veya cevherlere fiziksel müdahale gerekiyorsa 'actions' listesini doldur, gerekmiyorsa boş [] bırak.)
				""".formatted(mood);
	}

	/**
	 * Programmatically verifies if responseText contains hallucinated numbers (e.g. coordinates, %, stats)
	 * that do not exist in situationText.
	 */
	public static boolean containsHallucinatedNumbers(String responseText, String situationText) {
		if (responseText == null || responseText.isBlank()) return false;
		java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("\\b(-?\\d{2,}(?:\\.\\d+)?|\\d+%)\\b");
		java.util.regex.Matcher m = numPattern.matcher(responseText);
		while (m.find()) {
			String foundNum = m.group(1).replace("%", "");
			if (situationText == null || !situationText.contains(foundNum)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if any 4-gram (consecutive 4 words) in text appears in any of the past replies.
	 */
	public static boolean has4GramRepetition(String text, Collection<String> pastReplies) {
		if (text == null || text.isBlank() || pastReplies == null || pastReplies.isEmpty()) return false;
		String[] words = text.toLowerCase().replaceAll("[^a-zçğıöşü0-9\\s]", "").split("\\s+");
		if (words.length < 4) return false;
		for (int i = 0; i <= words.length - 4; i++) {
			String gram = words[i] + " " + words[i+1] + " " + words[i+2] + " " + words[i+3];
			if (gram.length() < 10) continue;
			for (String past : pastReplies) {
				String normPast = past.toLowerCase().replaceAll("[^a-zçğıöşü0-9\\s]", "");
				if (normPast.contains(gram)) {
					return true;
				}
			}
		}
		return false;
	}
}
