package com.aicaddy.ai;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.context.SmartContextBuilder;
import com.aicaddy.ai.mood.CompanionMoodEngine;
import com.aicaddy.ai.mood.CompanionMoodState;
import com.aicaddy.ai.mood.EmotionalEventDetector;
import com.aicaddy.ai.prompt.SharedPromptRules;
import com.aicaddy.ai.provider.AiProvider;
import com.aicaddy.ai.provider.ProviderRouter;
import com.aicaddy.ai.session.PlayerSessionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI Brain Manager — v2 pipeline.
 *
 * <p>All per-player state (history, mood, TTS) is now managed via
 * {@link PlayerSessionManager}. No more global static collections.
 *
 * <p>Provider selection is delegated to {@link ProviderRouter}.
 * Context is assembled by {@link SmartContextBuilder} (GamePhase + Threat + Biome + MC).
 */
public class AiBrainManager {

    // ─── History Management ─────────────────────────────────────────────────

    public static synchronized void addTurnToHistory(ServerPlayer player, String userMsg, String aiResp) {
        PlayerSessionManager.PlayerSession session = PlayerSessionManager.getSession(player);
        session.recordOpeningPhrase(aiResp, CompanionMoodEngine.getCurrentMood(player.getUUID()).getLabel());
        session.addChatTurn(userMsg, aiResp);
    }

    public static synchronized void clearHistory(ServerPlayer player) {
        if (player == null) {
            // Clear all sessions
            for (var uuid : PlayerSessionManager.getActiveSessions()) {
                var session = PlayerSessionManager.getSessionIfExists(uuid);
                if (session != null) session.clearChatHistory();
            }
        } else {
            PlayerSessionManager.getSession(player).clearChatHistory();
        }
        ExampleMod.LOGGER.info("✔ AI Companion chat history cleared for player: {}",
                player != null ? player.getScoreboardName() : "ALL");
    }

    public static synchronized void clearHistory() {
        clearHistory(null);
    }

    // ─── Provider Management ────────────────────────────────────────────────

    /** @deprecated Use ProviderRouter.getProvider() directly. */
    @Deprecated
    public static AiProvider getActiveProvider() {
        return ProviderRouter.getProvider();
    }

    // ─── Core Prompt Builder ────────────────────────────────────────────────

