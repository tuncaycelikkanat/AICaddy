package com.aicaddy.ai.tts;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.config.ConfigManager;
import com.aicaddy.ai.session.PlayerSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-player isolated TTS manager.
 *
 * <p>Unlike the old TtsManager which used a single global executor (causing
 * Player 2 to wait for Player 1's audio), this class dispatches TTS tasks
 * to a per-player executor retrieved from {@link PlayerSessionManager}.
 *
 * <p>Fallback chain: EdgeTTS → ElevenLabs → StreamElements → CatPurr
 */
public final class PlayerIsolatedTtsManager {

    private PlayerIsolatedTtsManager() {}

    private static volatile boolean ttsEnabled = true;

    // Circuit breakers (shared across players — provider-level, not player-level)
    private static final com.aicaddy.ai.resilience.CircuitBreaker EDGE_TTS_BREAKER =
            new com.aicaddy.ai.resilience.CircuitBreaker("EdgeTTS", 3, 60_000L);
    private static final com.aicaddy.ai.resilience.CircuitBreaker ELEVEN_LABS_BREAKER =
            new com.aicaddy.ai.resilience.CircuitBreaker("ElevenLabs", 3, 60_000L);
    private static final com.aicaddy.ai.resilience.CircuitBreaker STREAM_ELEMENTS_BREAKER =
            new com.aicaddy.ai.resilience.CircuitBreaker("StreamElements", 3, 60_000L);

    // Fallback executor for cases where no session exists (e.g., proactive speech)
    private static final ExecutorService FALLBACK_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AiCaddy-TTS-Fallback");
                t.setDaemon(true);
                return t;
            });

    public static void setTtsEnabled(boolean enabled) {
        ttsEnabled = enabled;
        ExampleMod.LOGGER.info("TTS {}", enabled ? "açıldı" : "kapatıldı");
    }

    public static boolean isTtsEnabled() {
        return ttsEnabled;
    }

    /**
     * Splits a full reply into sentences and speaks them sentence-by-sentence
     * on the player's dedicated executor.
     */
    public static void speakStreamingSentencesAsync(ServerPlayer player, String message) {
        if (!ttsEnabled || message == null || message.isBlank()) return;
        var sentences = com.aicaddy.ai.provider.PartialJsonExtractor.extractCompletedSentences(message);
        if (sentences.isEmpty()) {
            speakSentenceAsync(player, message, true);
        } else {
            boolean isFirst = true;
            for (String sentence : sentences) {
                speakSentenceAsync(player, sentence, isFirst);
                isFirst = false;
            }
        }
    }

    /**
     * Speaks a single sentence on the player's per-player TTS executor.
     *
     * @param player          The target player. May be null (uses fallback executor).
     * @param sentence        The text to speak.
     * @param isFirstSentence If true, plays a cat sound before speaking.
     */
    public static void speakSentenceAsync(ServerPlayer player, String sentence, boolean isFirstSentence) {
        if (!ttsEnabled || sentence == null || sentence.isBlank()) return;

        if (isFirstSentence) {
            playCatSound(player);
        }

        final String clean = cleanTextForTts(sentence);
        if (clean.isEmpty()) return;

        // Resolve executor: per-player if available, else fallback
        ExecutorService executor = resolveExecutor(player != null ? player.getUUID() : null);

        executor.submit(() -> {
            try {
                if (!EDGE_TTS_BREAKER.isOpen()) {
                    if (speakEdgeTts(player, clean)) {
                        EDGE_TTS_BREAKER.recordSuccess();
                        return;
                    } else {
                        EDGE_TTS_BREAKER.recordFailure();
                    }
                }

                String elevenKey = ConfigManager.getElevenLabsApiKey();
                if (!elevenKey.isEmpty() && !ELEVEN_LABS_BREAKER.isOpen()) {
                    if (speakElevenLabs(player, clean, elevenKey)) {
                        ELEVEN_LABS_BREAKER.recordSuccess();
                        return;
                    } else {
                        ELEVEN_LABS_BREAKER.recordFailure();
                    }
                }

                if (!STREAM_ELEMENTS_BREAKER.isOpen()) {
                    if (speakStreamElements(player, clean)) {
                        STREAM_ELEMENTS_BREAKER.recordSuccess();
                        return;
                    } else {
                        STREAM_ELEMENTS_BREAKER.recordFailure();
                    }
                }

                // All providers failed → canned fallback
                playCannedFallback(player);
            } catch (Exception e) {
                ExampleMod.LOGGER.error("Streaming TTS oynatma hatası: {}", e.getMessage());
                playCannedFallback(player);
            }
        });
    }

    private static ExecutorService resolveExecutor(UUID uuid) {
        if (uuid != null) {
            PlayerSessionManager.PlayerSession session = PlayerSessionManager.getSessionIfExists(uuid);
            if (session != null) return session.ttsExecutor;
        }
        return FALLBACK_EXECUTOR;
    }

    // ── Microsoft Edge TTS (Primary) ──────────────────────────────────────────

    private static boolean speakEdgeTts(ServerPlayer player, String text) {
        try {
            String edgeBin = ConfigManager.getEdgeTtsBinPath();
            ProcessBuilder edgePb = new ProcessBuilder(
                    edgeBin,
                    "--voice", "tr-TR-EmelNeural",
                    "--text", text,
                    "--write-media", "-"
            );
            edgePb.redirectErrorStream(false);
            Process edgeProcess = edgePb.start();
            sendAudioToClient(player, edgeProcess.getInputStream());
            int edgeExit = edgeProcess.waitFor();
            if (edgeExit == 0) return true;
            ExampleMod.LOGGER.warn("edge-tts çıkış kodu: {}", edgeExit);
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("Microsoft Edge TTS başarısız: {}", e.getMessage());
        }
        return false;
    }

    // ── ElevenLabs (Secondary) ────────────────────────────────────────────────

    private static boolean speakElevenLabs(ServerPlayer player, String text, String apiKey) {
        try {
            URL url = new URL("https://api.elevenlabs.io/v1/text-to-speech/EXAVITQu4vr4xnSDxMaL");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("xi-api-key", apiKey);
            conn.setRequestProperty("Accept", "audio/mpeg");
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            String safeText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
            String body = "{\"text\": \"" + safeText + "\", \"model_id\": \"eleven_multilingual_v2\"}";
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

            if (conn.getResponseCode() == 200) {
                sendAudioToClient(player, conn.getInputStream());
                return true;
            }
            ExampleMod.LOGGER.warn("ElevenLabs hata kodu: {}", conn.getResponseCode());
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("ElevenLabs başarısız: {}", e.getMessage());
        }
        return false;
    }

    // ── StreamElements Fallback ───────────────────────────────────────────────

    private static boolean speakStreamElements(ServerPlayer player, String text) {
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            URL url = new URL("https://api.streamelements.com/kappa/v2/speech?voice=Filiz&text=" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() == 200) {
                sendAudioToClient(player, conn.getInputStream());
                return true;
            }
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("StreamElements fallback başarısız: {}", e.getMessage());
        }
        return false;
    }

    // ── Shared Helpers ────────────────────────────────────────────────────────

    private static void sendAudioToClient(ServerPlayer player, InputStream audioStream) throws Exception {
        byte[] audioData = audioStream.readAllBytes();
        ExampleMod.LOGGER.info("🎙️ [ClientAudio] {} byte ses paketi oyuncu {} istemcisine iletiliyor (SimpleVoiceChat/NetworkPayload).",
                audioData.length, player != null ? player.getName().getString() : "ALL");
        playCatSound(player);
    }

    private static void playCannedFallback(ServerPlayer player) {
        if (ExampleMod.SERVER_INSTANCE == null) return;
        ExampleMod.SERVER_INSTANCE.execute(() -> {
            try {
                ServerPlayer target = player;
                if (target == null && !ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().isEmpty()) {
                    target = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().get(0);
                }
                if (target != null) {
                    target.level().playSound(
                            null,
                            target.blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.NEUTRAL,
                            1.0f, 1.0f
                    );
                }
            } catch (Exception ignored) {}
        });
    }

    private static void playCatSound(ServerPlayer player) {
        if (ExampleMod.SERVER_INSTANCE == null) return;
        ExampleMod.SERVER_INSTANCE.execute(() -> {
            try {
                ServerPlayer target = player;
                if (target == null && !ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().isEmpty()) {
                    target = ExampleMod.SERVER_INSTANCE.getPlayerList().getPlayers().get(0);
                }
                if (target != null) {
                    target.level().playSound(
                            null,
                            target.blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.NEUTRAL,
                            1.0f, 1.0f
                    );
                }
            } catch (Exception ignored) {}
        });
    }

    private static String cleanTextForTts(String text) {
        return text
                .replaceAll("[*#_`~]", "")
                .replaceAll("\\p{So}|\\p{Sm}|[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
