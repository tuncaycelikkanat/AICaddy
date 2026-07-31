package com.aicaddy.ai.test;

import com.aicaddy.ai.event.CompanionEventBus;
import com.aicaddy.ai.memory.MemoryConsolidator;
import com.aicaddy.ai.memory.SemanticEpisodeMemory;
import com.aicaddy.ai.resilience.CircuitBreaker;
import com.aicaddy.ai.resilience.ExponentialBackoffRetry;
import com.aicaddy.ai.utility.UtilityActionEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Formal Automated Verification Suite for AI Caddy Enterprise AI & Behavioral Intelligence.
 * Verifies CircuitBreaker failover, Reactive Event Bus debounce/throttle,
 * Semantic Episode Memory (TF-IDF RAG), and Utility AI non-linear scoring.
 */
public class EnterpriseAiSuiteTester {

	private static int totalTests = 0;
	private static int passedTests = 0;
	private static final StringBuilder REPORT = new StringBuilder();

	public static void main(String[] args) {
		System.out.println("================================================================");
		System.out.println(" 🤖 AI Caddy v2 — Enterprise AI & Behavioral Intelligence Suite");
		System.out.println("================================================================");

		REPORT.append("# AI Caddy v2 — Enterprise AI Doğrulama Test Raporu\n\n");
		REPORT.append("Bu rapor, sistemdeki Dayanıklılık (Resilience), Reaktif Olay Otobüsü (Event Bus), Semantik Hafıza (RAG) ve Fayda Yönelimli Davranış Zekası (Utility AI) modüllerinin otomatik test sonuçlarını gösterir.\n\n");

		try {
			test1_ResilienceCircuitBreaker();
			test2_ReactiveEventBusDebounce();
			test3_SemanticEpisodeMemoryRAG();
			test4_UtilityAiScoringEngine();
			test5_AutonomousCompanionActions();
			test6_RadarConfigurationAndXrayMode();
			test7_PromptModeConfigurationAndFreestyle();
		} catch (Throwable t) {
			System.err.println("❌ TEST ENGINE CRASHED: " + t.getMessage());
			t.printStackTrace();
		}

		System.out.println("================================================================");
		System.out.printf(" ✔ ENTERPRISE AI TEST SUITE COMPLETED: %d / %d TESTS PASSED%n", passedTests, totalTests);
		System.out.println("================================================================");

		REPORT.append("## Özet Sonuç\n");
		REPORT.append("- **Toplam Test:** ").append(totalTests).append("\n");
		REPORT.append("- **Başarılı:** ").append(passedTests).append("\n");
		REPORT.append("- **Durum:** ").append(passedTests == totalTests ? "✔ TÜM KURUMSAL KATMANLAR DOĞRULANDI" : "❌ Bazı testler başarısız oldu").append("\n");

		saveReport("ENTERPRISE_AI_TEST_REPORT.md");
		saveReport("artifacts/enterprise_ai_test_report.md");

		if (passedTests < totalTests) {
			System.exit(1);
		}
	}

	private static void test1_ResilienceCircuitBreaker() {
		System.out.println("\n[TEST 1/4] Resilience — Circuit Breaker & Exponential Backoff Testi");
		REPORT.append("### 1. Dayanıklılık — Circuit Breaker & Exponential Backoff Testleri\n\n");

		CircuitBreaker breaker = new CircuitBreaker("TestBreaker", 3, 60_000L);
		assertTest("Initial state is CLOSED", breaker.getState() == CircuitBreaker.CircuitState.CLOSED);

		breaker.recordFailure();
		breaker.recordFailure();
		breaker.recordFailure();
		assertTest("Circuit transitions to OPEN after 3 failures", breaker.getState() == CircuitBreaker.CircuitState.OPEN);

		ExponentialBackoffRetry retry = new ExponentialBackoffRetry(2, 50L, 200L);
		Optional<String> blockedResult = retry.execute(() -> "SUCCESS", breaker);
		assertTest("ExponentialBackoffRetry immediately skips execution when OPEN", blockedResult.isEmpty());

		breaker.reset();
		assertTest("Manual reset returns state to CLOSED", breaker.getState() == CircuitBreaker.CircuitState.CLOSED);

		Optional<String> successResult = retry.execute(() -> "RECOVERED", breaker);
		assertTest("ExponentialBackoffRetry succeeds when CLOSED",
				successResult.isPresent() && "RECOVERED".equals(successResult.get()));
	}

