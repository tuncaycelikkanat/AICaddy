package com.aicaddy.ai.session;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.memory.PlayerMemoryStore;
import com.aicaddy.ai.mood.CompanionMoodVector;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central per-player session manager.
 *
 * <p>Replaces all global static state previously scattered across:
 * <ul>
 *   <li>AiBrainManager.PLAYER_HISTORIES (chat history)</li>
 *   <li>CompanionMoodEngine.currentVector (mood — now per-player)</li>
 *   <li>TtsManager.TTS_EXECUTOR (single thread — now per-player)</li>
 * </ul>
 *
 * <p>Thread-safe: each session is accessed by UUID; sessions themselves
 * use atomic references and concurrent collections.
 */
public final class PlayerSessionManager {

    private PlayerSessionManager() {}

    // ─── Session Container ──────────────────────────────────────────────────

    public static final class PlayerSession {

        public final UUID uuid;
        public final String playerName;

        // ── Mood State (per-player, replaces global CompanionMoodEngine) ──
        public final AtomicReference<CompanionMoodVector> moodVector =
                new AtomicReference<>(CompanionMoodVector.defaultHappy());

        // ── Short-term Conversation History (max 5 turns) ─────────────────
        private final List<ChatTurn> chatHistory = new ArrayList<>();
        private static final int MAX_HISTORY = 5;

        // ── Persistent Memory Cache (loaded once on first access) ─────────
        private volatile boolean memoryCacheLoaded = false;
        private volatile int cachedAffinityScore = 0;
        private volatile List<String> cachedMilestones = new ArrayList<>();
        private volatile List<String> cachedRecentEvents = new ArrayList<>();

        // ── Per-Player TTS Executor ───────────────────────────────────────
        // Single-threaded FIFO per player → no cross-player audio blocking
        // (initialized in constructor to allow uuid reference)
        public final ExecutorService ttsExecutor;

        // ── Debounce & Timing ─────────────────────────────────────────────
        public volatile long lastRequestTimeMs = 0;
        public volatile long lastSpeechTimeMs = System.currentTimeMillis();

        // ── Emotional Memory (last 5 events — per-player) ─────────────────
        private final Deque<String> emotionalMemory = new ConcurrentLinkedDeque<>();
        private static final int MAX_EMOTIONAL_MEMORY = 5;

        // ── Proactive Speech Cooldowns ────────────────────────────────────
        private final ConcurrentHashMap<String, Long> proactiveCooldowns = new ConcurrentHashMap<>();
        private static final long PROACTIVE_COOLDOWN_MS = 30_000L;

        // ── Anti-Repetition: Opening Phrase Tracking ──────────────────────
        private final ConcurrentHashMap<String, Deque<String>> recentOpeningPhrasesByMood =
                new ConcurrentHashMap<>();

