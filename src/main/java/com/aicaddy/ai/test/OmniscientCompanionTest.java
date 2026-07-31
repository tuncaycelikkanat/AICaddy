package com.aicaddy.ai.test;

import com.aicaddy.ai.context.PlayerActivityTracker;
import com.aicaddy.ai.context.StructureAndPoiDetector;
import com.aicaddy.entity.AiCompanionEntity;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * P10 — Omniscient and Entity Companion Test Suite.
 * Verifies Layer 1 (Structure & POI wisdom), Layer 2 (Sliding Window Activity State),
 * and Layer 3 (Physical companion command parsing) in clean Turkish.
 */
public class OmniscientCompanionTest {

	public static void main(String[] args) throws Exception {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();

		System.out.println("=== AŞAMA 10: ÇEVRESEL VE YAPISAL BİLGELİK, AKTİVİTE TAKİBİ VE CANLI YOL ARKADAŞI TESTİ ===");

		// 1. Test Structure Key Translation (Layer 1)
		System.out.println("\n--- 1. Yapı Adı ve Bağlam Çeviri Testi ---");
		verifyEquals("Köy Yerleşkesi (Ova Köyü)",
				StructureAndPoiDetector.translateStructureKey("minecraft:village_plains"));
		verifyEquals("Köy Yerleşkesi (Çöl Köyü)",
				StructureAndPoiDetector.translateStructureKey("minecraft:village_desert"));
		verifyEquals("Çöl Piramidi",
				StructureAndPoiDetector.translateStructureKey("minecraft:desert_pyramid"));
		verifyEquals("Nether Kalesi",
				StructureAndPoiDetector.translateStructureKey("minecraft:fortress"));
		verifyEquals("Sınav Odaları (Trial Chambers)",
				StructureAndPoiDetector.translateStructureKey("minecraft:trial_chambers"));
		verifyEquals("Doğal Açık Alan",
				StructureAndPoiDetector.detectStructureName(null, null));
		System.out.println("✔ 1. Katman: Yapı adları ve çeviri motoru %100 doğru.");

		// 2. Test Sliding Window Activity Tracker (Layer 2)
		System.out.println("\n--- 2. Uğraş ve Aktivite Durumu (Sliding Window) Testi ---");
		UUID testUuid = UUID.randomUUID();
		PlayerActivityTracker.clear(testUuid);

		String initialSummary = PlayerActivityTracker.getActivitySummary(testUuid);
		System.out.println("Başlangıç durumu: " + initialSummary);
		if (!initialSummary.contains("KEŞİF")) {
			throw new RuntimeException("Varsayılan durum KEŞİF olmalı!");
		}

		// Record 6 mining block breaks -> should switch to MADENCİLİK
		for (int i = 0; i < 6; i++) {
			PlayerActivityTracker.recordBlockBreak(testUuid, Blocks.DIAMOND_ORE);
		}
		String miningSummary = PlayerActivityTracker.getActivitySummary(testUuid);
		System.out.println("6 maden kırdıktan sonra: " + miningSummary);
		if (!miningSummary.contains("MADENCİLİK")) {
			throw new RuntimeException("Durum MADENCİLİK olmalı: " + miningSummary);
		}

		// Record 4 combat attacks -> combat overrides mining in precedence
		for (int i = 0; i < 4; i++) {
			PlayerActivityTracker.recordCombatAttack(testUuid);
		}
		String combatSummary = PlayerActivityTracker.getActivitySummary(testUuid);
		System.out.println("4 savaş vuruşundan sonra: " + combatSummary);
		if (!combatSummary.contains("SAVAŞ")) {
			throw new RuntimeException("Durum SAVAŞ olmalı: " + combatSummary);
		}
		System.out.println("✔ 2. Katman: Zaman pencereli aktivite takip motoru %100 doğru.");

		// 3. Test Companion Command Matching (Layer 3)
		System.out.println("\n--- 3. Canlı Yol Arkadaşı Sesli/Yazılı Komut Algılama Testi ---");
		verifyTrue(AiCompanionEntity.isSitCommand("Kedi burada bekle biraz"), "Otur komutu 1");
		verifyTrue(AiCompanionEntity.isSitCommand("otur yerinde"), "Otur komutu 2");
		verifyFalse(AiCompanionEntity.isSitCommand("merhaba nasılsın"), "Otur komutu negatif");

		verifyTrue(AiCompanionEntity.isFollowCommand("yanıma gel dostum"), "Takip komutu 1");
		verifyTrue(AiCompanionEntity.isFollowCommand("beni takip et"), "Takip komutu 2");

		verifyTrue(AiCompanionEntity.isInvestigateCommand("şuraya bak örste ne var"), "İncele komutu 1");
		verifyTrue(AiCompanionEntity.isInvestigateCommand("şu bloğu incele"), "İncele komutu 2");
		System.out.println("✔ 3. Katman: Fiziksel komut ayrıştırıcı %100 doğru.");

		System.out.println("\n=== BÜTÜN AŞAMA 10 TESTLERİ BAŞARIYLA TAMAMLANDI ===");
	}

	private static void verifyEquals(String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new RuntimeException("Beklenen: '" + expected + "', ama alınan: '" + actual + "'");
		}
	}

	private static void verifyTrue(boolean condition, String msg) {
		if (!condition) {
			throw new RuntimeException("Test başarısız (Doğru bekleniyordu): " + msg);
		}
	}

	private static void verifyFalse(boolean condition, String msg) {
		if (condition) {
			throw new RuntimeException("Test başarısız (Yanlış bekleniyordu): " + msg);
		}
	}
}
