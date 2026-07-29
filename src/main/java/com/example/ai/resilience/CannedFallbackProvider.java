package com.example.ai.resilience;

import com.example.ai.mood.CompanionMoodEngine;
import com.example.ai.mood.CompanionMoodState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Offline "Canned" Fallback Provider.
 * Provides 16 immersive, 100% Turkish responses categorized by emotional mood.
 * Used automatically when a provider's CircuitBreaker opens (e.g., after 3 consecutive failures),
 * preventing robot-like API error messages and maintaining immersion.
 */
public class CannedFallbackProvider {

	private static final Map<String, List<String>> CANNED_HAVUZU = new ConcurrentHashMap<>();
	private static final Random RANDOM = new Random();

	static {
		CANNED_HAVUZU.put("SCARED", Arrays.asList(
				"Burası biraz fazla sessiz ve karanlık... Sırtımı sana yaslıyorum dostum, dikkatli olalım.",
				"Tüylerim diken diken oldu, etraftan tuhaf sesler geliyor. Hemen bir meşale koyalım mı?"
		));

		CANNED_HAVUZU.put("EXCITED", Arrays.asList(
				"Gözlerim parlıyor! Burada harika bir macera kokusu var, hadi hemen keşfedelim!",
				"Şu an içim kıpır kıpır! Ne bulduğumuzu görmek için sabırsızlanıyorum."
		));

		CANNED_HAVUZU.put("SAD", Arrays.asList(
				"Canın sağ olsun dostum... Bazen işler ters gidebilir ama yanındayım, birlikte toparlarız.",
				"O kadar emeğe üzüldüm... Ama sen vazgeçmezsin biliyorum, ben buradayım."
		));

		CANNED_HAVUZU.put("PROUD", Arrays.asList(
				"Seninle gurur duyuyorum! Gerçekten usta işi bir hamleydi.",
				"Vay canına, bunu her oyuncu başaramaz! Harika iş çıkardın dostum."
		));

		CANNED_HAVUZU.put("BORED", Arrays.asList(
				"Biraz durgunlaştık sanki... Etrafta yapacak yeni bir çılgınlık yok mu?",
				"Kuyruğumu kovalayacak hale geldim, hadi yeni bir şeyler inşa edelim ya da keşfe çıkalım!"
		));

		CANNED_HAVUZU.put("FRUSTRATED", Arrays.asList(
				"Sakin olalım dostum, derin bir nefes alıp tekrar deneyelim. Hal edeceğiz!",
				"Biraz sinir bozucu biliyorum ama pes etmek bize yakışmaz."
		));

		CANNED_HAVUZU.put("CURIOUS", Arrays.asList(
				"Acaba şu köşenin ardında ne saklı? Birlikte gidip bakalım mı?",
				"Bu blokların dizilimi çok ilginç, burada gizli bir şeyler olabilir."
		));

		CANNED_HAVUZU.put("TENSE", Arrays.asList(
				"Adımlarımızı çok dikkatli atalım, havada tehlikeli bir elektrik var.",
				"Gözümü etraftan ayırmıyorum, her an bir şey çıkacakmış gibi hissettiriyor."
		));

		CANNED_HAVUZU.put("DEFAULT", Arrays.asList(
				"Miyav! Seninle yan yana koşmak gerçekten çok keyifli dostum.",
				"Ben hazırım dostum, nereye gidersek gidelim arkandayım."
		));
	}

	/**
	 * Returns an immersive, 100% Turkish canned response for the current companion mood.
	 */
	public static String getCannedFallback() {
		CompanionMoodState currentMood = CompanionMoodEngine.getCurrentMood();
		String moodKey = (currentMood != null) ? currentMood.name() : "DEFAULT";
		List<String> list = CANNED_HAVUZU.getOrDefault(moodKey, CANNED_HAVUZU.get("DEFAULT"));
		if (list == null || list.isEmpty()) {
			return "Miyav! Seninle yan yana koşmak gerçekten çok keyifli dostum.";
		}
		return list.get(RANDOM.nextInt(list.size()));
	}

	/**
	 * Returns a canned fallback response for a specific mood label.
	 */
	public static String getCannedFallbackForMood(String moodLabel) {
		String key = (moodLabel != null && !moodLabel.isEmpty()) ? moodLabel.toUpperCase() : "DEFAULT";
		List<String> list = CANNED_HAVUZU.getOrDefault(key, CANNED_HAVUZU.get("DEFAULT"));
		if (list == null || list.isEmpty()) {
			return "Miyav! Seninle yan yana koşmak gerçekten çok keyifli dostum.";
		}
		return list.get(RANDOM.nextInt(list.size()));
	}
}