        private PlayerSession(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
            final String shortId = uuid.toString().substring(0, 8);
            this.ttsExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AiCaddy-TTS-" + shortId);
                t.setDaemon(true);
                return t;
            });
        }

        // ─── Chat History ────────────────────────────────────────────────

        public synchronized void addChatTurn(String userMessage, String aiResponse) {
            chatHistory.add(new ChatTurn(userMessage, aiResponse));
            while (chatHistory.size() > MAX_HISTORY) {
                chatHistory.remove(0);
            }
        }

        public synchronized List<ChatTurn> getChatHistorySnapshot() {
            return new ArrayList<>(chatHistory);
        }

        public synchronized void clearChatHistory() {
            chatHistory.clear();
        }

        // ─── Persistent Memory Cache ─────────────────────────────────────

        /**
         * Loads memory from SQLite once, then serves from in-memory cache.
         * Subsequent calls return instantly.
         */
        public void ensureMemoryCacheLoaded() {
            if (memoryCacheLoaded) return;
            synchronized (this) {
                if (memoryCacheLoaded) return;
                cachedAffinityScore = PlayerMemoryStore.getAffinityScore(uuid);
                cachedMilestones = PlayerMemoryStore.getMajorMilestones(uuid);
                cachedRecentEvents = PlayerMemoryStore.getRecentEvents(uuid);
                memoryCacheLoaded = true;
            }
        }

        public int getAffinityScore() {
            ensureMemoryCacheLoaded();
            return cachedAffinityScore;
        }

        public List<String> getMilestones() {
            ensureMemoryCacheLoaded();
            return Collections.unmodifiableList(cachedMilestones);
        }

        public List<String> getRecentEvents() {
            ensureMemoryCacheLoaded();
            return Collections.unmodifiableList(cachedRecentEvents);
        }

        /** Called after SQLite write to keep cache in sync. */
        public void onAffinityChanged(int newScore) {
            cachedAffinityScore = newScore;
        }

        /** Called after SQLite write to keep cache in sync. */
        public void onMilestoneAdded(String milestone) {
            if (!cachedMilestones.contains(milestone)) {
                List<String> updated = new ArrayList<>(cachedMilestones);
                updated.add(milestone);
                while (updated.size() > 10) updated.remove(0);
                cachedMilestones = Collections.unmodifiableList(updated);
            }
        }

        /** Called after SQLite write to keep cache in sync. */
        public void onEventAppended(String event) {
            List<String> updated = new ArrayList<>(cachedRecentEvents);
            if (!updated.isEmpty() && updated.get(updated.size() - 1).equals(event)) return;
            updated.add(event);
            while (updated.size() > 5) updated.remove(0);
            cachedRecentEvents = Collections.unmodifiableList(updated);
        }

        /** Invalidates cache so next call reloads from SQLite. */
        public synchronized void invalidateMemoryCache() {
            memoryCacheLoaded = false;
        }

        // ─── Emotional Memory (short-term) ───────────────────────────────

        public void recordEmotionalEvent(String description) {
            if (emotionalMemory.size() >= MAX_EMOTIONAL_MEMORY) {
                emotionalMemory.pollFirst();
            }
            emotionalMemory.addLast(description);
        }

        public List<String> getEmotionalMemorySnapshot() {
            return new ArrayList<>(emotionalMemory);
        }

        // ─── Proactive Speech Cooldown ────────────────────────────────────

        public boolean canSpeakProactively(String eventKey) {
            long now = System.currentTimeMillis();
            Long last = proactiveCooldowns.get(eventKey);
            if (last == null || now - last >= PROACTIVE_COOLDOWN_MS) {
                proactiveCooldowns.put(eventKey, now);
                return true;
            }
            return false;
        }

        public void resetProactiveCooldowns() {
            proactiveCooldowns.clear();
        }

        // ─── Anti-Repetition Phrases ──────────────────────────────────────

        public void recordOpeningPhrase(String reply, String moodLabel) {
            if (reply == null || reply.isBlank()) return;
            String[] words = reply.trim().split("\\s+");
            if (words.length == 0) return;
            String firstWord = words[0].replaceAll("[^a-zA-ZçÇğĞıIİöÖşŞüÜ]", "");
            if (words.length > 1 && (firstWord.equalsIgnoreCase("Vay")
                    || firstWord.equalsIgnoreCase("Aman")
                    || firstWord.equalsIgnoreCase("Yine"))) {
                firstWord = firstWord + " " + words[1].replaceAll("[^a-zA-ZçÇğĞıIİöÖşŞüÜ]", "");
            }
            if (!firstWord.isEmpty()) {
                String key = (moodLabel != null && !moodLabel.isEmpty()) ? moodLabel : "DEFAULT";
                Deque<String> queue = recentOpeningPhrasesByMood.computeIfAbsent(
                        key, k -> new ConcurrentLinkedDeque<>());
                queue.add(firstWord);
                while (queue.size() > 8) queue.pollFirst();
            }
        }

        public java.util.Collection<String> getRecentOpeningPhrases(String moodLabel) {
            String key = (moodLabel != null && !moodLabel.isEmpty()) ? moodLabel : "DEFAULT";
            Deque<String> q = recentOpeningPhrasesByMood.get(key);
            return q != null ? new ArrayList<>(q) : Collections.emptyList();
        }

        // ─── Cleanup ─────────────────────────────────────────────────────

        public void shutdown() {
            ttsExecutor.shutdownNow();
        }
    }

    // ─── Chat Turn ───────────────────────────────────────────────────────────

    public record ChatTurn(String userMessage, String aiResponse) {}

    // ─── Session Registry ────────────────────────────────────────────────────

    private static final ConcurrentHashMap<UUID, PlayerSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * Returns or creates a session for the given player.
     * Call this from any component that needs per-player state.
     */
    public static PlayerSession getSession(UUID uuid, String playerName) {
        return SESSIONS.computeIfAbsent(uuid, id -> {
            ExampleMod.LOGGER.info("🤝 [PlayerSessionManager] Yeni oturum oluşturuldu: {} ({})",
                    playerName, id.toString().substring(0, 8));
            return new PlayerSession(id, playerName != null ? playerName : id.toString());
        });
    }

    /** Convenience overload using ServerPlayer. */
    public static PlayerSession getSession(ServerPlayer player) {
        return getSession(player.getUUID(), player.getScoreboardName());
    }

    /** Returns session if it exists, null otherwise (no creation). */
    public static PlayerSession getSessionIfExists(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    /** Clears session data for a player (e.g., on disconnect or /unut). */
    public static void removeSession(UUID uuid) {
        PlayerSession session = SESSIONS.remove(uuid);
        if (session != null) {
            session.shutdown();
            com.aicaddy.ai.memory.SemanticEpisodeMemory.clear(uuid);
            com.aicaddy.ai.memory.MemoryConsolidator.clear(uuid);
            ExampleMod.LOGGER.info("🤝 [PlayerSessionManager] Oturum kaldırıldı: {}",
                    uuid.toString().substring(0, 8));
        }
    }

    /** Returns all active session UUIDs. */
    public static java.util.Set<UUID> getActiveSessions() {
        return Collections.unmodifiableSet(SESSIONS.keySet());
    }

    /**
     * Called on server shutdown — gracefully shuts down all per-player TTS executors.
     */
    public static void shutdownAll() {
        ExampleMod.LOGGER.info("🤝 [PlayerSessionManager] Tüm oturumlar kapatılıyor ({} oyuncu)...",
                SESSIONS.size());
        for (PlayerSession session : SESSIONS.values()) {
            session.shutdown();
        }
        SESSIONS.clear();
    }
}
