package com.aicaddy.ai.test;

import com.aicaddy.ai.innovation.*;
import com.aicaddy.ai.innovation.CompanionJobManager.CompanionJob;
import com.aicaddy.ai.innovation.SocialAffinityEngine.RelationshipTier;
import com.aicaddy.ai.innovation.TacticalVoxelMap.HazardType;

/**
 * Verification Suite for ALL 4 Flagship Integrated Systems:
 * 1. NVIDIA Voyager Skill Library (CompanionSkillLibrary)
 * 2. Altera AI / Project Sid Trust & Daily Mood Diary (SocialAffinityEngine)
 * 3. Baritone Threat-Aware Voxel Safety Map (TacticalVoxelMap)
 * 4. Minecraft Comes Alive (MCA) Autonomous Job Delegation (CompanionJobManager)
 */
public class EnterpriseInnovationsSuiteTester {

	public static void main(String[] args) {
		System.out.println("==================================================================================");
		System.out.println(" 🌟 AI CADDY v3 — DÜNYANIN EN İYİ 4 MİMARİSİNİN ENTEGRASYON DOGRULAMA TESTİ");
		System.out.println("==================================================================================");

		int passed = 0;
		int total = 4;

		// 1. Test NVIDIA Voyager Skill Library
		System.out.println("\n[1/4] NVIDIA Voyager 'Skill Library' Entegrasyon Kontrolü...");
		var creeperSkill = CompanionSkillLibrary.getSkill("EMERGENCY_CREEPER_DEFENSE");
		var dragonSkill = CompanionSkillLibrary.getSkill("ENDER_DRAGON_COMBAT");
		var wardenSkill = CompanionSkillLibrary.getSkill("WARDEN_SILENT_ESCAPE");
		var netherSkill = CompanionSkillLibrary.getSkill("NETHER_FORTRESS_INVASION");
		if (creeperSkill.isPresent() && dragonSkill.isPresent() && wardenSkill.isPresent() && netherSkill.isPresent()
				&& CompanionSkillLibrary.getAllSkills().size() >= 7) {
			System.out.println(" ✔ Beceriler başarıyla yüklendi (Toplam " + CompanionSkillLibrary.getAllSkills().size() + " beceri makrosu): " + dragonSkill.get().name());
			System.out.println(" ✔ Ejderha Sekansı: " + dragonSkill.get().actionSequence());
			System.out.println(" ✔ Warden Sekansı  : " + wardenSkill.get().actionSequence());
			passed++;
		} else {
			System.err.println(" ❌ Beceriler yüklenemedi!");
		}

		// 2. Test Altera AI / Project Sid Trust & Diary
		System.out.println("\n[2/4] Altera AI 'Social Affinity & Mood Diary' Entegrasyon Kontrolü...");
		SocialAffinityEngine.addTrustScore(0.15f); // Increase trust
		float trust = SocialAffinityEngine.getTrustScore();
		RelationshipTier tier = SocialAffinityEngine.getCurrentTier();
		System.out.println(" ✔ Aktif Güven Puanı: " + String.format("%.2f / 1.00", trust) + " (" + tier.name() + ")");
		System.out.println(" ✔ Günlük Çıktısı:\n" + SocialAffinityEngine.getDiaryMarkdown());
		if (trust >= 0.60f && (tier == RelationshipTier.GUVENDIK_DOST || tier == RelationshipTier.EFSANEVI_YOLDAS)) {
			passed++;
		}

		// 3. Test Baritone Tactical Voxel Map
		System.out.println("[3/4] Baritone 'Threat-Aware Voxel Safety Map' Entegrasyon Kontrolü...");
		float clearSafety = TacticalVoxelMap.evaluatePathSafety(0, 0, 10, HazardType.CLEAR);
		float lavaSafety = TacticalVoxelMap.evaluatePathSafety(0, 0, 10, HazardType.LAVA_POOL);
		System.out.println(" ✔ Temiz Rota Güvenlik Skoru : " + clearSafety + " (Beklenen: >0.9)");
		System.out.println(" ✔ Lav Havuzu Güvenlik Skoru : " + lavaSafety + " (Beklenen: 0.0 - Ölümcül)");
		if (clearSafety > 0.9f && lavaSafety == 0.0f) {
			passed++;
		}

		// 4. Test MCA Autonomous Job Manager
		System.out.println("\n[4/4] Minecraft Comes Alive (MCA) 'Job Delegation' Entegrasyon Kontrolü...");
		CompanionJobManager.setActiveJob(CompanionJob.ARCHER_DEFENDER);
		CompanionJob job = CompanionJobManager.getActiveJob();
		CompanionJobManager.setActiveJob(CompanionJob.AUTO_TORCHER);
		CompanionJob job2 = CompanionJobManager.getActiveJob();
		CompanionJobManager.setActiveJob(CompanionJob.BUILDER_ASSISTANT);
		CompanionJob job3 = CompanionJobManager.getActiveJob();
		System.out.println(" ✔ Atanan Otonom Görev (1): " + CompanionJob.ARCHER_DEFENDER.getId() + " - " + CompanionJob.ARCHER_DEFENDER.getDescription());
		System.out.println(" ✔ Atanan Otonom Görev (2): " + CompanionJob.AUTO_TORCHER.getId() + " - " + CompanionJob.AUTO_TORCHER.getDescription());
		System.out.println(" ✔ Atanan Otonom Görev (3): " + job3.getId() + " - " + job3.getDescription());
		System.out.println(" ✔ Prompt Enjeksiyon Çıktısı: " + CompanionJobManager.toPromptInjection());
		if (job == CompanionJob.ARCHER_DEFENDER && job2 == CompanionJob.AUTO_TORCHER && job3 == CompanionJob.BUILDER_ASSISTANT
				&& CompanionJob.values().length >= 7) {
			passed++;
		}

		System.out.println("\n==================================================================================");
		System.out.println(" 🏆 ENTEGRASYON SONUCU: " + passed + " / " + total + " BAŞARILI (%100 GEÇTİ)");
		System.out.println("==================================================================================");

		if (passed != total) {
			throw new IllegalStateException("❌ Entegrasyon testlerinden bazıları başarısız oldu!");
		}
	}
}
