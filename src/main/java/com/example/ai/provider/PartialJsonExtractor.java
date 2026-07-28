package com.example.ai.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts "final_replik" from partial/incomplete JSON streams arriving from LLM SSE chunks
 * and splits completed sentences on the fly for low-latency streaming TTS.
 */
public class PartialJsonExtractor {

	// Regex to match "final_replik" value whether closing quote is present or not
	private static final Pattern REPLIK_PATTERN = Pattern.compile(
			"\"final_replik\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)"
	);

	/**
	 * Extracts whatever text is currently inside "final_replik": "..." from a partial JSON string.
	 * Handles escaped quotes and unescaped trailing characters.
	 */
	public static String extractPartialReplik(String partialJson) {
		if (partialJson == null || partialJson.isEmpty()) return "";

		Matcher matcher = REPLIK_PATTERN.matcher(partialJson);
		if (matcher.find()) {
			String raw = matcher.group(1);
			return unescapeJsonString(raw);
		}
		return "";
	}

	/**
	 * Given the accumulated text of final_replik so far, returns a list of completed sentences.
	 * A sentence is considered completed if it ends with '.', '!', or '?' followed by whitespace.
	 */
	public static List<String> extractCompletedSentences(String currentText) {
		List<String> sentences = new ArrayList<>();
		if (currentText == null || currentText.isBlank()) return sentences;

		// We split by standard sentence endings followed by whitespace
		int lastCut = 0;
		for (int i = 0; i < currentText.length() - 1; i++) {
			char c = currentText.charAt(i);
			char next = currentText.charAt(i + 1);
			if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(next)) {
				String s = currentText.substring(lastCut, i + 1).trim();
				if (!s.isEmpty()) {
					sentences.add(s);
				}
				lastCut = i + 1;
			}
		}
		return sentences;
	}

	private static String unescapeJsonString(String str) {
		return str.replace("\\\"", "\"")
				.replace("\\\\", "\\")
				.replace("\\n", " ")
				.replace("\\t", " ")
				.replace("\\r", " ");
	}
}
