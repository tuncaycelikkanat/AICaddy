package com.example.ai.memory;

import com.example.ExampleMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQLite-backed persistent memory store for AI Caddy.
 * Stores player notable events across server restarts.
 */
public class PlayerMemoryStore {

	private static final String DB_PATH = "config/ai_caddy_memory.db";
	private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

	public static void init() {
		try {
			File configDir = new File("config");
			if (!configDir.exists()) {
				configDir.mkdirs();
			}

			try (Connection conn = DriverManager.getConnection(JDBC_URL);
				 Statement stmt = conn.createStatement()) {
				String sql = "CREATE TABLE IF NOT EXISTS player_memory (" +
						"uuid TEXT PRIMARY KEY," +
						"player_name TEXT," +
						"favorite_biome TEXT," +
						"last_seen_at INTEGER," +
						"notable_events TEXT" +
						");";
				stmt.execute(sql);
				ExampleMod.LOGGER.info("✔ AI Caddy SQLite persistent memory database initialized.");
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Failed to initialize PlayerMemoryStore DB: {}", e.getMessage());
		}
	}

	/**
	 * Asynchronously appends a notable event to the player's persistent memory (keeps max 10 events).
	 */
	public static void appendEventAsync(UUID playerUuid, String playerName, String eventText) {
		if (playerUuid == null || eventText == null || eventText.isBlank()) return;
		CompletableFuture.runAsync(() -> {
			try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
				String selectSql = "SELECT notable_events FROM player_memory WHERE uuid = ?";
				JsonArray eventsArray = new JsonArray();
				try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
					selectStmt.setString(1, playerUuid.toString());
					try (ResultSet rs = selectStmt.executeQuery()) {
						if (rs.next()) {
							String existingJson = rs.getString("notable_events");
							if (existingJson != null && !existingJson.isEmpty()) {
								try {
									eventsArray = JsonParser.parseString(existingJson).getAsJsonArray();
								} catch (Exception ignored) {}
							}
						}
					}
				}

				eventsArray.add(eventText);
				while (eventsArray.size() > 10) {
					eventsArray.remove(0);
				}

				String upsertSql = "INSERT INTO player_memory (uuid, player_name, last_seen_at, notable_events) " +
						"VALUES (?, ?, ?, ?) " +
						"ON CONFLICT(uuid) DO UPDATE SET " +
						"player_name = excluded.player_name, " +
						"last_seen_at = excluded.last_seen_at, " +
						"notable_events = excluded.notable_events;";

				try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
					upsertStmt.setString(1, playerUuid.toString());
					upsertStmt.setString(2, playerName != null ? playerName : "Player");
					upsertStmt.setLong(3, System.currentTimeMillis());
					upsertStmt.setString(4, eventsArray.toString());
					upsertStmt.executeUpdate();
				}
			} catch (Exception e) {
				ExampleMod.LOGGER.warn("Failed to save player event to SQLite: {}", e.getMessage());
			}
		});
	}

	/**
	 * Synchronously loads the recent notable events for the given player UUID.
	 */
	public static List<String> getRecentEvents(UUID playerUuid) {
		List<String> events = new ArrayList<>();
		if (playerUuid == null) return events;

		try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
			String sql = "SELECT notable_events FROM player_memory WHERE uuid = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, playerUuid.toString());
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						String json = rs.getString("notable_events");
						if (json != null && !json.isEmpty()) {
							JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
							for (JsonElement el : arr) {
								events.add(el.getAsString());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("Failed to load player events from SQLite: {}", e.getMessage());
		}
		return events;
	}
}
