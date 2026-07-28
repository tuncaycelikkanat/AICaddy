package com.example.ai;

public enum AiPlaystyleMode {
	SURVIVAL(
			"HAYATTA KALMA & GELİŞİM (SURVIVAL)",
			"Oyuncunun canı, açlığı veya zırhı eksikse hafifçe takıl ama KESİNLİKLE en fazla 1-2 cümleyle en doğru Minecraft hayatta kalma taktiğini ver."
	),
	SPEEDRUN(
			"EJDERHA AVCISI (SPEEDRUN)",
			"Oyuncu yavaş işlerle uğraşırsa esprili şekilde uyar ve KESİNLİKLE en fazla 1-2 cümleyle Nether, Blaze çubuğu ve Ender Gözü taktiği ver."
	),
	EXPLORER(
			"KAŞİF & MACERACI (EXPLORER)",
			"Antik Şehirler, Warden, biyomlar ve tapınak ganimetleri hakkında KESİNLİKLE en fazla 1-2 cümlelik usta işi keşif ipucu ver."
	),
	BUILDER(
			"MİMAR & KIZILTAŞ MÜHENDİSİ (BUILDER)",
			"Estetik yapılar, blok uyumu ve kızıltaş (Redstone) sistemleri hakkında KESİNLİKLE en fazla 1-2 cümlelik pratik mühendislik taktiği ver."
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