    /**
     * Builds the full companion prompt using:
     * <ul>
     *   <li>GamePhase — early / mid / late / endgame mood</li>
     *   <li>ThreatAssessor — danger score and description</li>
     *   <li>BiomeCharacterizer — emotional tone of the biome</li>
     *   <li>Minecraft context — HP, inventory, LookAt, structure</li>
     *   <li>Per-player mood (Valence-Arousal)</li>
     *   <li>Per-player memory (affinity, milestones, events from session cache)</li>
     *   <li>Per-player chat history</li>
     * </ul>
     */
    private static String buildCompanionPrompt(ServerPlayer player, String playerSpeech) {
        PlayerSessionManager.PlayerSession session = PlayerSessionManager.getSession(player);

        // ── Smart Context (GamePhase + Threat + Biome + MC Context) ──
        SmartContextBuilder.ContextPayload ctx = SmartContextBuilder.build(player);

        // ── Per-player mood ──
        String moodContext = CompanionMoodEngine.getMoodContext(player.getUUID());
        CompanionMoodState currentMood = CompanionMoodEngine.getCurrentMood(player.getUUID());

        StringBuilder sb = new StringBuilder();

        // ── Identity ──
        sb.append("Sen '").append(player.getScoreboardName()).append("' ile birlikte Minecraft oynayan, oyunun mekaniklerini yalayıp yutmuş usta bir PRO oyuncu arkadaşsın.\n");
        sb.append("Adın 'AI Arkadaş' / 'Yoldaş'. Hem arkadaşça muhabbet eden hem de gerçek Minecraft bilgisiyle mantıklı ve net konuşan tecrübeli bir yol arkadaşısın. Kedi veya hayvan gibi davranma.\n\n");

        // ── Core persona ──
        sb.append(SharedPromptRules.CORE_PERSONA_RULE).append("\n");

        // ── Smart Context block (Faz + Tehdit + Biyom + Oyun Durumu) ──
        sb.append(ctx.toPromptBlock());

        // ── Per-player mood ──
        sb.append(moodContext).append("\n");

        // ── Persistent memory (from session cache — no SQLite round-trip) ──
        String memorySummary = buildMemorySummaryFromSession(session);
        if (!memorySummary.isEmpty()) {
            sb.append(memorySummary).append("\n");
        }

        // ── Conversation history ──
        var history = session.getChatHistorySnapshot();
        if (!history.isEmpty()) {
            sb.append("[SON KONUŞMALARIMIZ]:\n");
            for (var turn : history) {
                sb.append("  ").append(player.getScoreboardName()).append(": \"").append(turn.userMessage()).append("\"\n");
                sb.append("  AI Arkadaş: \"").append(turn.aiResponse()).append("\"\n");
            }
            sb.append("\n");
        }

        // ── Response rules ──
        SharedPromptRules.PromptMode activeMode = SharedPromptRules.getActiveMode();
        sb.append("CEVAP KURALLARI (Aktif Mod: ").append(activeMode.name()).append("):\n");
        sb.append(activeMode.getResponseRules());
        if (activeMode == SharedPromptRules.PromptMode.STANDART) {
            if (currentMood == CompanionMoodState.SAD) {
                sb.append(SharedPromptRules.SAD_MOOD_RULE);
            }
            sb.append(SharedPromptRules.SPECIFICITY_RULE);
            sb.append(SharedPromptRules.buildOpeningPhraseBlacklistRule(
                    session.getRecentOpeningPhrases(currentMood.getLabel())));
        }
        sb.append("- Ruh haline göre konuş — şu an ").append(currentMood.getLabel()).append(" hissediyorsun.\n\n");

        // ── Language rule ──
        sb.append(SharedPromptRules.FINAL_LANGUAGE_RULE).append("\n");

        // ── Output format ──
        sb.append("ÇIKTI FORMATI — SADECE bu JSON:\n");
        sb.append(SharedPromptRules.buildOutputSchema(currentMood.getLabel())).append("\n");

        // ── Player speech ──
        sb.append(player.getScoreboardName()).append(" şimdi şunu dedi/yaptı: \"").append(playerSpeech).append("\"");

        return sb.toString();
    }

