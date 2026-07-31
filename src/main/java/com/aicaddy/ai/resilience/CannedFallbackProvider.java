package com.aicaddy.ai.resilience;

import com.aicaddy.ai.mood.CompanionMoodEngine;
import com.aicaddy.ai.mood.CompanionMoodState;

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
	private static final Map<String, Integer> LAST_USED_INDEX = new ConcurrentHashMap<>();
	private static final Random RANDOM = new Random();

	static {
		CANNED_HAVUZU.put("SCARED", Arrays.asList(
				"Burası biraz fazla sessiz ve karanlık... Sırtımı sana yaslıyorum dostum, dikkatli olalım.",
				"Tüylerim diken diken oldu, etraftan tuhaf sesler geliyor. Hemen bir meşale koyalım mı?",
				"Dur dur dur, ne o ne o... Gittiğimiz iyi olur ya, burada bir gariplik var!",
				"Karanlıkta gözlerim parlıyor ama içim ürperdi, arkandan geliyorum sakın benden ayrılma.",
				"Şu sese bak ya! Bir şeyler yaklaşıyor sanki, kılıcını elinde tut dostum.",
				"Ben burada bekleyeyim en iyisi, çok tehlikeli oldu burası!"
		));

		CANNED_HAVUZU.put("EXCITED", Arrays.asList(
				"Yeraltında böyle zengin bir cevher damarı bulmuşken hemen kazıp toplayalım!",
				"Harika gidiyoruz, zırhların ve aletlerin dayanıklılığı da yerindeyse hız kesmeden devam edelim.",
				"Bu bulduğumuz kaynaklar sayesinde gelişim hızımız ikiye katlanacak, harika!",
				"Nether portalı için obsidyenlerimiz hazırsa yeni katmana geçmeye hazırım.",
				"Bu bölgedeki maden damarları gerçekten bereketli duruyor, kazmalar çalışsın!",
				"Envanteri güzelce düzenledikten sonra yeni hedeflerimize doğru koşalım."
		));

		CANNED_HAVUZU.put("SAD", Arrays.asList(
				"Canın sağ olsun dostum, öldüğümüzde eşyalar gitse bile tecrübemiz kalıyor.",
				"Kritik bir kayıp oldu ama envanteri sandıktaki yedek aletlerle hızlıca toparlayabiliriz.",
				"Zor bir durum, bundan sonra karanlık alanlara girerken mutlaka meşale ve kalkanla girelim.",
				"Eşyalar yeniden yapılır, önemli olan tehlikeli bölgeyi güvenceye alıp geri dönmek.",
				"Moral bozmak yok, üsse dönüp eksiklerimizi tamamlayarak tekrar deneriz.",
				"Zırhlarımız yetersiz kalmış olabilir, bir dahaki sefere enchant basıp öyle gidelim."
		));

		CANNED_HAVUZU.put("PROUD", Arrays.asList(
				"Tebrikler, gerçekten çok temiz ve taktiksel bir mücadeleydi.",
				"Kritik vuruş zamanlamaların harikaydı, hiç hasar almadan atlattık.",
				"Bu ilerleme hızıyla kısa sürede oyunun en iyi ekipmanlarını tamamlayacağız.",
				"Çok akıllıca bir hamle, risk almadan en yüksek verimi aldık.",
				"Mekanikleri çok iyi kullanıyorsun, bu taktikle her mobun üstesinden geliriz.",
				"Kaynak yönetimi ve savunmamız çok iyi durumda, böyle devam."
		));

		CANNED_HAVUZU.put("BORED", Arrays.asList(
				"Üs çevresinde çok bekledik, biraz madene inip kaynak depolayalım mı?",
				"Boş durmak yerine örste alet onarımı veya tarım alanlarını hasat etmeyi yapabiliriz.",
				"Yapacak bir şey arıyorsan yakınlardaki köylülerle takas yapmayı deneyebiliriz.",
				"Envanterdeki gereksiz eşyaları sandıklara ayırıp yeni bir keşfe çıkma vakti geldi.",
				"Şu an sakiniz, istersen iksir standı veya enchant masası için hazırlık yapalım.",
				"Aletlerin dayanıklılığını kontrol ettikten sonra yeni bir biyoma açılalım."
		));

		CANNED_HAVUZU.put("FRUSTRATED", Arrays.asList(
				"Creeper patlamalarına dikkat edelim, blokları ve eşyaları boşuna kaybetmeyelim.",
				"Canın azken agresif oynamak tehlikeli, önce yemek yiyip can yenilemelisin.",
				"Burada gereksiz hasar aldık, taktik değiştirip kalkanımızı daha aktif kullanalım.",
				"Karanlık köşeleri aydınlatmadığımız için sürekli mob doğuyor, önce ışıklandıralım.",
				"Maden kazarken altımızdaki bloğu doğrudan kazmayalım, lava düşme riskimiz yüksek.",
				"Kılıcın bekleme süresi dolmadan art arda vurmak hasarı düşürüyor, zamanlamaya dikkat edelim."
		));

		CANNED_HAVUZU.put("CURIOUS", Arrays.asList(
				"Y-16 seviyesindeysek demir için, -58'e inersek elmas için en verimli katmandayız.",
				"Sol köşe karanlık kalmış, ışık seviyesi sıfırsa mob doğabilir, meşale atalım.",
				"Bu biyomun derinliklerinde Antik Şehir olma ihtimali var, Warden seslerine dikkat edelim.",
				"Bu bölgede yapı olma olasılığı yüksek, koordinatları not edip incelemekte fayda var.",
				"Kazdığımız bloğun sesinden arkada bir mağara boşluğu olduğunu anlayabiliriz.",
				"Köylülerin meslek bloklarını düzenlersek çok daha avantajlı takaslar yakalayabiliriz."
		));

		CANNED_HAVUZU.put("TENSE", Arrays.asList(
				"Kalkanını hazır tut, ileriden iskelet ve zombi sesleri geliyor.",
				"Etrafta ışık seviyesi çok düşük, her an bir Creeper veya patlayıcı tehlike çıkabilir.",
				"Canın kritik seviyede, güvenli bir köşeye çekilip can barımızı dolduralım.",
				"Derin karanlık bölgesindeyiz, titreşim sensörlerini tetiklememek için eğilerek yürüyelim.",
				"Nether'da lav kıyısında savaşırken savrulma direnci ve ateşe dayanıklılık çok önemli.",
				"Etrafımız sarılıyor, köşeye sıkışmadan açık alana doğru stratejik geri çekilelim."
		));

		CANNED_HAVUZU.put("DEFAULT", Arrays.asList(
				"Zırhının ve kılıcının dayanıklılığına göz kulak ol, kritik anda yolda bırakmasın.",
				"Madene ineceksek yanımıza yeterince meşale, tahta ve yemek aldığımızdan emin olalım.",
				"Ben hazırım dostum, hangi katmana veya biyoma gidersek gidelim arkandayım.",
				"Çevre güvenliğini sağladığımız sürece her türlü kaynağı rahatça toplayabiliriz.",
				"İlerlememiz gayet dengeli, kaynakları üsse taşıyıp güvenceye alalım.",
				"Taktiksel ilerleyip riskleri en aza indirdiğimiz sürece hiçbir mob sorun çıkaramaz."
		));
	}

	/**
	 * Returns an immersive, 100% Turkish canned response for the current companion mood
	 * with anti-repetition guarantee so the same fallback is never spoken twice in a row.
	 */
	public static String getCannedFallback() {
		CompanionMoodState currentMood = CompanionMoodEngine.getCurrentMood();
		String moodKey = (currentMood != null) ? currentMood.name() : "DEFAULT";
		List<String> list = CANNED_HAVUZU.getOrDefault(moodKey, CANNED_HAVUZU.get("DEFAULT"));
		return pickWithAntiRepetition(moodKey, list);
	}

	/**
	 * Returns a canned fallback response for a specific mood label with anti-repetition guarantee.
	 */
	public static String getCannedFallbackForMood(String moodLabel) {
		String key = (moodLabel != null && !moodLabel.isEmpty()) ? moodLabel.toUpperCase() : "DEFAULT";
		List<String> list = CANNED_HAVUZU.getOrDefault(key, CANNED_HAVUZU.get("DEFAULT"));
		return pickWithAntiRepetition(key, list);
	}

	private static String pickWithAntiRepetition(String key, List<String> list) {
		if (list == null || list.isEmpty()) {
			return "Miyav! Seninle yan yana koşmak gerçekten çok keyifli dostum.";
		}
		if (list.size() == 1) {
			return list.get(0);
		}
		int lastIdx = LAST_USED_INDEX.getOrDefault(key, -1);
		int newIdx;
		do {
			newIdx = RANDOM.nextInt(list.size());
		} while (newIdx == lastIdx);

		LAST_USED_INDEX.put(key, newIdx);
		return list.get(newIdx);
	}
}
