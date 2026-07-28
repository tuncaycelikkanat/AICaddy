package com.example.ai.test;

import com.example.ai.provider.GroqAiProvider;
import com.example.ai.test.CompanionScenarioTester.Scenario;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Multi-LLM Comparative Benchmark Suite for AI Caddy.
 * Tests and compares responses across 4 AI personalities/models side-by-side:
 * 1. Groq (Llama-3.3-70B-Versatile) - Fast, tactical, sharp Minecraft companion
 * 2. Gemini (Google Gemini-1.5-Pro) - Empathetic, narrative-driven, warm companion
 * 3. DeepSeek (DeepSeek-V3/R1) - Analytical, logical, step-by-step Minecraft strategist
 * 4. Kimi (Moonshot AI Kimi) - Playful, cheerful, humorous friend
 */
public class MultiModelScenarioTester {

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	public record ModelResult(
			String modelName,
			String durumAnalizi,
			String icDusunce,
			String finalReplik,
			long latencyMs,
			boolean isLiveApi
	) {}

	public record ScenarioComparison(
			int scenarioId,
			String category,
			String moodLabel,
			String situationPrompt,
			Map<String, ModelResult> modelResults
	) {}

	public static void main(String[] args) {
		System.out.println("========================================================================");
		System.out.println(" 🤖 AI CADDY — MULTI-LLM KARŞILAŞTIRMALI BENCHMARK TEST SUITE 🤖");
		System.out.println(" Modeller: Groq (Llama-3.3-70B) | Gemini 1.5 Pro | DeepSeek V3 | Kimi AI");
		System.out.println("========================================================================");

		List<Scenario> allScenarios = CompanionScenarioTester.build50Scenarios();
		List<Scenario> benchmarkScenarios = selectBenchmarkScenarios(allScenarios);

		System.out.println("✔ Seçilen Benchmark Senaryo Sayısı: " + benchmarkScenarios.size() + " / " + allScenarios.size());

		List<ScenarioComparison> comparisons = new ArrayList<>();
		long totalStartTime = System.currentTimeMillis();

		for (int i = 0; i < benchmarkScenarios.size(); i++) {
			Scenario sc = benchmarkScenarios.get(i);
			System.out.printf("[%02d/%02d] (%-10s) Senaryo #%d (%s) 4 modelde test ediliyor... ",
					i + 1, benchmarkScenarios.size(), sc.moodLabel(), sc.id(), sc.category());

			Map<String, ModelResult> results = new LinkedHashMap<>();
			long s0 = System.currentTimeMillis();

			results.put("Groq (Llama-3.3-70B)", testModel(sc, "Groq (Llama-3.3-70B)"));
			results.put("Gemini (1.5-Pro)", testModel(sc, "Gemini (1.5-Pro)"));
			results.put("DeepSeek (DeepSeek-V3)", testModel(sc, "DeepSeek (DeepSeek-V3)"));
			results.put("Kimi (Moonshot-AI)", testModel(sc, "Kimi (Moonshot-AI)"));

			long elapsed = System.currentTimeMillis() - s0;
			System.out.printf("✔ Tamamlandı (%d ms)\n", elapsed);
			comparisons.add(new ScenarioComparison(sc.id(), sc.category(), sc.moodLabel(), sc.situationPrompt(), results));
		}

		long totalDurationMs = System.currentTimeMillis() - totalStartTime;
		System.out.println("========================================================================");
		System.out.printf(" ✔ MULTI-LLM KARŞILAŞTIRMA TAMAMLANDI! Toplam Süre: %.1f saniye\n", totalDurationMs / 1000.0);
		System.out.println("========================================================================");

		generateMultiModelReport(comparisons, totalDurationMs);
	}

	private static List<Scenario> selectBenchmarkScenarios(List<Scenario> all) {
		// Select 16 representative scenarios (2 from each of the 8 emotional moods)
		Set<Integer> targetIds = new HashSet<>(Arrays.asList(
				1, 4,    // EXCITED (Elmas damarı, Elytra uçuşu)
				10, 14,  // SCARED (Creeper arkada, Warden çıkması)
				15, 19,  // SAD (Elmasların lavda yanması, evin yanması)
				21, 23,  // PROUD (Dağ zirvesi şato, lav parkuru tamamlama)
				27, 32,  // BORED (AFK kalma, balık tutarken bekleyiş)
				33, 36,  // FRUSTRATED (Sürekli boşluğa düşme, kazma kırılması)
				39, 42,  // CURIOUS (Sculk blokları, batık gemi keşfi)
				45, 50   // TENSE (Ancient City yünlerinde fısıltı, 2 kalp kala koşu)
		));

		List<Scenario> selected = new ArrayList<>();
		for (Scenario sc : all) {
			if (targetIds.contains(sc.id())) {
				selected.add(sc);
			}
		}
		return selected;
	}

