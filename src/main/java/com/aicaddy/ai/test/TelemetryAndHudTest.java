package com.aicaddy.ai.test;

import com.aicaddy.ai.debug.CompanionTelemetryLogger;
import com.aicaddy.ai.mood.CompanionMoodState;
import com.aicaddy.ai.provider.GroqAiProvider;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public class TelemetryAndHudTest {

	public static void main(String[] args) throws Exception {
		System.out.println("=== P3.1 GÖRSEL ARAYÜZ (ROZET/HUD) & P2.3 TELEMETRİ LOG DOĞRULAMA TESTİ ===");

		// 1. Verify Dynamic Mood Chat Badges (P3.1)
		System.out.println("\n--- 1. Dinamik Sohbet Rozetleri (Chat Badge) Testi ---");
		for (CompanionMoodState mood : CompanionMoodState.values()) {
			String badge = mood.getChatBadge();
			String hud = mood.getActionBarNotification();
			System.out.println(mood.name() + " -> Rozet: " + badge + " | HUD: " + hud);

			if (!badge.contains("Kedi - ")) {
				throw new RuntimeException("❌ HATA: Sohbet rozeti formatı hatalı: " + badge);
			}
			if (GroqAiProvider.containsForeignOrHallucinatedWords(badge)) {
				throw new RuntimeException("❌ HATA: Rozet içinde yabancı kelime var: " + badge);
			}
			if (GroqAiProvider.containsForeignOrHallucinatedWords(hud)) {
				throw new RuntimeException("❌ HATA: HUD bildiriminde yabancı kelime var: " + hud);
			}
		}
		System.out.println("✔ Tüm 8 duygu durumu için sohbet rozetleri ve HUD bildirimleri kurallı ve sıfır yabancı kelimeli.");

		// 2. Verify Telemetry JSONL Recording (P2.3)
		System.out.println("\n--- 2. Telemetri JSONL Loglama Testi ---");
		File logDir = new File("logs");
		File telemetryFile = new File(logDir, "ai_caddy_telemetry.jsonl");
		if (telemetryFile.exists()) {
			telemetryFile.delete();
		}

		String testUuid = UUID.randomUUID().toString();
		String testPlayer = "Tuncay_Test";

		// Log a test turn
		CompanionTelemetryLogger.logTurnAsync(
				testUuid,
				testPlayer,
				"CHAT",
				"groq-llama-3.3-70b",
				1250,
				410,
				CompanionMoodState.EXCITED,
				25,
				false,
				"Elmas bulduk!",
				"YOO BE! ELMAS MI LAN O! KAZMAYA DİKKAT ET!"
		);

		// Log a test mood transition
		CompanionTelemetryLogger.logMoodTransitionAsync(
				testUuid,
				testPlayer,
				"FOUND_DIAMOND",
				CompanionMoodState.CURIOUS,
				CompanionMoodState.EXCITED,
				25
		);

		// Wait for async IO
		Thread.sleep(600);

		if (!telemetryFile.exists()) {
			throw new RuntimeException("❌ HATA: ai_caddy_telemetry.jsonl dosyası oluşturulamadı!");
		}

		List<String> lines = Files.readAllLines(telemetryFile.toPath());
		System.out.println("Kaydedilen Telemetri Satır Sayısı: " + lines.size() + " (Beklenen: en az 2)");
		if (lines.size() < 2) {
			throw new RuntimeException("❌ HATA: Telemetri satırları dosyaya tam yazılmadı! Satır: " + lines.size());
		}

		// Find Turn and Mood JSON lines (async order-independent)
		String turnLine = lines.stream().filter(l -> l.contains("TURN_TELEMETRY")).findFirst()
				.orElseThrow(() -> new RuntimeException("❌ HATA: TURN_TELEMETRY satırı bulunamadı!"));
		String moodLine = lines.stream().filter(l -> l.contains("MOOD_TRANSITION")).findFirst()
				.orElseThrow(() -> new RuntimeException("❌ HATA: MOOD_TRANSITION satırı bulunamadı!"));

		// Verify Turn JSON line
		JsonObject turnObj = JsonParser.parseString(turnLine).getAsJsonObject();
		if (turnObj.get("latency_ms").getAsLong() != 1250 || turnObj.get("affinity_score").getAsInt() != 25) {
			throw new RuntimeException("❌ HATA: Turn telemetri sayısal değerleri uyuşmuyor!");
		}

		// Verify Mood Transition JSON line
		JsonObject moodObj = JsonParser.parseString(moodLine).getAsJsonObject();
		if (!moodObj.get("new_mood").getAsString().equals("EXCITED")) {
			throw new RuntimeException("❌ HATA: Mood transition new_mood değeri uyuşmuyor!");
		}

		System.out.println("✔ Telemetri JSONL dosya oluşturma, şema doğruluğu ve asenkron I/O testleri başarıyla geçti.");
		System.out.println("\n🎉 P3.1 GÖRSEL ARAYÜZ (ROZET & HUD) VE P2.3 TELEMETRİ BİRİM TESTLERİ %100 BAŞARILI!");
	}
}
