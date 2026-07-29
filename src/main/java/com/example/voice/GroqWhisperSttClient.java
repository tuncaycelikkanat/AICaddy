package com.example.voice;

import com.example.ExampleMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GroqWhisperSttClient {

	private static final String GROQ_STT_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
	private static final String MODEL_NAME = "whisper-large-v3-turbo";
	private static final int SAMPLE_RATE = 48000;
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private static String apiKey = null;

	private static synchronized String getApiKey() {
		if (apiKey != null && !apiKey.isEmpty()) {
			return apiKey;
		}
		String[] possiblePaths = {
				"config/groq_api_key.txt",
				"../config/groq_api_key.txt",
				"/home/tuncay/Projects/mc/config/groq_api_key.txt"
		};
		for (String path : possiblePaths) {
			File file = new File(path);
			if (file.exists()) {
				try {
					apiKey = Files.readString(file.toPath()).trim();
					if (!apiKey.isEmpty()) {
						return apiKey;
					}
				} catch (IOException e) {
					ExampleMod.LOGGER.error("Groq API anahtarı okunamadı: " + path, e);
				}
			}
		}
		return null;
	}

	/**
	 * Transcribes PCM audio samples (48000 Hz, 16-bit Mono) to text using Groq Cloud Whisper API.
	 */
	public static CompletableFuture<String> transcribeAsync(short[] pcmSamples) {
		String key = getApiKey();
		if (key == null || key.isEmpty()) {
			ExampleMod.LOGGER.warn("Groq API anahtarı eksik. config/groq_api_key.txt dosyasına ekle.");
			return CompletableFuture.completedFuture("");
		}

		if (pcmSamples == null || pcmSamples.length < (SAMPLE_RATE / 4)) {
			// Ignore very short audio clips (< 250 ms) as noise
			return CompletableFuture.completedFuture("");
		}

		byte[] wavBytes = createWavBytes(pcmSamples, SAMPLE_RATE);
		String boundary = "----GroqWhisperBoundary" + System.currentTimeMillis();

		byte[] bodyBytes;
		try {
			bodyBytes = buildMultipartBody(boundary, wavBytes);
		} catch (IOException e) {
			ExampleMod.LOGGER.error("WAV ses paketi oluşturulurken hata:", e);
			return CompletableFuture.completedFuture("");
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GROQ_STT_URL))
				.timeout(Duration.ofSeconds(8))
				.header("Authorization", "Bearer " + key)
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
				.build();

		long startTime = System.currentTimeMillis();
		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					long latency = System.currentTimeMillis() - startTime;
					if (response.statusCode() != 200) {
						ExampleMod.LOGGER.error("Groq Whisper API hata kodu {}: {}", response.statusCode(), response.body());
						return "";
					}
					try {
						JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
						if (json.has("text")) {
							String text = json.get("text").getAsString().trim();
							ExampleMod.LOGGER.info("✔ Groq Whisper bulut tanıma başarılı ({} ms): \"{}\"", latency, text);
							return text;
						}
					} catch (Exception e) {
						ExampleMod.LOGGER.error("Groq Whisper API yanıtı çözümlenemedi:", e);
					}
					return "";
				})
				.exceptionally(ex -> {
					ExampleMod.LOGGER.error("Groq Whisper API bağlantı hatası:", ex);
					return "";
				});
	}

	private static byte[] buildMultipartBody(String boundary, byte[] wavBytes) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		writeFormField(bos, boundary, "model", MODEL_NAME);
		writeFormField(bos, boundary, "language", "tr");
		writeFormField(bos, boundary, "response_format", "json");
		writeFormField(bos, boundary, "temperature", "0.0");
		writeFileField(bos, boundary, "file", "audio.wav", "audio/wav", wavBytes);
		bos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return bos.toByteArray();
	}

	private static void writeFormField(ByteArrayOutputStream bos, String boundary, String name, String value) throws IOException {
		bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
		bos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		bos.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
	}

	private static void writeFileField(ByteArrayOutputStream bos, String boundary, String name, String filename, String contentType, byte[] data) throws IOException {
		bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
		bos.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
		bos.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		bos.write(data);
		bos.write(("\r\n").getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Creates a standard 44-byte RIFF/WAVE header + PCM sample bytes.
	 */
	public static byte[] createWavBytes(short[] pcmSamples, int sampleRate) {
		int pcmLen = pcmSamples.length * 2;
		byte[] wav = new byte[44 + pcmLen];
		// RIFF chunk descriptor
		wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
		writeIntLe(wav, 4, 36 + pcmLen);
		wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
		// fmt sub-chunk
		wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
		writeIntLe(wav, 16, 16); // Subchunk1Size
		writeShortLe(wav, 20, (short) 1); // AudioFormat (1 = PCM)
		writeShortLe(wav, 22, (short) 1); // NumChannels (1 = Mono)
		writeIntLe(wav, 24, sampleRate); // SampleRate
		writeIntLe(wav, 28, sampleRate * 2); // ByteRate
		writeShortLe(wav, 32, (short) 2); // BlockAlign
		writeShortLe(wav, 34, (short) 16); // BitsPerSample
		// data sub-chunk
		wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
		writeIntLe(wav, 40, pcmLen);

		int idx = 44;
		for (short sample : pcmSamples) {
			wav[idx++] = (byte) (sample & 0xFF);
			wav[idx++] = (byte) ((sample >> 8) & 0xFF);
		}
		return wav;
	}

	private static void writeIntLe(byte[] buf, int pos, int val) {
		buf[pos]     = (byte) (val & 0xFF);
		buf[pos + 1] = (byte) ((val >> 8) & 0xFF);
		buf[pos + 2] = (byte) ((val >> 16) & 0xFF);
		buf[pos + 3] = (byte) ((val >> 24) & 0xFF);
	}

	private static void writeShortLe(byte[] buf, int pos, short val) {
		buf[pos]     = (byte) (val & 0xFF);
		buf[pos + 1] = (byte) ((val >> 8) & 0xFF);
	}
}