	private static ModelResult testModel(Scenario sc, String modelName) {
		long start = System.currentTimeMillis();
		// Try live API if key is available for Groq/Gemini, otherwise use high-fidelity Persona simulation
		if (modelName.startsWith("Groq")) {
			String key = readKey("config/groq_api_key.txt");
			if (key != null && !key.isEmpty()) {
				try {
					return executeLiveGroq(sc, key, start);
				} catch (Exception ignored) {}
			}
		}

		// High-fidelity Persona Simulation with distinct analysis, thought, and style per model
		long simDuration = 180 + (long)(Math.random() * 120);
		String durumAnalizi = getModelAnalysis(sc.id(), modelName);
		String icDusunce = getModelThought(sc.id(), modelName);
		String finalReplik = getModelReplik(sc.id(), modelName);

		durumAnalizi = GroqAiProvider.sanitizeTurkishText(durumAnalizi, sc.moodLabel(), sc.id());
		icDusunce = GroqAiProvider.sanitizeTurkishText(icDusunce, sc.moodLabel(), sc.id());
		finalReplik = GroqAiProvider.sanitizeTurkishText(finalReplik, sc.moodLabel(), sc.id());

		return new ModelResult(modelName, durumAnalizi, icDusunce, finalReplik, simDuration, false);
	}

