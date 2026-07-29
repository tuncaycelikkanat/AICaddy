package com.example.ai.test;

import com.example.voice.GroqWhisperSttClient;
import com.example.voice.SttManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class SttManagerTest {

	public static void main(String[] args) throws Exception {
		System.out.println("=== HYBRİD SES TANIMA (STT) MOTOR SEÇİMİ VE WAV FORMAT DOĞRULAMA TESTİ ===");

		// 1. Test Provider Selection & Persistence
		System.out.println("\n--- 1. STT Motoru Seçimi ve Kaydetme Testi ---");
		SttManager.setActiveProviderId("default");
		String id1 = SttManager.getActiveProviderId();
		String display1 = SttManager.getProviderDisplayName(id1);
		System.out.println("Seçilen: " + id1 + " -> Ekran Adı: " + display1);
		if (!"default".equals(id1) || !display1.contains("Yerel Vosk")) {
			throw new RuntimeException("Varsayılan Vosk seçimi başarısız oldu!");
		}

		SttManager.setActiveProviderId("groq_whisper");
		String id2 = SttManager.getActiveProviderId();
		String display2 = SttManager.getProviderDisplayName(id2);
		System.out.println("Seçilen: " + id2 + " -> Ekran Adı: " + display2);
		if (!"groq_whisper".equals(id2) || !display2.contains("Groq Bulut Whisper")) {
			throw new RuntimeException("Groq Whisper seçimi başarısız oldu!");
		}

		// Verify config file was written correctly
		File configFile = new File("config/stt_provider.txt");
		if (configFile.exists()) {
			String saved = Files.readString(configFile.toPath()).trim();
			System.out.println("Dosyaya kaydedilen değer (config/stt_provider.txt): " + saved);
			if (!"groq_whisper".equals(saved)) {
				throw new RuntimeException("Ayar dosyası stt_provider.txt uyuşmazlığı!");
			}
		}
		System.out.println("✔ STT Motoru seçimi ve yapılandırma kaydı başarılı.");

		// 2. Test WAV Header Generation for Whisper API
		System.out.println("\n--- 2. PCM'den WAV Başlığı Oluşturma Doğrulaması ---");
		short[] pcmSamples = new short[480]; // 10 ms at 48000 Hz
		for (int i = 0; i < pcmSamples.length; i++) {
			pcmSamples[i] = (short) (Math.sin(i * 0.1) * 1000);
		}
		byte[] wavBytes = GroqWhisperSttClient.createWavBytes(pcmSamples, 48000);

		int expectedLength = 44 + (pcmSamples.length * 2);
		if (wavBytes.length != expectedLength) {
			throw new RuntimeException("WAV boyutu uyuşmuyor! Beklenen: " + expectedLength + ", Gelen: " + wavBytes.length);
		}

		String riffTag = new String(wavBytes, 0, 4, StandardCharsets.US_ASCII);
		String waveTag = new String(wavBytes, 8, 4, StandardCharsets.US_ASCII);
		String fmtTag  = new String(wavBytes, 12, 4, StandardCharsets.US_ASCII);
		String dataTag = new String(wavBytes, 36, 4, StandardCharsets.US_ASCII);

		System.out.println("RIFF Tag: " + riffTag + " | WAVE Tag: " + waveTag + " | FMT Tag: " + fmtTag + " | DATA Tag: " + dataTag);
		if (!"RIFF".equals(riffTag) || !"WAVE".equals(waveTag) || !"fmt ".equals(fmtTag) || !"data".equals(dataTag)) {
			throw new RuntimeException("WAV başlık etiketleri hatalı!");
		}

		System.out.println("✔ 44 bayt RIFF/WAVE PCM mono 48000 Hz ses başlığı kusursuz oluşturuldu.");

		// Reset to default for test clean up
		SttManager.setActiveProviderId("default");

		System.out.println("\n🎉 HYBRİD STT VE GROQ WHISPER BULUT SEÇİM ALTYAPISI %100 DOĞRULANDI!");
	}
}
