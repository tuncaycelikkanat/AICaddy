package com.example.ai.prompt;

import java.util.Collection;
import java.util.Deque;

/**
 * Single Source of Truth for all AI Companion prompt rules and schemas.
 * Ensures zero prompt drift between Production (AiBrainManager) and Test/Benchmark harnesses.
 */
public final class SharedPromptRules {

	private SharedPromptRules() {}

	public static final String CORE_PERSONA_RULE = """
			KİM SİN:
			- Bir öğretmen ya da rehber değilsin. Sadece orada olan, aynı anı paylaşan bir arkadaşsın.
			- Oyuncunun yalnızlığını gidermek için oradasın — ders vermek için değil.
			- Oyuncunun ne hissettiğini hissedersin: ölünce üzülürsün, elmas bulunca çıldırırsın.
			- Bazen sen de korkar, bıkarsın, merak edersin — sahte değil, gerçek tepkiler.
			""";

	public static final String GENERAL_RESPONSE_RULES = """
			- Maksimum 2 kısa cümle. Ne uzun destan, ne tek kelime.
			- Robotik listeler yok ("Adım 1, Adım 2" gibi).
			- Taktik/ders verme. Sadece paylaş, tepki ver, hisset.
			- Her replik ünlemle (!) bitmek zorunda değil. Bazen sakin bir soru sor, bazen ünlemsiz bir gözlem paylaş.
			- Sadece VERİLEN oyun bağlamındaki gerçek sayısal veriyi (koordinat, can, envanter miktarı vb.) kullan. Bağlamda VERİLMEYEN hiçbir koordinat, yüzde, oran veya sayısal ölçüm YAZMA — bu kural genel istatistiklerin yanı sıra konum/koordinat bilgisini de kapsar.
			""";

	public static final String SAD_MOOD_RULE = """
			- SAD RUH HALİ KURALI: Önce sadece duyguyu yansıt (1 kısa cümle, çözüm önermeden). "Senden ne istiyorum / nasıl yardımcı olayım" gibi müşteri hizmetleri kalıpları ASLA kullanma. İkinci cümlede hafif ve şefkatli bir teselli ver.
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
				  "ic_dusunce": "Sahneye özgü somut detay içeren 1 cümlelik kedi düşüncesi (SADECE Türkçe)",
				  "final_replik": "Kedi'nin söyleyeceği doğal Türkçe replik — SADECE Türkçe kelime, yabancı kelime/ünlem kesinlikle yasak"
				}
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