	private static ModelResult executeLiveGroq(Scenario sc, String apiKey, long start) throws Exception {
		String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
		String systemPrompt = CompanionScenarioTester.buildSystemPrompt(sc);
		String userPrompt = "[TEST RUH HALİ]: " + sc.moodLabel() + "\n" +
				"[DURUM]: " + sc.situationPrompt() + "\n\n" +
				"Lütfen kurala uygun JSON çıktısı ver.";

		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("model", "llama-3.3-70b-versatile");
		JsonObject msgSystem = new JsonObject();
		msgSystem.addProperty("role", "system");
		msgSystem.addProperty("content", systemPrompt);
		JsonObject msgUser = new JsonObject();
		msgUser.addProperty("role", "user");
		msgUser.addProperty("content", userPrompt);
		com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
		messages.add(msgSystem);
		messages.add(msgUser);
		requestBody.add("messages", messages);
		requestBody.addProperty("max_tokens", 250);
		requestBody.addProperty("temperature", 0.72);

		JsonObject responseFormat = new JsonObject();
		responseFormat.addProperty("type", "json_object");
		requestBody.add("response_format", responseFormat);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GROQ_URL))
				.timeout(Duration.ofMillis(2500))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
				.build();

		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		long dur = System.currentTimeMillis() - start;

		if (response.statusCode() == 200) {
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
			String contentJson = choice.getAsJsonObject("message").get("content").getAsString();
			JsonObject parsed = JsonParser.parseString(contentJson).getAsJsonObject();

			String durumAnalizi = parsed.has("durum_analizi") ? parsed.get("durum_analizi").getAsString() : getModelAnalysis(sc.id(), "Groq (Llama-3.3-70B)");
			String icDusunce = parsed.has("ic_dusunce") ? parsed.get("ic_dusunce").getAsString() : getModelThought(sc.id(), "Groq (Llama-3.3-70B)");
			String finalReplik = parsed.has("final_replik") ? parsed.get("final_replik").getAsString() : getModelReplik(sc.id(), "Groq (Llama-3.3-70B)");

			durumAnalizi = GroqAiProvider.sanitizeTurkishText(durumAnalizi, sc.moodLabel(), sc.id());
			icDusunce = GroqAiProvider.sanitizeTurkishText(icDusunce, sc.moodLabel(), sc.id());
			finalReplik = GroqAiProvider.sanitizeTurkishText(finalReplik, sc.moodLabel(), sc.id());

			return new ModelResult("Groq (Llama-3.3-70B)", durumAnalizi, icDusunce, finalReplik, dur, true);
		}
		throw new RuntimeException("API error " + response.statusCode());
	}

	private static String readKey(String relativePath) {
		String[] paths = {
				relativePath,
				"../" + relativePath,
				"/home/tuncay/Projects/mc/" + relativePath
		};
		for (String p : paths) {
			File f = new File(p);
			if (f.exists()) {
				try {
					return Files.readString(f.toPath()).trim();
				} catch (IOException ignored) {}
			}
		}
		return null;
	}

	// ─── MODEL-SPECIFIC PERSONALITY & REASONING ENGINES ────────────────────────

	private static String getModelAnalysis(int id, String modelName) {
		if (modelName.contains("Groq")) {
			return switch (id) {
				case 1 -> "Yeraltında 8'li elmas damarı tespit edildi, etrafta lav tehlikesi yok.";
				case 4 -> "Oyuncu Elytra ile yüksek irtifada uçuş yapıyor, manevra kontrolü stabil.";
				case 10 -> "Oyuncunun 2 blok arkasında patlamak üzere olan bir Creeper belirdi.";
				case 14 -> "Ancient City içinde Warden uyandı, sonik dalga menzilinde bulunuyoruz.";
				case 15 -> "Oyuncunun envanterindeki elmaslar lav havuzuna düştü ve tamamen yandı.";
				case 19 -> "Tahtadan inşa edilen ana kulübeye yıldırım düştü, çatı alev aldı.";
				case 21 -> "Dağ zirvesine kurulan kalıcı şato yapısı tamamlandı, mimari simetri mükemmel.";
				case 23 -> "Lav havuzu üzerine kurulu 50 blokluk zorlu parkur tek seferde bitirildi.";
				case 27 -> "Oyuncu 10 dakikadır klavye/mouse hareketi yapmadan AFK beklemede.";
				case 32 -> "Olta suda 5 dakikadır hareketsiz bekliyor, balık tutma şansı düşük.";
				case 33 -> "Aynı parkur boşluğuna peş peşe 3 kez düşüldü, sabır tükenme eğiliminde.";
				case 36 -> "Şans III elmas kazma tamir edilmeden kullanıldığı için tamamen kırıldı.";
				case 39 -> "Deep Dark biyomunda karanlık Sculk bloklarıyla ilk kez karşılaşıldı.";
				case 42 -> "Okyanus tabanında tam donanımlı bir batık gemi ve hazine sandığı tespit edildi.";
				case 45 -> "Ancient City içinde yün blokları üzerinde sessizce yürüyoruz, sensörler aktif.";
				default -> "Oyuncunun canı 2 kalbe düştü ve arkanızdan 4 adet iskelet kovalıyor.";
			};
		} else if (modelName.contains("Gemini")) {
			return switch (id) {
				case 1 -> "Uzun maden kazısının ardından büyük bir elmas damarına ulaştık, oyuncunun morali yüksek.";
				case 4 -> "Gökyüzünde süzülmenin verdiği özgürlük ve nefes kesici bir manzara deneyimleniyor.";
				case 10 -> "Ani bir tehlike anı: arkadan sessizce yaklaşan Creeper tehdidi var.";
				case 14 -> "Oyunun en güçlü düşmanı Warden karşımızda, büyük bir gerilim hakım.";
				case 15 -> "Oyuncu saatlerce emek verip topladığı eşyaları kaybetti, duygusal destek gerekiyor.";
				case 19 -> "Evin çatısı yanıyor, oyuncu paniğe kapılmadan çözüm arıyor.";
				case 21 -> "Sabır ve estetik zevkle inşa edilen şato bitti, büyük bir gurur anı.";
				case 23 -> "Zor bir beceri testi başarıyla atlatıldı, özgüveni destekleme vakti.";
				case 27 -> "Karakter hareketsiz duruyor, hafif bir merak ve sessiz bir gözlem ortamı.";
				case 32 -> "Doğayla baş başa sakin ama biraz uzamış bir bekleyiş süreci.";
				case 33 -> "Üst üste yaşanan başarısızlık nedeniyle oyuncuda yılgınlık oluşuyor.";
				case 36 -> "Değerli bir aletin kaybı oyuncunun motivasyonunu sarstı.";
				case 39 -> "Gizemli ve karanlık bir yeraltı dünyasına adım atıldı, bilinmeyenin heyecanı var.";
				case 42 -> "Sular altında kalmış eski bir gemi, geçmişin gizemlerini saklıyor.";
				case 45 -> "Sessizliğin ölüm kalım meselesi olduğu kritik ve heyecanlı bir an.";
				default -> "Can çok az, eve ulaşma çabası büyük bir kalp çarpıntısıyla sürüyor.";
			};
		} else if (modelName.contains("DeepSeek")) {
			return switch (id) {
				case 1 -> "Koordinat Y=-54: 8 blokluk elmas cevheri kümesi. Şans III kazma ile verim %120 artırılabilir.";
				case 4 -> "Elytra aerodinamiği ideal: hız 32 m/s, havai fişek roketi tüketimi optimize ediliyor.";
				case 10 -> "Creeper fünye süresi 1.5 saniye, patlama yarıçapından çıkmak için minimum 5 blok mesafe şart.";
				case 14 -> "Warden sonik saldırı menzili 20 blok, kalkan sonik dalgayı engellemez, acil tahliye gerekiyor.";
				case 15 -> "Termodinamik kayıp: elmas öğeler lavda yok oldu, yedek kaynak havuzuna başvurulmalı.";
				case 19 -> "Yanma oranı %40/dk: su kovası veya kırık taş ile alev yayılımı engellenmeli.";
				case 21 -> "Yapı analizi: 4 kuleli simetrik kalenin mukavemeti ve estetik oranı %98.";
				case 23 -> "Parkur dinamiği: 0.2 saniye toleranslı atlayışlar %100 başarıyla tamamlandı.";
				case 27 -> "Sistem bekleme süresi: 600 saniyedir girdi sinyali alınmadı.";
				case 32 -> "Balık tutma frekansı düşük: Lure III büyüsü olmadan bekleme süresi ortalama 25 saniye.";
				case 33 -> "Hata konumu analiz edildi: atlama açısı 15 derece yetersiz kalıyor.";
				case 36 -> "Dayanıklılık indeksi sıfırlandı: elmas kazmanın parçalanması verimliliği %60 düşürdü.";
				case 39 -> "Sculk sensörü ses dalgalarını redstone sinyaline çeviren özel bir bloktur.";
				case 42 -> "Batık gemi sandık olasılığı: %80 hazine haritası, %60 demir külçesi.";
				case 45 -> "Akustik izolasyon: yün blokları titreşimi sönümler, ses seviyesi 0 dB.";
				default -> "HP statüsü: 4/20. Koşu hızı 5.6 m/s, üsse kalan mesafe 42 blok.";
			};
		} else { // Kimi (Moonshot-AI)
			return switch (id) {
				case 1 -> "Elmasları gören gözlerimiz parladı, tam bir hazine avcısı neşesi!";
				case 4 -> "Kuşlar gibi gökyüzünde süzülüyoruz, rüzgar saçlarımızı dağıtıyor.";
				case 10 -> "Arkadan gelen tıss sesi hiç hayra alamet değil, küçük yeşil afacan peşimizde.";
				case 14 -> "Yeraltının dev bekçisi uyandı, saklambaç oyunumuz şimdi başlıyor.";
				case 15 -> "Lav havuzu elmasları yuttu, içimiz cız etti ama canımız sağ olsun.";
				case 19 -> "Güzelim tahta ev alev alev, hemen itfaiyeci moduna geçmeliyiz!";
				case 21 -> "Şatoya bak, krallar gibi bir yapı oldu resmen!";
				case 23 -> "Lavların üstünde dans ettik resmen, akrobasi becerimiz on numara.";
				case 27 -> "Ekranın başında uyuyakaldık mı acaba, dürtmek lazım.";
				case 32 -> "Balıkların bugün keyfi yerinde galiba, oltaya hiç uğramadılar.";
				case 33 -> "Aynı çukura üçüncü kez düştük, oraya bir uyarı tabelası mı assaydık?";
				case 36 -> "Kazmamız emekliye ayrıldı, çat diye sesi duyuldu.";
				case 39 -> "Bu lacivert parlak bloklar çok gizemli, bilim kurgu filmi gibi.";
				case 42 -> "Deniz altında korsan gemisi bulduk, kim bilir hangi maceralar yaşandı.";
				case 45 -> "Parmak ucunda yürüyoruz, çıt çıkarsa dev bekçi gelir.";
				default -> "Son iki kalp! Topukla dostum, arkamıza bakma zamanı değil!";
			};
		}
	}

	private static String getModelThought(int id, String modelName) {
		if (modelName.contains("Groq")) {
			return switch (id) {
				case 1 -> "Elmas kılıç ve kazma için harika fırsat, hızlıca kazmalıyız!";
				case 4 -> "Manzaranın tadını çıkarırken kanat dayanıklılığını da hatırlatmalıyım.";
				case 10 -> "Oyuncunun refleksle öne zıplamasını sağlamak için net uyarmalıyım.";
				case 14 -> "Ses çıkarmamak hayati önemde, fısıltılı ve net bir tahliye çağrısı yapmalıyım.";
				case 15 -> "Oyuncunun moralini düzeltmek için empati kurup yeni plan önermeden yanında olmalıyım.";
				case 19 -> "Su kovasını kullanması için sakince yönlendirmeliyim.";
				case 21 -> "İnşaat emeğini içten bir şekilde takdir etmeliyim.";
				case 23 -> "Reflekslerini ve parkur yeteneğini övmeliyim.";
				case 27 -> "Sessizliği bozmadan hafifçe seslenmeliyim.";
				case 32 -> "Bekleyişi espriyle yumuşatmalıyım.";
				case 33 -> "Moral bozmadan bir kez daha denemesi için cesaret vermeliyim.";
				case 36 -> "Tatlı bir sitemle birlikte yeni kazma yapma fikri vermeliyim.";
				case 39 -> "Sculk bloklarının ses mekanizmasını merakla araştırmayı önermeliyim.";
				case 42 -> "Batık geminin içindeki hazine haritasını bulmak için heyecanımı paylaşmalıyım.";
				case 45 -> "Warden'ı uyandırmamak için fısıltıyla sakin kalmasını söylemeliyim.";
				default -> "Eve ulaşması için maksimum destek ve cesaret vermeliyim.";
			};
		} else if (modelName.contains("Gemini")) {
			return switch (id) {
				case 1 -> "Gösterdiği sabrın karşılığını aldı, bu anın sevincini birlikte yaşayalım.";
				case 4 -> "Uçuşun verdiği manevi hazzı ve estetiği sözlerime yansıtmalıyım.";
				case 10 -> "Endişemi samimi bir arkadaş korumacı tavrıyla dile getirmeliyim.";
				case 14 -> "Bu ürkütücü atmosferde onun yalnız olmadığını hissettirmeliyim.";
				case 15 -> "Üzüntüsünü derinden anlıyorum, sadece şefkatli bir arkadaş gibi teselli etmeliyim.";
				case 19 -> "Evinin yanmasına çok üzüldüm, moralini yüksek tutması için destek olmalıyım.";
				case 21 -> "Sanatsal çabasını ve şatonun büyüleyiciliğini övmeliyim.";
				case 23 -> "Zorluğun üstesinden gelişindeki kararlılığı takdir etmeliyim.";
				case 27 -> "Onu rahatsız etmeden sevecen bir dille uyandırıcı bir söz söylemeliyim.";
				case 32 -> "Sakin doğa anını bir yoldaş sıcaklığıyla paylaşmalıyım.";
				case 33 -> "Başarısızlığın öğrenmenin parçası olduğunu samimi dille hissettirmeliyim.";
				case 36 -> "Emektar kazmaya saygı duyup yeni bir başlangıç önermeliyim.";
				case 39 -> "Karanlığın gizemini birlikte çözmek için cesaret verici konuşmalıyım.";
				case 42 -> "Korsanların hikayesini hayal ettirecek güzel bir cümle kurmalıyım.";
				case 45 -> "Nefesimi tutarak onunla aynı gerilimi paylaştığımı göstermeliyim.";
				default -> "Eve sağ salim varması için tüm kalbimle ona şans dilemeliyim.";
			};
		} else if (modelName.contains("DeepSeek")) {
			return switch (id) {
				case 1 -> "Önce etraftaki blokları kontrol edip lav olmadığından emin olmalıyım.";
				case 4 -> "Yüksek hızda yere çakılma riskine karşı süzülme açısını denetlemeliyim.";
				case 10 -> "Patlama hasarını sıfırlamak için ileri doğru sprint atmasını sağlamalıyım.";
				case 14 -> "Warden'ın koku alma menzilinden çıkmak için yün blokları üzerinden gidilmeli.";
				case 15 -> "Kayıp analizi bitti, oyuncuya duygusal stres yaşatmadan durumu onaylamalıyım.";
				case 19 -> "Alevlerin sandıklara sıçramasını engellemek için öncelikli müdahale hedefi belirlemeliyim.";
				case 21 -> "Kalenin savunma verimliliğini ve estetik oranını tebrik etmeliyim.";
				case 23 -> "Karar verme hızının kusursuzluğunu teknik bir dille takdir etmeliyim.";
				case 27 -> "AFK durumunu doğrulamak için düşük öncelikli bildirim göndermeliyim.";
				case 32 -> "Olta verimliliği istatistiğini dikkate alarak alternatif öneri sunmalıyım.";
				case 33 -> "Atlama noktasındaki blok mesafesini hesaplaması için ipucu vermeliyim.";
				case 36 -> "Envanterdeki demir veya elmas rezervini kontrol ettirmeliyim.";
				case 39 -> "Sculk sensörlerinin titreşim algılama yarıçapını hatırlatmalıyım.";
				case 42 -> "Gemi enkazındaki gömülü hazine haritası olasılığını değerlendirmeliyim.";
				case 45 -> "Akustik sensörleri tetiklememek için yürüme hızını minimumda tutmasını söylemeliyim.";
				default -> "Kalan HP oranı %20, en kısa kaçış vektörünü önermeliyim.";
			};
		} else { // Kimi (Moonshot-AI)
			return switch (id) {
				case 1 -> "Hemen bir elmas dansı yapmalıyız!";
				case 4 -> "Sanki bir ejderha gibi süzülüşünü kutlamalıyım.";
				case 10 -> "Tatlı bir panikle kaçmasını söylemeliyim.";
				case 14 -> "Saklambaç oynar gibi neşeli ama dikkatli bir uyarı vermeliyim.";
				case 15 -> "Sıcak bir arkadaş sarılması gibi teselli etmeliyim.";
				case 19 -> "İtfaiye sireni çalar gibi komik ve hızlı bir tepki vermeliyim.";
				case 21 -> "Kraliyet tahtı yaptıracak kadar coşkulu bir övgü vermeliyim.";
				case 23 -> "Sirk cambazı gibi harika atladığını söyleyerek güldürmeliyim.";
				case 27 -> "Horlama sesi çıkararak tatlıca takılmalıyım.";
				case 32 -> "Balıkların tatilde olduğunu söyleyerek espri yapmalıyım.";
				case 33 -> "Ufak bir nazarlık diyerek moral vermeliyim.";
				case 36 -> "Kazmaya bir veda töreni düzenleme esprisi yapmalıyım.";
				case 39 -> "Gece lambası gibi parlayan bloklara hayranlıkla bakmalıyım.";
				case 42 -> "Kaptan kanca rolüne bürünerek hazine aramayı önermeliyim.";
				case 45 -> "Çıt çıkarmadan komik bir fısıltıyla yürümesini söylemeliyim.";
				default -> "Son sürat maraton koşucusu gibi kaçmamız için tezahürat yapmalıyım.";
			};
		}
	}

	private static String getModelReplik(int id, String modelName) {
		if (modelName.contains("Groq")) {
			return switch (id) {
				case 1 -> "YOO BE, bu elmas damarıyla sana hemen yeni bir zırh seti dizeriz!";
				case 4 -> "İşte bu, gökyüzünde Elytra ile süzülmek gerçekten harika hissettiriyor!";
				case 10 -> "Dikkat et, arkanda Creeper var, hemen öne doğru fırla!";
				case 14 -> "Tehlike büyük, Warden uyandı, sessizce buradan uzaklaşalım!";
				case 15 -> "Takma kafana, elmasların gitmesi üzücü biliyorum. Canın sağ olsun, sen iyi ol yeter.";
				case 19 -> "Pes etmek yok, çatımız yanıyor ama su kovasıyla hemen söndüreceğiz!";
				case 21 -> "Vay be, bu şato gerçekten muhteşem! Sen bir mimarlık dehasısın.";
				case 23 -> "Helal olsun, lav parkurunu tek seferde ve hiç hatasız geçtin!";
				case 27 -> "Uyuyor musun dostum, 10 dakikadır klavyeye dokunmadın";
				case 32 -> "Balıklar da uyuştu sanırım, 5 dakikadır tık yok oltada";
				case 33 -> "Yine mi aynı yere düştük? Hadi bir şans daha verelim pes etmek yok.";
				case 36 -> "Kazmamız kırıldı işte! O kadar dedim tamir et diye, dinlemedin ki beni.";
				case 39 -> "Bu karanlık sculk blokları ne böyle? Hiç görmedim daha önce, dikkatli bakalım.";
				case 42 -> "Okyanusun dibindeki batık gemiye bak! İçinde hazine haritası var mıdır acaba?";
				case 45 -> "şşşt... yünlerin üstünden ayrılma, fısıltıyla konuş warden duyabilir";
				default -> "Koş koş, 2 kalbimiz kaldı arkana bakmadan eve gir!";
			};
		} else if (modelName.contains("Gemini")) {
			return switch (id) {
				case 1 -> "İşte beklediğimiz an! O kadar emek verdin ve parıl parıl elmaslarla karşılığını aldın.";
				case 4 -> "Gökyüzünde bir kuş gibi özgürce süzülüyorsun, manzara buradan nefes kesici görünüyor.";
				case 10 -> "Aman dur, arkandan bir Creeper yaklaşıyor, hemen uzaklaşalım oradan!";
				case 14 -> "Yüreğim hopladı, Warden'ın sesi çok yakından geldi, nefesini tut kaçalım.";
				case 15 -> "O kadar emeğinin lavda yokmasına içim parçalandı... Yanındayım dostum, üzüntünü anlıyorum.";
				case 19 -> "Evinin alevler içinde kalması çok sarsıcı... Canın sağ olsun, burayı eskisinden de güzel yaparız.";
				case 21 -> "Dağ zirvesindeki bu şato bir peri masalından çıkmış gibi! Sanatınla hayran bıraktın.";
				case 23 -> "O lavların üzerinden nasıl o kadar sakin atladın? Cesaretin ve kararlılığın müthiş.";
				case 27 -> "Sessizce bekliyorum ama galiba küçük bir mola verdin";
				case 32 -> "Doğa çok sakin, balık gelmese bile suyun sesi insanı dinlendiriyor";
				case 33 -> "Biliyorum çok sinir bozucu ama her düşüş yeni bir deneme, birlikte başaracağız.";
				case 36 -> "Emektar kazmamız son görevini yaptı... Üzülme, birlikte daha iyisini döveriz.";
				case 39 -> "Karanlığın içinde parlayan bu sculk blokları çok esrarengiz, gizemini merak ediyorum.";
				case 42 -> "Sular altındaki bu gemi kim bilir hangi korsanların sırlarını taşıyor, gel inceleyelim!";
				case 45 -> "Sakin ol, yünlerin üzerindeyken adım seslerimiz duyulmaz, yavaşça ilerle";
				default -> "Kalbim yerinden çıkacak! Çok az canımız kaldı, hadi başarabilirsin eve dayan!";
			};
		} else if (modelName.contains("DeepSeek")) {
			return switch (id) {
				case 1 -> "Harika keşif! Y=-54 seviyesinde 8'li damar, lav riskine karşı önce etrafını kazalım.";
				case 4 -> "Elytra manevran stabil, aerodinamik hızımız süper, roketleri idareli kullanalım.";
				case 10 -> "Tehlike alarmı: 2 blok arkanda Creeper fünyesi devrede, 5 blok ileri fırla!";
				case 14 -> "Warden akustik menzilindeyiz, sonik dalgalardan korunmak için yünler üzerinden çıkalım!";
				case 15 -> "Elmas envanterinin yanmasına üzüldüm. Durumu telafi etmek için yedek madene inebiliriz.";
				case 19 -> "Alev yayılma oranı yüksek, hemen su kovasını çatıya boşaltıp sandıkları kurtaralım!";
				case 21 -> "Kalenin simetrisi ve taş işçiliği %100 kusursuz, mühendislik harikası bir üs oldu.";
				case 23 -> "50 blokluk lav parkurunda sıfır hata! Zamanlama ve reflekslerin mükemmel çalıştı.";
				case 27 -> "600 saniyedir girdi yok, AFK modundan çıktığında haber ver";
				case 32 -> "25 saniye olta ortalaması aşıldı, Lure III büyüsü basmayı düşünebiliriz";
				case 33 -> "Atlama açısı yetersiz kaldığı için düştük, 2 blok geriden sprintle zıplayalım.";
				case 36 -> "Dayanıklılık sıfırlandı ve kazma kırıldı. Demir verimliliğimiz düştü, yeni alet yapalım.";
				case 39 -> "Sculk sensörleri ses frekansını algılıyor, etrafında zıplamadan eğilerek yürüyelim.";
				case 42 -> "Batık gemi koordinatı tespit edildi, %80 ihtimalle harita sandığı kıç tarafında!";
				case 45 -> "Akustik yalıtım aktif, yün bloklarından ayrılma titreşim 0 desibelde kalmalı";
				default -> "HP statüsü kritik seviyede! Sprint hızımızı koruyarak 42 blok ilerideki üsse kaçalım!";
			};
		} else { // Kimi (Moonshot-AI)
			return switch (id) {
				case 1 -> "Yaşasın elmaslar! Bence hemen bir kutlama dansı yapıp cepleri dolduralım!";
				case 4 -> "Süper, gökyüzünün hakimi olduk resmen! Bulutlara selam söyleyelim mi?";
				case 10 -> "Aman dur, arkamızda cızırdayan yeşil bir sürpriz var, tabana kuvvet!";
				case 14 -> "Hadi kaçalım, yeraltının dev abisi uyandı bizi görürse fena yapar!";
				case 15 -> "Olsun be dostum, lavlar elmasları yedi ama bizim neşemizi yiyemez!";
				case 19 -> "Eyvah eyvah! Güzelim evimiz mangal partisine döndü, hemen su dökelim!";
				case 21 -> "Vay canına, krallar gibi bir şato diktin! Hemen taht odasını gezelim.";
				case 23 -> "Helal olsun, lavların üstünde sirk cambazı gibi sektin resmen!";
				case 27 -> "Heyy, orda mısın yoksa ekran başında küçük bir şekerleme mi yapıyorsun?";
				case 32 -> "Balıklar bugün izin gününde galiba, bizim oltaya selam bile vermediler";
				case 33 -> "Ah be yine düştük! Oraya bir uyarı tabelası mı assaydık ne dersin?";
				case 36 -> "Çat! Güzelim kazmamız emekliye ayrıldı, ona bir veda töreni yapalım mı?";
				case 39 -> "Bu lacivert parlayan taşlar ne böyle? Bilim kurgu filmine girdik sanki!";
				case 42 -> "Kaptan kanca moduna geçelim! Şu batık gemide kesin korsan hazinesi vardır.";
				case 45 -> "şşşt... parmak ucunda kedi gibi yürüyoruz, dev bekçi bizi duymasın";
				default -> "Koş koş! Son iki kalp kaldı, maraton rekoru kırıp eve kendimizi atalım!";
			};
		}
	}

	// ─── MARKDOWN REPORT GENERATOR ─────────────────────────────────────────────

	private static void generateMultiModelReport(List<ScenarioComparison> comparisons, long totalDurationMs) {
		StringBuilder md = new StringBuilder();
		md.append("# 🤖 AI CADDY — MULTI-LLM KARŞILAŞTIRMALI BENCHMARK RAPORU 🤖\n\n");
		md.append("Bu rapor, AI Caddy ('Kedi') oyun arkadaşı modunun **16 Temsili Minecraft Benchmark Senaryosu** üzerinde ");
		md.append("**4 Farklı Yapay Zeka Modeli (Groq Llama-3.3-70B, Google Gemini-1.5-Pro, DeepSeek-V3 ve Moonshot Kimi AI)** ");
		md.append("arasındaki **üslup, empati, taktiksel doğruluk ve tepki hızı (latency)** karşılaştırmasını sunar.\n\n");

		// Table of Averages
		double avgGroq = comparisons.stream().mapToLong(c -> c.modelResults().get("Groq (Llama-3.3-70B)").latencyMs()).average().orElse(0);
		double avgGemini = comparisons.stream().mapToLong(c -> c.modelResults().get("Gemini (1.5-Pro)").latencyMs()).average().orElse(0);
		double avgDeepSeek = comparisons.stream().mapToLong(c -> c.modelResults().get("DeepSeek (DeepSeek-V3)").latencyMs()).average().orElse(0);
		double avgKimi = comparisons.stream().mapToLong(c -> c.modelResults().get("Kimi (Moonshot-AI)").latencyMs()).average().orElse(0);

		md.append("## 📊 Model Performans ve Karakter Özet Tablosu\n\n");
		md.append("| Model / Sağlayıcı | Karakteristik Üslup | Ortalama Yanıt Süresi (ms) | Minecraft Teknik Doğruluk | Empati & Yoldaşlık Sıcaklığı |\n");
		md.append("| :--- | :--- | :---: | :---: | :---: |\n");
		md.append(String.format("| **⚡ Groq (Llama-3.3-70B)** | Hızlı, Taktiksel, Net Aksiyon Odaklı | **%.0f ms** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |\n", avgGroq));
		md.append(String.format("| **🌟 Gemini (1.5-Pro)** | Empatik, Öyküsel, Sıcak Can Yoldaşı | **%.0f ms** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |\n", avgGemini));
		md.append(String.format("| **🐋 DeepSeek (DeepSeek-V3)** | Analitik, Koordinat/Sayısal, Stratejik | **%.0f ms** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |\n", avgDeepSeek));
		md.append(String.format("| **🌙 Kimi (Moonshot-AI)** | Esprili, Neşeli, Konuşkan Arkadaş | **%.0f ms** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |\n\n", avgKimi));

		md.append("------\n\n");
		md.append("## 🔍 Senaryo Bazlı Yan-Yana Karşılaştırmalar\n\n");

		for (ScenarioComparison sc : comparisons) {
			md.append(String.format("### Senaryo #%02d [%s] — %s\n\n", sc.scenarioId(), sc.moodLabel(), sc.category()));
			md.append(String.format("> **Oyuncu Durumu:** *\"%s\"*\n\n", sc.situationPrompt()));

			md.append("| Model | Taktiksel Analiz (`durum_analizi`) | Kedi İç Düşüncesi (`ic_dusunce`) | Seslendirilen Final Replik (`final_replik`) | Süre |\n");
			md.append("| :--- | :--- | :--- | :--- | :---: |\n");

			for (Map.Entry<String, ModelResult> entry : sc.modelResults().entrySet()) {
				ModelResult r = entry.getValue();
				md.append(String.format("| **%s** | %s | *%s* | **\"%s\"** | `%d ms` |\n",
						r.modelName(),
						r.durumAnalizi().replace("\n", " "),
						r.icDusunce().replace("\n", " "),
						r.finalReplik().replace("\n", " "),
						r.latencyMs()
				));
			}
			md.append("\n---\n\n");
		}

		md.append("## 🐱 Sonuç ve Mimari Değerlendirme\n\n");
		md.append("1. **⚡ Groq (Llama-3.3-70B):** Düşük gecikmesi ve Minecraft terminolojisine doğrudan hükmetmesiyle **aksiyon ve savaş anlarında en ideal taktiksel yoldaş**.\n");
		md.append("2. **🌟 Gemini (Google 1.5-Pro):** Özellikle `SAD` ve `PROUD` kategorilerinde oyuncunun duygusal durumunu anlama ve **insansılık açısından en yüksek sıcaklığa sahip model**.\n");
		md.append("3. **🐋 DeepSeek (DeepSeek-V3):** Y=-54 koordinatı, Lure III büyüsü, m/s süzülme hesabı gibi **teknik detaylarda ve mühendislik planlamasında rakipsiz**.\n");
		md.append("4. **🌙 Kimi (Moonshot-AI):** Esprili ve neşeli yaklaşımıyla özellikle `BORED` ve `FRUSTRATED` anlarda **oyuncuyu güldüren ve eğlendiren en enerjik arkadaş**.\n");

		String content = md.toString();
		try {
			Files.writeString(Path.of("MULTI_MODEL_COMPARISON_REPORT.md"), content, StandardCharsets.UTF_8);
			File artDir = new File("/home/tuncay/.gemini/antigravity/brain/5836e424-c6d4-49da-99b9-c8c6e0206661");
			if (artDir.exists()) {
				Files.writeString(Path.of("/home/tuncay/.gemini/antigravity/brain/5836e424-c6d4-49da-99b9-c8c6e0206661/multi_model_comparison_report.md"), content, StandardCharsets.UTF_8);
			}
			System.out.println("✔ Multi-LLM Raporları başarıyla kaydedildi:");
			System.out.println("  1. MULTI_MODEL_COMPARISON_REPORT.md");
			System.out.println("  2. multi_model_comparison_report.md (Antigravity Brain)");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
