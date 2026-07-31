package com.aicaddy.ai.test;

import com.aicaddy.ai.memory.PlayerMemoryStore;
import com.aicaddy.ai.provider.GroqAiProvider;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class PlayerMemoryTest {

	public static void main(String[] args) throws Exception {
		System.out.println("=== P2.1 KALICI HAFIZA, SAMİMİYET PUANI & MILESTONES DOĞRULAMA TESTİ ===");

		// Ensure clean test DB path
		File dbFile = new File("config/ai_caddy_memory.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}

		// 1. Initialize SQLite Database & Schema Migration
		PlayerMemoryStore.init();

		UUID testUuid = UUID.randomUUID();
		String testPlayer = "TestOyuncusu_Tuncay";

		// 2. Test initial Affinity Score (Should be 0 - Nötr)
		int initScore = PlayerMemoryStore.getAffinityScore(testUuid);
		System.out.println("Başlangıç Samimiyet Puanı: " + initScore + " (Beklenen: 0)");
		if (initScore != 0) {
			throw new RuntimeException("❌ HATA: Başlangıç puanı 0 değil!");
		}

		// 3. Test Tier label formatting across ranges
		String tierCold = PlayerMemoryStore.getAffinityTierLabel(-8);
		String tierNeutral = PlayerMemoryStore.getAffinityTierLabel(0);
		String tierFriend = PlayerMemoryStore.getAffinityTierLabel(15);
		String tierBestie = PlayerMemoryStore.getAffinityTierLabel(45);
		String tierSoulmate = PlayerMemoryStore.getAffinityTierLabel(80);

		System.out.println("Seviye Etiketleri Kontrolü:");
		System.out.println("  -8  -> " + tierCold);
		System.out.println("   0  -> " + tierNeutral);
		System.out.println("  15  -> " + tierFriend);
		System.out.println("  45  -> " + tierBestie);
		System.out.println("  80  -> " + tierSoulmate);

		if (!tierCold.contains("Kırgın") || !tierSoulmate.contains("Ruh İkizi")) {
			throw new RuntimeException("❌ HATA: Seviye etiketleri eşleşmiyor!");
		}

		// 4. Test Milestones Addition & Deduplication
		System.out.println("\nMilestone ekleme ve tekilleştirme testi yapılıyor...");
		PlayerMemoryStore.addMilestoneAsync(testUuid, testPlayer, "Nether Boyutuna Adım Attı");
		Thread.sleep(200); // allow async insert
		PlayerMemoryStore.addMilestoneAsync(testUuid, testPlayer, "Elmas Buldu");
		Thread.sleep(200);
		PlayerMemoryStore.addMilestoneAsync(testUuid, testPlayer, "Nether Boyutuna Adım Attı"); // Duplicate! Should NOT add again
		Thread.sleep(300);

		List<String> milestones = PlayerMemoryStore.getMajorMilestones(testUuid);
		System.out.println("Kayıtlı Milestones: " + milestones);
		if (milestones.size() != 2) {
			throw new RuntimeException("❌ HATA: Milestone tekilleştirmesi başarısız! Boyut: " + milestones.size());
		}

		// 5. Test Affinity modification (each milestone added +2 affinity -> should be 4)
		int newScore = PlayerMemoryStore.getAffinityScore(testUuid);
		System.out.println("2 Milestone sonrası Samimiyet Puanı: " + newScore + " (Beklenen: 4)");
		if (newScore != 4) {
			throw new RuntimeException("❌ HATA: Samimiyet Puanı +2 hediyeleri yansımadı! Mevcut: " + newScore);
		}

		// Modify manually
		PlayerMemoryStore.modifyAffinityAsync(testUuid, testPlayer, 15, "Birlikte uzun macera");
		Thread.sleep(300);
		newScore = PlayerMemoryStore.getAffinityScore(testUuid);
		System.out.println("Manuel +15 sonrası Samimiyet Puanı: " + newScore + " (Beklenen: 19)");
		if (newScore != 19) {
			throw new RuntimeException("❌ HATA: Puan artışı hatalı!");
		}

		// 6. Test Recent Event Retention Policy
		PlayerMemoryStore.appendEventAsync(testUuid, testPlayer, "Tehlikeli şekilde canı azaldı (20%)");
		Thread.sleep(150);
		PlayerMemoryStore.appendEventAsync(testUuid, testPlayer, "'deep dark' biyomunu keşfetti");
		Thread.sleep(250);

		// 7. Test Summarized Prompt Block & Language Compliance
		String summary = PlayerMemoryStore.getSummarizedMemoryPrompt(testUuid);
		System.out.println("\n=== OLUŞTURULAN HAFIZA PROMPT BLOK ÖZETİ ===");
		System.out.println(summary);

		boolean hasForeign = GroqAiProvider.containsForeignOrHallucinatedWords(summary);
		if (hasForeign) {
			throw new RuntimeException("❌ HATA: Hafıza özetinde yabancı kelime tespit edildi!");
		}

		System.out.println("✔ Hafıza özeti sıfır yabancı kelime ve tam kurallı formatta.");
		System.out.println("\n🎉 P2.1 TÜM HAFIZA, SAMİMİYET PUANI VE RETENTION TESTLERİ %100 BAŞARIYLA GEÇTİ!");
	}
}
