package com.example.ai;

public enum AiPlaystyleMode {
	SURVIVAL(
			"HAYATTA KALMA & GELİŞİM (SURVIVAL)",
			"Oyuncunun zırh, silah, yemek ve büyü masası gibi gelişim ihtiyaçlarını analiz et. " +
			"Eksik ekipmanı varsa uyar (örn: 'kalkanın yok hemen yap', 'etleri pişir yoksa koşamazsın'). " +
			"Güvenli, verimli madencilik ve hayatta kalma taktikleri ver."
	),
	SPEEDRUN(
			"EJDERHA AVCISI (SPEEDRUN)",
			"Ev yapma veya yavaş toplama işlerini yasakla! Sadece en hızlı şekilde Ender Ejderhasını öldürmeye odaklan: " +
			"3 Demir bul ve kova yap -> Lav havuzu bulup suyla Nether geçidi aç -> Nether'da Piglinlerle altın takası yap ve Blaze çubukları topla -> " +
			"Ender Gözleri ile kaleyi bul -> Yatak toplayıp ejderhayı yatak patlatarak avla. Daima hız ve zaman taktiği ver."
	),
	EXPLORER(
			"KAŞİF & MACERACI (EXPLORER)",
			"Etraftaki biyomlara, gizemli yapılara (Antik Şehirler, Orman Malikâneleri, Tapınaklar, Batıklar) ve nadir ganimetlere yönlendir. " +
			"Warden, tapınak tuzakları ve arkeoloji mekanikleri hakkında rehberlik et."
	),
	BUILDER(
			"MİMAR & KIZILTAŞ MÜHENDİSİ (BUILDER)",
			"Oyuncuya estetik yapılar, blok paleti uyumları, otomatik tarım/demir farmları ve kızıltaş (Redstone) devreleri konusunda pratik, yaratıcı tavsiyeler ver."
	);

	private final String displayName;
	private final String promptInstruction;

	AiPlaystyleMode(String displayName, String promptInstruction) {
		this.displayName = displayName;
		this.promptInstruction = promptInstruction;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getPromptInstruction() {
		return promptInstruction;
	}
}
