package com.aicaddy.ai.provider;

import com.aicaddy.ai.config.ConfigManager;
import com.aicaddy.ai.resilience.CannedFallbackProvider;
import com.aicaddy.ai.resilience.CircuitBreaker;
import com.aicaddy.ExampleMod;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise ProviderRouter with automatic CircuitBreaker failover chain:
 * Primary: Gemini -> Secondary: Groq -> Tertiary: CannedFallbackProvider.
 */
public final class ProviderRouter {

    private static final Map<String, CircuitBreaker> BREAKERS = new ConcurrentHashMap<>();

    private static volatile AiProvider geminiProvider = null;
    private static volatile AiProvider groqProvider = null;
    private static volatile AiProvider cannedProvider = null;

    private static volatile String lastProviderId = "";
    private static volatile String lastBackupId = "";
    private static volatile long lastConfigCheckMs = 0;
    private static final long CONFIG_CHECK_INTERVAL_MS = 5000;

    private ProviderRouter() {}

    static {
        BREAKERS.put("gemini", new CircuitBreaker("Gemini", 3, 60_000L));
        BREAKERS.put("groq", new CircuitBreaker("Groq", 3, 60_000L));
    }

    public static CircuitBreaker getBreaker(String providerId) {
        if (providerId == null) return null;
        return BREAKERS.computeIfAbsent(providerId.toLowerCase(), id -> new CircuitBreaker(id, 3, 60_000L));
    }

    /**
     * Returns the active AiProvider based on ConfigManager unified ai_caddy_config.json:
     * activeAiProvider (primary) -> backupAiProvider (backup) -> CannedFallback (safe mode).
     */
    public static synchronized AiProvider getProvider() {
        long now = System.currentTimeMillis();
        if (now - lastConfigCheckMs > CONFIG_CHECK_INTERVAL_MS) {
            String currentConfigId = ConfigManager.getActiveAiProvider();
            if (currentConfigId == null || currentConfigId.isEmpty()) {
                currentConfigId = "gemini";
            }
            if (!currentConfigId.equalsIgnoreCase(lastProviderId)) {
                lastProviderId = currentConfigId;
            }
            String currentBackupId = ConfigManager.getBackupAiProvider();
            if (currentBackupId == null || currentBackupId.isEmpty()) {
                currentBackupId = "groq";
            }
            if (!currentBackupId.equalsIgnoreCase(lastBackupId)) {
                lastBackupId = currentBackupId;
            }
            lastConfigCheckMs = now;
        }

        AiProvider primary = getProviderInstance(lastProviderId);
        if (primary != null) {
            return primary;
        }

        AiProvider backup = getProviderInstance(lastBackupId);
        if (backup != null && !lastBackupId.equalsIgnoreCase(lastProviderId)) {
            ExampleMod.LOGGER.info("🔄 [ProviderRouter] Primary provider '" + lastProviderId + "' is OPEN/unhealthy. Failing over to '" + lastBackupId + "'.");
            return backup;
        }

        // Failover to CannedFallback safe mode
        ExampleMod.LOGGER.warn("⚠️ [ProviderRouter] All online AI providers are OPEN/unhealthy. Falling back to CannedFallbackProvider.");
        if (cannedProvider == null) {
            cannedProvider = new CannedAiProviderWrapper();
        }
        return cannedProvider;
    }

    private static AiProvider getProviderInstance(String id) {
        if (id == null) return null;
        CircuitBreaker breaker = getBreaker(id);
        if (breaker != null && breaker.isHealthy()) {
            if ("groq".equalsIgnoreCase(id)) {
                if (groqProvider == null) {
                    groqProvider = createProviderSafe("groq");
                }
                return groqProvider;
            } else {
                if (geminiProvider == null) {
                    geminiProvider = new GeminiAiProvider();
                }
                return geminiProvider;
            }
        }
        return null;
    }

    public static boolean isProviderHealthy(String providerId) {
        CircuitBreaker breaker = getBreaker(providerId);
        return breaker != null && breaker.isHealthy();
    }

    public static synchronized void forceProvider(String id) {
        lastProviderId = id;
        geminiProvider = createProviderSafe(id);
        lastConfigCheckMs = System.currentTimeMillis();
    }

    public static synchronized void reset() {
        geminiProvider = null;
        groqProvider = null;
        cannedProvider = null;
        lastProviderId = "";
        lastBackupId = "";
        lastConfigCheckMs = 0;
        BREAKERS.values().forEach(CircuitBreaker::reset);
    }

    private static AiProvider createProviderSafe(String id) {
        return switch (id.toLowerCase()) {
            case "gemini" -> new GeminiAiProvider();
            case "groq" -> {
                try {
                    yield (AiProvider) Class.forName("com.aicaddy.ai.provider.GroqAiProvider").getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    yield new GeminiAiProvider();
                }
            }
            default -> new GeminiAiProvider();
        };
    }

    private static class CannedAiProviderWrapper implements AiProvider {
        @Override
        public String getId() {
            return "canned-fallback";
        }

        @Override
        public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
            String fallback = CannedFallbackProvider.getCannedFallback();
            return CompletableFuture.completedFuture(fallback);
        }
    }
}