    private static String buildMemorySummaryFromSession(PlayerSessionManager.PlayerSession session) {
        // Load from session cache (no SQLite call)
        session.ensureMemoryCacheLoaded();
        int score = session.getAffinityScore();
        var milestones = session.getMilestones();
        var events = session.getRecentEvents();
        String tier = com.aicaddy.ai.memory.PlayerMemoryStore.getAffinityTierLabel(score);

        if (score == 0 && milestones.isEmpty() && events.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[OYUNCU HAFIZASI VE SAMİMİYET SEVİYESİ]:\n");
        sb.append("- Samimiyet Seviyesi: ").append(tier).append("\n");
        if (!milestones.isEmpty()) {
            sb.append("- Kalıcı Başarılar (Milestones): ").append(String.join(", ", milestones)).append("\n");
        }
        if (!events.isEmpty()) {
            sb.append("- Son Yaşanan Olaylar: ").append(String.join("; ", events)).append("\n");
        }
        return sb.toString();
    }

    // ─── Generate Response ──────────────────────────────────────────────────

    public static CompletableFuture<String> generateCompanionResponseAsync(ServerPlayer player, String playerSpeech) {
        // Handle clear commands
        String lower = playerSpeech.toLowerCase().trim();
        if (lower.equals("unut") || lower.equals("hafızayı temizle") || lower.equals("geçmişi temizle")) {
            clearHistory(player);
            return CompletableFuture.completedFuture("Tamam tamam, seninle ne konuşmuşsak hepsini sildim. Sıfırdan başlıyoruz!");
        }

        // Notify mood engine about player speaking (per-player)
        EmotionalEventDetector.onPlayerSpoke(player);
        CompanionMoodEngine.processPlayerSpeech(playerSpeech, player.getUUID());

        String prompt = buildCompanionPrompt(player, playerSpeech);
        com.aicaddy.ai.debug.CompanionDebugLogger.logPromptSent(player, prompt);

        AiProvider provider = ProviderRouter.getProvider();
        return provider.generateResponseAsync(player, prompt, playerSpeech).thenApply(response -> {
            // Quality checks (now active in production)
            String contextStr = SmartContextBuilder.build(player).mcContext();
            if (SharedPromptRules.containsHallucinatedNumbers(response, contextStr)) {
                ExampleMod.LOGGER.warn("⚠️ [Hallucination] Yanıtta bağlamda olmayan sayı tespit edildi: {}", response);
            }
            var recentReplies = PlayerSessionManager.getSession(player)
                    .getChatHistorySnapshot().stream()
                    .map(PlayerSessionManager.ChatTurn::aiResponse)
                    .collect(Collectors.toList());
            if (SharedPromptRules.has4GramRepetition(response, recentReplies)) {
                ExampleMod.LOGGER.warn("⚠️ [4-gram] Tekrarlayan çıktı tespit edildi: {}", response);
            }
            return response;
        });
    }

    // ─── Proactive Speech ───────────────────────────────────────────────────

    public static void triggerProactiveResponse(ServerPlayer player, String situationPrompt) {
        com.aicaddy.ai.debug.CompanionDebugLogger.logPromptSent(player, situationPrompt);
        ProviderRouter.getProvider().generateResponseAsync(player, situationPrompt, "").thenAccept(response -> {
            broadcastResponse(player, response);
        }).exceptionally(ex -> {
            ExampleMod.LOGGER.error("Proaktif konuşma başarısız.", ex);
            return null;
        });
    }

    // ─── Process and Broadcast ──────────────────────────────────────────────

    public static void processAndRespond(ServerPlayer player, String playerSpeech) {
        PlayerSessionManager.PlayerSession session = PlayerSessionManager.getSession(player);

        // Per-player debounce (replaces global lastRequestTimeMs)
        long now = System.currentTimeMillis();
        if (now - session.lastRequestTimeMs < 1000) return;
        session.lastRequestTimeMs = now;

        // Execute physical companion command if applicable (P10.3 Layer 3)
        com.aicaddy.entity.AiCompanionEntity.handleCompanionCommand(player, playerSpeech);

        long startTimeMs = System.currentTimeMillis();
        generateCompanionResponseAsync(player, playerSpeech).thenAccept(aiResponse -> {
            long latencyMs = System.currentTimeMillis() - startTimeMs;
            addTurnToHistory(player, playerSpeech, aiResponse);
            broadcastResponse(player, aiResponse);

            // P2.3 Telemetry log
            CompanionMoodState moodState = CompanionMoodEngine.getCurrentMood(player.getUUID());
            int affinity = session.getAffinityScore();
            com.aicaddy.ai.debug.CompanionTelemetryLogger.logTurnAsync(
                    player.getStringUUID(),
                    player.getScoreboardName(),
                    "CHAT",
                    ProviderRouter.getProvider().getId(),
                    latencyMs,
                    ProviderRouter.getProvider().getId().equals("groq") ? 650 : -1,
                    moodState,
                    affinity,
                    false,
                    playerSpeech,
                    aiResponse
            );
        });
    }

    private static void broadcastResponse(ServerPlayer player, String message) {
        CompanionMoodState currentMood = CompanionMoodEngine.getCurrentMood(player.getUUID());
        String badge = currentMood.getChatBadge();
        ExampleMod.LOGGER.info("🤝 [AI Arkadaş -> {} | Mood: {}]: \"{}\"",
                player.getScoreboardName(), currentMood.getLabel(), message);
        if (ExampleMod.SERVER_INSTANCE != null) {
            ExampleMod.SERVER_INSTANCE.execute(() -> {
                ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
                        Component.literal(badge + ": §f" + message),
                        false
                );
                // Update companion entity name badge and spawn emotion particles (P10.3)
                com.aicaddy.entity.AiCompanionEntity.updateMoodBadge(player, currentMood);
                com.aicaddy.entity.AiCompanionEntity.spawnEmotionParticles(player, currentMood);

                // Execute autonomous companion physical actions (P10.5 Tool Calling)
                com.aicaddy.ai.action.CompanionActionExecutor.executeQueuedActions(player);

                // TTS: Groq streams sentence-by-sentence during generation (in GroqAiProvider).
                // For non-streaming providers, speak the full reply now via per-player executor.
                if (!ProviderRouter.getProvider().getId().equals("groq")) {
                    com.aicaddy.ai.tts.PlayerIsolatedTtsManager.speakStreamingSentencesAsync(player, message);
                }
            });
        }
    }
}
