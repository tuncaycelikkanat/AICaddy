package com.aicaddy.ai.config;

import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigManager {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private ConfigManager() {}

    private static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    private static String readConfigValue(String section, String key, String fallbackFile, String defaultValue) {
        String cacheKey = section + "." + key;
        String cached = CACHE.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        Path[] jsonPaths = {
                getConfigDir().resolve("ai_caddy_config.json"),
                Path.of("config", "ai_caddy_config.json"),
                Path.of("../config", "ai_caddy_config.json")
        };

        for (Path p : jsonPaths) {
            if (Files.exists(p)) {
                try {
                    String jsonText = Files.readString(p).trim();
                    JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
                    if (root.has(section) && root.get(section).isJsonObject()) {
                        JsonObject sectionObj = root.getAsJsonObject(section);
                        if (sectionObj.has(key) && !sectionObj.get(key).isJsonNull()) {
                            String val = sectionObj.get(key).getAsString().trim();
                            if (!val.isEmpty()) {
                                CACHE.put(cacheKey, val);
                                return val;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore JSON parsing errors and fall back to text file
                }
            }
        }

        return readFileWithFallback(fallbackFile, defaultValue);
    }

    private static String readFileWithFallback(String filename, String defaultValue) {
        String cached = CACHE.get(filename);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        Path[] fallbacks = {
                getConfigDir().resolve(filename),
                Path.of("config", filename),
                Path.of("../config", filename)
        };

        for (Path p : fallbacks) {
            if (Files.exists(p)) {
                try {
                    String content = Files.readString(p).trim();
                    if (!content.isEmpty()) {
                        CACHE.put(filename, content);
                        return content;
                    }
                } catch (IOException e) {
                    // Ignore and try next
                }
            }
        }

        if (defaultValue != null && !defaultValue.isEmpty()) {
            CACHE.put(filename, defaultValue);
        }
        return defaultValue;
    }

    public static String getGroqApiKey() {
        return readConfigValue("apiKeys", "groqApiKey", "groq_api_key.txt", "");
    }

    public static String getGeminiApiKey() {
        return readConfigValue("apiKeys", "geminiApiKey", "gemini_api_key.txt", "");
    }

    public static String getElevenLabsApiKey() {
        return readConfigValue("apiKeys", "elevenLabsApiKey", "elevenlabs_api_key.txt", "");
    }

    public static String getActiveAiProvider() {
        return readConfigValue("provider", "activeAiProvider", "ai_companion_provider.txt", "gemini");
    }

    public static String getBackupAiProvider() {
        return readConfigValue("provider", "backupAiProvider", "backup_provider.txt", "groq");
    }

    public static String getActiveSttProvider() {
        return readConfigValue("provider", "activeSttProvider", "stt_provider.txt", "default");
    }

    public static String getEdgeTtsBinPath() {
        String defaultPath = System.getProperty("user.home") + "/.venvs/tts/bin/edge-tts";
        if (!Files.exists(Path.of(defaultPath))) {
            defaultPath = "edge-tts";
        }
        return readConfigValue("paths", "edgeTtsBinPath", "edge_tts_bin.txt", defaultPath);
    }

    public static String getMpvBinPath() {
        return readConfigValue("paths", "mpvBinPath", "mpv_bin.txt", "/usr/bin/mpv");
    }

    public static void saveActiveSttProvider(String id) {
        Path fabricConfig = getConfigDir().resolve("stt_provider.txt");
        try {
            Files.writeString(fabricConfig, id);
            CACHE.put("stt_provider.txt", id);
        } catch (IOException e) {
            // Ignore or log
        }
    }

    public static void saveActiveAiProvider(String id) {
        Path fabricConfig = getConfigDir().resolve("ai_companion_provider.txt");
        try {
            Files.writeString(fabricConfig, id);
            CACHE.put("provider.activeAiProvider", id);
            CACHE.put("ai_companion_provider.txt", id);
        } catch (IOException e) {
            // Ignore or log
        }
    }

    public static void reload() {
        CACHE.clear();
    }
}
