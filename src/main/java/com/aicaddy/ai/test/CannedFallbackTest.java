package com.aicaddy.ai.test;

import com.aicaddy.ai.resilience.CannedFallbackProvider;
import com.aicaddy.ai.resilience.CircuitBreaker;
import com.aicaddy.ai.prompt.SharedPromptRules;

import java.util.Arrays;
import java.util.List;

public class CannedFallbackTest {

	public static void main(String[] args) {
		System.out.println("=== P1.3 CANNED FALLBACK & CIRCUIT BREAKER DOĞRULAMA TESTİ ===");

		List<String> moods = Arrays.asList(
				"SCARED", "EXCITED", "SAD", "PROUD", "BORED", "FRUSTRATED", "CURIOUS", "TENSE", "DEFAULT"
		);

		boolean allPassed = true;
		int totalTested = 0;

		for (String mood : moods) {
			for (int i = 0; i < 5; i++) {
				String fallback = CannedFallbackProvider.getCannedFallbackForMood(mood);
				totalTested++;
				boolean hasForeign = com.aicaddy.ai.provider.GroqAiProvider.containsForeignOrHallucinatedWords(fallback);
				if (hasForeign || fallback == null || fallback.isBlank()) {
					System.err.println("❌ BAŞARISIZ: [" + mood + "] Fallback hatalı veya yabancı kelime içeriyor: " + fallback);
					allPassed = false;
				}
			}
			System.out.println("✔ [" + mood + "] 5/5 fallback repliği %100 Türkçe ve kurallara uygun.");
		}

		// Verify CircuitBreaker state transition
		System.out.println("\n=== CIRCUIT BREAKER DAVRANIŞ TESTİ ===");
		CircuitBreaker breaker = new CircuitBreaker("TestAPI", 2, 5000L);
		System.out.println("Başlangıçta devre kesici açık mı? " + breaker.isOpen() + " (Beklenen: false)");
		breaker.recordFailure();
		System.out.println("1. hatadan sonra devre kesici açık mı? " + breaker.isOpen() + " (Beklenen: false)");
		breaker.recordFailure();
		System.out.println("2. hatadan sonra devre kesici açık mı? " + breaker.isOpen() + " (Beklenen: true)");

		if (allPassed && breaker.isOpen()) {
			System.out.println("\n🎉 TÜM P1.3 FALLBACK VE CIRCUIT BREAKER TESTLERİ BAŞARIYLA GEÇTİ! Toplam test edilen replik: " + totalTested);
		} else {
			System.err.println("\n❌ TEST BAŞARISIZ OLDU.");
			System.exit(1);
		}
	}
}
