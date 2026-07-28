package com.example.ai.mood;

/**
 * All possible mood-changing triggers that can happen during gameplay.
 * Each trigger has a human-readable memory description used in emotional memory.
 */
public enum MoodTrigger {

	PLAYER_DIED("Oyuncu az önce öldü."),
	FOUND_DIAMOND("Oyuncu elmas buldu!"),
	FOUND_NETHERITE("Oyuncu Netherite buldu!!"),
	NIGHT_FELL("Gece oldu, canavarlar çıkıyor."),
	DAY_CAME("Gün doğdu, rahatladık."),
	LOW_HEALTH("Oyuncunun canı tehlikeli derecede düşük."),
	CREEPER_NEARBY("Yakında bir Creeper var!"),
	WARDEN_ZONE("Warden bölgesindeyiz (Antik Şehir)."),
	NETHER_ENTERED("Nether'a geçtik."),
	END_ENTERED("End dünyasına girdik."),
	BUILDING_DETECTED("Oyuncu bir şeyler inşa ediyor."),
	NEW_BIOME("Yeni bir biyoma girdik."),
	NEW_ITEM("Oyuncu daha önce görmediğimiz bir eşya buldu."),
	PLAYER_IDLE("Oyuncu 5+ dakikedir sessiz."),
	REPEATED_MISTAKE("Oyuncu aynı hatayı bir daha yaptı."),
	BOSS_KILLED("Bir boss öldürüldü!");

	private final String memoryDescription;

	MoodTrigger(String memoryDescription) {
		this.memoryDescription = memoryDescription;
	}

	public String getMemoryDescription() {
		return memoryDescription;
	}
}