	private static void test2_ReactiveEventBusDebounce() {
		System.out.println("\n[TEST 2/4] Reactive Event Bus — Debounce & Throttle Koruması Testi");
		REPORT.append("### 2. Reaktif Olay Otobüsü (`CompanionEventBus`) Debounce Testleri\n\n");

		CompanionEventBus.clear();
		AtomicInteger receivedCount = new AtomicInteger(0);
		CompanionEventBus.subscribe(event -> receivedCount.incrementAndGet());

		UUID testUuid = UUID.randomUUID();
		// Publish 5 duplicate normal priority events rapidly
		CompanionEventBus.publish("DAMAGE_TAKEN", testUuid, "Took 2 damage", 5);
		CompanionEventBus.publish("DAMAGE_TAKEN", testUuid, "Took 2 damage", 5);
		CompanionEventBus.publish("DAMAGE_TAKEN", testUuid, "Took 2 damage", 5);
		CompanionEventBus.publish("DAMAGE_TAKEN", testUuid, "Took 2 damage", 5);
		CompanionEventBus.publish("DAMAGE_TAKEN", testUuid, "Took 2 damage", 5);

		assertTest("Debounce filter suppressed 4 duplicate events (only 1 received)", receivedCount.get() == 1);

		// Publish a critical event (priority 10) which bypasses debounce and throttle
		CompanionEventBus.publish("DAMAGE_TAKEN", testUuid, "Critical hit!", 10);
		assertTest("Critical priority event bypasses debounce/throttle", receivedCount.get() == 2);
	}

	private static void test3_SemanticEpisodeMemoryRAG() {
		System.out.println("\n[TEST 3/4] Semantik Hafıza & RAG (`SemanticEpisodeMemory`) Testi");
		REPORT.append("### 3. Semantik Hafıza & RAG (`SemanticEpisodeMemory`) Testleri\n\n");

		UUID playerUuid = UUID.randomUUID();
		SemanticEpisodeMemory.clear(playerUuid);
		MemoryConsolidator.clear(playerUuid);

		SemanticEpisodeMemory.recordEpisode(playerUuid,
				"Nether kalesinde Blaze çubukları topladık, lav tehlikesi vardı.", "nether kale blaze lav");
		SemanticEpisodeMemory.recordEpisode(playerUuid,
				"Koyun kırptık ve yatak yaptık, sakin bir gündü.", "koyun yatak sakin");
		SemanticEpisodeMemory.recordEpisode(playerUuid,
				"Creeper evimizin köşesini patlattı, duvarı tamir ettik.", "creeper ev patlama");

		assertTest("3 semantic episodes recorded successfully",
				SemanticEpisodeMemory.getEpisodeCount(playerUuid) == 3);

		List<SemanticEpisodeMemory.Episode> netherResults =
				SemanticEpisodeMemory.findRelevantEpisodes(playerUuid, "nether lav", 1);
		assertTest("Semantic RAG query 'nether lav' retrieved Nether episode",
				!netherResults.isEmpty() && netherResults.get(0).summary().contains("Nether"));

		List<SemanticEpisodeMemory.Episode> creeperResults =
				SemanticEpisodeMemory.findRelevantEpisodes(playerUuid, "creeper patla", 1);
		assertTest("Semantic RAG query 'creeper patla' retrieved Creeper episode",
				!creeperResults.isEmpty() && creeperResults.get(0).summary().contains("Creeper"));

		MemoryConsolidator.consolidateChatTurn(playerUuid,
				"Creeper patlamasından çok korktum!", "Dikkatli olalım dostum.");
		assertTest("MemoryConsolidator extracted persona note from user chat",
				!MemoryConsolidator.getPersonaNotes(playerUuid).isEmpty());
	}

	private static void test4_UtilityAiScoringEngine() {
		System.out.println("\n[TEST 4/4] Davranışsal Zeka — Utility AI Scoring Engine Testi");
		REPORT.append("### 4. Fayda Yönelimli Davranış Zekası (`UtilityActionEngine`) Testleri\n\n");

		// Scenario A: Critical threat (0.90) and low HP (0.20)
		UtilityActionEngine.UtilityDecision emergencyDecision =
				UtilityActionEngine.decideBestAction(0.20f, 0.90f, -0.50, 0.80, false);
		assertTest("Emergency Critical Threat -> FLEE_DANGER selected",
				emergencyDecision.action() == UtilityActionEngine.UtilityAction.FLEE_DANGER);

		// Scenario B: Safe environment (0.10) with recent achievement
		UtilityActionEngine.UtilityDecision celebrateDecision =
				UtilityActionEngine.decideBestAction(0.90f, 0.10f, 0.40, 0.60, true);
		assertTest("Safe Environment + Achievement -> CELEBRATE_ACHIEVEMENT selected",
				celebrateDecision.action() == UtilityActionEngine.UtilityAction.CELEBRATE_ACHIEVEMENT);

		// Scenario C: Safe environment (0.10), low arousal, calm -> STAY_CLOSE or SIT_AND_REST
		UtilityActionEngine.UtilityDecision calmDecision =
				UtilityActionEngine.decideBestAction(0.80f, 0.05f, 0.00, 0.10, false);
		assertTest("Calm safe state -> default STAY_CLOSE or SIT_AND_REST selected",
				calmDecision.action() == UtilityActionEngine.UtilityAction.STAY_CLOSE ||
				calmDecision.action() == UtilityActionEngine.UtilityAction.SIT_AND_REST);
	}

