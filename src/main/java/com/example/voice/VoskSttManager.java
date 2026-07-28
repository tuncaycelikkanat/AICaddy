package com.example.voice;

import com.example.ExampleMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VoskSttManager {

	private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip";
	private static final String MODELS_DIR = "models";
	private static final String MODEL_DIR_NAME = "vosk-model-tr";

	private static Model model = null;
	private static Recognizer recognizer = null;
	private static boolean isReady = false;

	public static void initialize() {
		// Reduce Vosk native C++ library log spam.
		LibVosk.setLogLevel(LogLevel.WARNINGS);

		// Load or download the acoustic model asynchronously.
		CompletableFuture.runAsync(() -> {
			try {
				File modelDir = ensureModelDownloaded();
				ExampleMod.LOGGER.info("Loading Vosk Turkish model from: " + modelDir.getAbsolutePath());
				model = new Model(modelDir.getAbsolutePath());
				// Simple Voice Chat uses 48000 Hz sample rate by default.
				recognizer = new Recognizer(model, 48000.0f);
				isReady = true;
				ExampleMod.LOGGER.info("✔ Vosk Turkish STT Model initialized successfully.");
			} catch (Exception e) {
				ExampleMod.LOGGER.error("Failed to initialize Vosk STT model.", e);
			}
		});
	}

	public static boolean isReady() {
		return isReady && recognizer != null;
	}

	/**
	 * Transcribes raw 16-bit PCM audio samples into text.
	 */
	public static synchronized String transcribe(short[] pcmData) {
		if (!isReady()) {
			return "";
		}
		try {
			boolean isFinal = recognizer.acceptWaveForm(pcmData, pcmData.length);
			String jsonResult = isFinal ? recognizer.getResult() : recognizer.getPartialResult();

			JsonObject jsonObject = JsonParser.parseString(jsonResult).getAsJsonObject();
			if (jsonObject.has("text")) {
				return jsonObject.get("text").getAsString().trim();
			} else if (jsonObject.has("partial")) {
				return jsonObject.get("partial").getAsString().trim();
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Error transcribing PCM audio:", e);
		}
		return "";
	}

	/**
	 * Downloads and extracts the Vosk acoustic model if not present.
	 */
	private static File ensureModelDownloaded() throws IOException {
		File modelsDirFile = new File(MODELS_DIR);
		if (!modelsDirFile.exists()) {
			modelsDirFile.mkdirs();
		}
		File targetDir = new File(modelsDirFile, MODEL_DIR_NAME);
		if (targetDir.exists() && targetDir.isDirectory() && targetDir.list() != null && targetDir.list().length > 0) {
			return targetDir;
		}

		ExampleMod.LOGGER.info("Vosk Turkish model not found. Downloading 45 MB model archive...");
		File zipFile = new File(modelsDirFile, "vosk-model-tr.zip");

		// Download ZIP archive.
		try (InputStream in = new URL(MODEL_URL).openStream();
			 FileOutputStream out = new FileOutputStream(zipFile)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}

		// Extract ZIP archive.
		ExampleMod.LOGGER.info("Extracting Vosk model archive...");
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				File newFile = new File(modelsDirFile, entry.getName());
				if (entry.isDirectory()) {
					newFile.mkdirs();
				} else {
					newFile.getParentFile().mkdirs();
					try (FileOutputStream fos = new FileOutputStream(newFile)) {
						byte[] buf = new byte[8192];
						int len;
						while ((len = zis.read(buf)) > 0) {
							fos.write(buf, 0, len);
						}
					}
				}
				zis.closeEntry();
			}
		}

		// Rename extracted root directory to target directory name.
		File extractedDir = new File(modelsDirFile, "vosk-model-small-tr-0.3");
		if (extractedDir.exists()) {
			extractedDir.renameTo(targetDir);
		}
		if (zipFile.exists()) {
			zipFile.delete();
		}

		return targetDir;
	}
}
