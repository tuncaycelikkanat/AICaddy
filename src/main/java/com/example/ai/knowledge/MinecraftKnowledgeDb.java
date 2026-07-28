package com.example.ai.knowledge;

import java.util.HashMap;
import java.util.Map;

/**
 * Static knowledge reference database for Minecraft 1.20+ mechanics.
 * Prevents LLM hallucinations by injecting absolute truth into the prompt.
 */
public class MinecraftKnowledgeDb {

	private static final Map<String, String> KNOWLEDGE_MAP = new HashMap<>();

	static {
		KNOWLEDGE_MAP.put("elmas", "Elmas en sık Y = -54 ile Y = -58 katları arasında bulunur. SADECE Demir, Elmas veya Netherite kazmayla kırılabilir. Tahta, Taş veya Altın kazmayla kırılırsa eşya YOK OLUR.");
		KNOWLEDGE_MAP.put("diamond", "Elmas en sık Y = -54 ile Y = -58 katları arasında bulunur. SADECE Demir veya Elmas kazmayla kırılabilir.");
		KNOWLEDGE_MAP.put("demir", "Demir cevheri en sık Y = 16 ile Y = 232 katlarında bulunur. Kırmak için en az Taş Kazma gerekir. Kullanmak için fırında veya maden fırınında eritilmelidir.");
		KNOWLEDGE_MAP.put("iron", "Demir cevheri en sık Y = 16 katında bulunur ve Taş Kazma ile kırılır.");
		KNOWLEDGE_MAP.put("nether", "Nether geçidi için en az 10 obsidyen gerekir (3x2 iç boşluk). Kova ile lav ve su buluşturularak elmas kazmasız da kapı yapılabilir.");
		KNOWLEDGE_MAP.put("ejderha", "Ender Ejderhası için: Nether'da Blaze çubuğu (Blaze tozu) + Piglin takasıyla Ender İncisi = Ender Gözü yapılır. Ejderha yatak patlatılarak en hızlı avlanır.");
		KNOWLEDGE_MAP.put("speedrun", "Speedrun önceliği: 3 demir bul (kova yap) -> Lav havuzundan Nether geçidi aç -> Piglin takası ve Blaze avı -> Ender gözleriyle kaleyi bul -> Yatak patlat.");
		KNOWLEDGE_MAP.put("warden", "Warden Antik Şehirlerde (Deep Dark, Y=-52) ses ve titreşimle ortaya çıkar. Eğilerek (Sneak) yürü veya yün blokları kullan. Savaşmak yerine sessizce ganimeti al.");
		KNOWLEDGE_MAP.put("yemek", "Çiğ et az doyurur ve gıda zehirlenmesi yapabilir. Kamp ateşi, fırın veya tütsüleyicide (Smoker) pişir. En verimli yemekler: Pişmiş sığır eti, altın havuç.");
		KNOWLEDGE_MAP.put("açlık", "Açlık çubuğu 6 barın altına düşerse koşamazsın, 0 olursa canın azalır.");
		KNOWLEDGE_MAP.put("büyü", "Büyü masası (Enchanting Table) için 2 elmas, 4 obsidyen, 1 kitap gerekir. Maksimum 30. seviye büyü için etrafına 15 kitaplık dizilmelidir.");
		KNOWLEDGE_MAP.put("kalkan", "Kalkan 1 demir külçesi ve 6 tahta ile üretilir. Creeper patlamasını, okları ve fiziksel saldırıları %100 engeller.");
	}

	/**
	 * Checks the player's speech for keywords and returns exact static Minecraft facts.
	 */
	public static String getRelevantFacts(String speech) {
		String lower = speech.toLowerCase().trim();
		StringBuilder facts = new StringBuilder();

		for (Map.Entry<String, String> entry : KNOWLEDGE_MAP.entrySet()) {
			if (lower.contains(entry.getKey())) {
				facts.append("- ").append(entry.getValue()).append("\n");
			}
		}

		if (facts.length() > 0) {
			return "[KESİN MİNECRAFT 1.20+ GERÇEKLERİ (Bunu Kullan)]:\n" + facts.toString() + "\n";
		}
		return "";
	}
}