	private static void test5_AutonomousCompanionActions() {
		System.out.println("\n[TEST 5/5] Otonom Yol Arkadaşı — Tool Calling Action Parser Testi");
		REPORT.append("### 5. Otonom Eylem ve Araç Çağrısı (`CompanionActionExecutor`) Testleri\n\n");

		com.aicaddy.ai.action.CompanionActionExecutor.clearPendingActions();
		String sampleJson = """
				{
				  "durum_analizi": "Etrafta 1 creeper ve elmas cevheri var",
				  "kedi_duygusu": "GERGİN",
				  "ic_dusunce": "Creeper'a saldırıp elması korumalıyım",
				  "actions": [
				    { "type": "ATTACK_ENTITY", "target": "Creeper", "reason": "Oyuncuyu koruma" },
				    { "type": "MINE_BLOCK", "x": 14, "y": -58, "z": 22, "reason": "Elmas toplama" }
				  ],
				  "final_replik": "Arkanda Creeper var, dikkat et!"
				}
				""";
		com.aicaddy.ai.action.CompanionActionExecutor.queueActionsFromJson(sampleJson);
		assertTest("LLM JSON contains 2 valid actions -> Queued in PENDING_ACTIONS",
				com.aicaddy.ai.action.CompanionActionExecutor.getPendingActionCount() == 2);
		com.aicaddy.ai.action.CompanionActionExecutor.clearPendingActions();
	}

	private static void test6_RadarConfigurationAndXrayMode() {
		System.out.println("\n[TEST 6/6] Çevresel Algı Radarı — X-Ray & Yarıçap Konfigürasyon Testi");
		REPORT.append("### 6. Çevresel Algı Radarı (`EnvironmentalRadar`) Testleri\n\n");

		com.aicaddy.ai.context.EnvironmentalRadar.setRadarRange(12);
		assertTest("Radar range set to 12 blocks within bounds",
				com.aicaddy.ai.context.EnvironmentalRadar.getRadarRange() == 12);

		com.aicaddy.ai.context.EnvironmentalRadar.setXrayMode(true);
		assertTest("X-Ray underground scan toggled ON",
				com.aicaddy.ai.context.EnvironmentalRadar.isXrayMode());

		// Reset to default
		com.aicaddy.ai.context.EnvironmentalRadar.setRadarRange(15);
		com.aicaddy.ai.context.EnvironmentalRadar.setXrayMode(false);
		assertTest("X-Ray underground scan toggled OFF (Sees only Line of Sight)",
				!com.aicaddy.ai.context.EnvironmentalRadar.isXrayMode() &&
				com.aicaddy.ai.context.EnvironmentalRadar.getRadarRange() == 15);
	}

	private static void test7_PromptModeConfigurationAndFreestyle() {
		System.out.println("\n[TEST 7/7] Prompt Modları — Serbest & Kuralsız Mod Testi");
		REPORT.append("### 7. Prompt Modları (`SharedPromptRules.PromptMode`) Testleri\n\n");

		com.aicaddy.ai.prompt.SharedPromptRules.setActiveMode(com.aicaddy.ai.prompt.SharedPromptRules.PromptMode.SERBEST);
		assertTest("PromptMode switched to SERBEST (Freestyle mode without strict sentence rules)",
				com.aicaddy.ai.prompt.SharedPromptRules.getActiveMode() == com.aicaddy.ai.prompt.SharedPromptRules.PromptMode.SERBEST &&
				!com.aicaddy.ai.prompt.SharedPromptRules.getActiveMode().getResponseRules().contains("Maksimum 1-2"));

		com.aicaddy.ai.prompt.SharedPromptRules.setActiveMode(com.aicaddy.ai.prompt.SharedPromptRules.PromptMode.TAKTIKSEL);
		assertTest("PromptMode switched to TAKTIKSEL (Commander style)",
				com.aicaddy.ai.prompt.SharedPromptRules.getActiveMode() == com.aicaddy.ai.prompt.SharedPromptRules.PromptMode.TAKTIKSEL);

		// Reset to default
		com.aicaddy.ai.prompt.SharedPromptRules.setActiveMode(com.aicaddy.ai.prompt.SharedPromptRules.PromptMode.STANDART);
		assertTest("PromptMode reset to STANDART default",
				com.aicaddy.ai.prompt.SharedPromptRules.getActiveMode() == com.aicaddy.ai.prompt.SharedPromptRules.PromptMode.STANDART);
	}

	private static void assertTest(String description, boolean condition) {
		totalTests++;
		if (condition) {
			passedTests++;
			System.out.println("  ✔ [PASS] " + description);
			REPORT.append("- ✔ **BAŞARILI:** ").append(description).append("\n");
		} else {
			System.err.println("  ❌ [FAIL] " + description);
			REPORT.append("- ❌ **BAŞARISIZ:** ").append(description).append("\n");
		}
	}

	private static void saveReport(String relativePath) {
		try {
			Path p = Paths.get(relativePath);
			if (p.getParent() != null) {
				Files.createDirectories(p.getParent());
			}
			Files.writeString(p, REPORT.toString());
			System.out.println("✔ Report saved to: " + p.toAbsolutePath());
		} catch (IOException e) {
			System.err.println("⚠️ Could not save report to " + relativePath + ": " + e.getMessage());
		}
	}
}
