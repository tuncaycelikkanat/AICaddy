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
 * SQLite-backed persistent memory store for AI Caddy (P2.1 Enriched Memory & Affinity Engine).
 * Stores player notable events, major milestones, and affinity score across server restarts.
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
						"notable_events TEXT," +
						"major_milestones TEXT," +
						"affinity_score INTEGER DEFAULT 0" +
						");";
				stmt.execute(sql);

				// Schema migration for existing tables
				try {
					stmt.execute("ALTER TABLE player_memory ADD COLUMN major_milestones TEXT;");
				} catch (Exception ignored) {}
				try {
					stmt.execute("ALTER TABLE player_memory ADD COLUMN affinity_score INTEGER DEFAULT 0;");
				} catch (Exception ignored) {}

				ExampleMod.LOGGER.info("✔ AI Caddy SQLite persistent memory & affinity database initialized.");
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.error("Failed to initialize PlayerMemoryStore DB: {}", e.getMessage());
		}
	}

	/**
	 * Asynchronously modifies the player's affinity score (-10 to 100) and logs the change.
	 */
	public static void modifyAffinityAsync(UUID playerUuid, String playerName, int delta, String reason) {
		if (playerUuid == null || delta == 0) return;
		CompletableFuture.runAsync(() -> {
			try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
				int currentScore = 0;
				String selectSql = "SELECT affinity_score FROM player_memory WHERE uuid = ?";
				try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
					selectStmt.setString(1, playerUuid.toString());
					try (ResultSet rs = selectStmt.executeQuery()) {
						if (rs.next()) {
							currentScore = rs.getInt("affinity_score");
						}
					}
				}

				int newScore = Math.max(-10, Math.min(100, currentScore + delta));

				String upsertSql = "INSERT INTO player_memory (uuid, player_name, last_seen_at, affinity_score) " +
						"VALUES (?, ?, ?, ?) " +
						"ON CONFLICT(uuid) DO UPDATE SET " +
						"player_name = excluded.player_name, " +
						"last_seen_at = excluded.last_seen_at, " +
						"affinity_score = excluded.affinity_score;";

				try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
					upsertStmt.setString(1, playerUuid.toString());
					upsertStmt.setString(2, playerName != null ? playerName : "Player");
					upsertStmt.setLong(3, System.currentTimeMillis());
					upsertStmt.setInt(4, newScore);
					upsertStmt.executeUpdate();
				}

				ExampleMod.LOGGER.info("🐱 [{}] Samimiyet Puanı güncellendi: {} -> {} ({}) | Sebep: {}",
						playerName, currentScore, newScore, (delta >= 0 ? "+" + delta : delta), reason);
			} catch (Exception e) {
				ExampleMod.LOGGER.warn("Failed to modify affinity score in SQLite: {}", e.getMessage());
			}
		});
	}

	/**
	 * Synchronously gets the current affinity score for the player.
	 */
	public static int getAffinityScore(UUID playerUuid) {
		if (playerUuid == null) return 0;
		try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
			String sql = "SELECT affinity_score FROM player_memory WHERE uuid = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, playerUuid.toString());
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						return rs.getInt("affinity_score");
					}
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("Failed to get affinity score from SQLite: {}", e.getMessage());
		}
		return 0;
	}

	/**
	 * Maps an affinity score to an immersive Turkish tier label.
	 */
	public static String getAffinityTierLabel(int score) {
		if (score < -5) return "Kırgın / Soğuk (Puan: " + score + ")";
		if (score < 10) return "Yoldaş / Nötr (Puan: " + score + ")";
		if (score < 30) return "Can Dostu (Puan: +" + score + ")";
		if (score < 60) return "Ayrılmaz İkili (Puan: +" + score + ")";
		return "Ruh İkizi (Puan: +" + score + ")";
	}

	/**
	 * Asynchronously adds a unique permanent major milestone (up to 10 milestones) and grants +2 affinity.
	 */
	public static void addMilestoneAsync(UUID playerUuid, String playerName, String milestone) {
		if (playerUuid == null || milestone == null || milestone.isBlank()) return;
		CompletableFuture.runAsync(() -> {
			try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
				String selectSql = "SELECT major_milestones FROM player_memory WHERE uuid = ?";
				JsonArray array = new JsonArray();
				boolean alreadyExists = false;
				try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
					selectStmt.setString(1, playerUuid.toString());
					try (ResultSet rs = selectStmt.executeQuery()) {
						if (rs.next()) {
							String existingJson = rs.getString("major_milestones");
							if (existingJson != null && !existingJson.isEmpty()) {
								try {
									array = JsonParser.parseString(existingJson).getAsJsonArray();
									for (JsonElement el : array) {
										if (el.getAsString().equalsIgnoreCase(milestone)) {
											alreadyExists = true;
											break;
										}
									}
								} catch (Exception ignored) {}
							}
						}
					}
				}

				if (alreadyExists) return;

				array.add(milestone);
				while (array.size() > 10) {
					array.remove(0);
				}

				String upsertSql = "INSERT INTO player_memory (uuid, player_name, last_seen_at, major_milestones) " +
						"VALUES (?, ?, ?, ?) " +
						"ON CONFLICT(uuid) DO UPDATE SET " +
						"player_name = excluded.player_name, " +
						"last_seen_at = excluded.last_seen_at, " +
						"major_milestones = excluded.major_milestones;";

				try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
					upsertStmt.setString(1, playerUuid.toString());
					upsertStmt.setString(2, playerName != null ? playerName : "Player");
					upsertStmt.setLong(3, System.currentTimeMillis());
					upsertStmt.setString(4, array.toString());
					upsertStmt.executeUpdate();
				}

				// Also reward positive affinity for reaching a milestone
				modifyAffinityAsync(playerUuid, playerName, 2, "Başarı/Milestone kazandı: " + milestone);
			} catch (Exception e) {
				ExampleMod.LOGGER.warn("Failed to save major milestone to SQLite: {}", e.getMessage());
			}
		});
	}

	/**
	 * Synchronously loads the major milestones for the given player UUID.
	 */
	public static List<String> getMajorMilestones(UUID playerUuid) {
		List<String> milestones = new ArrayList<>();
		if (playerUuid == null) return milestones;

		try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
			String sql = "SELECT major_milestones FROM player_memory WHERE uuid = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, playerUuid.toString());
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						String json = rs.getString("major_milestones");
						if (json != null && !json.isEmpty()) {
							JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
							for (JsonElement el : arr) {
								milestones.add(el.getAsString());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			ExampleMod.LOGGER.warn("Failed to load major milestones from SQLite: {}", e.getMessage());
		}
		return milestones;
	}

	/**
	 * Asynchronously appends a notable event to the player's persistent memory (keeps max 5 events via retention policy).
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

				// Avoid immediate duplicate event spam
				if (eventsArray.size() > 0 && eventsArray.get(eventsArray.size() - 1).getAsString().equals(eventText)) {
					return;
				}

				eventsArray.add(eventText);
				// Retention policy: keep only the most recent 5 events to prevent context clutter
				while (eventsArray.size() > 5) {
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

	/**
	 * Returns a structured, summarized memory block for prompt injection.
	 */
	public static String getSummarizedMemoryPrompt(UUID playerUuid) {
		if (playerUuid == null) return "";
		int score = getAffinityScore(playerUuid);
		String tier = getAffinityTierLabel(score);
		List<String> milestones = getMajorMilestones(playerUuid);
		List<String> recentEvents = getRecentEvents(playerUuid);

		if (score == 0 && milestones.isEmpty() && recentEvents.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("[OYUNCU HAFIZASI VE SAMİMİYET SEVİYESİ]:\n");
		sb.append("- Samimiyet Seviyesi: ").append(tier).append("\n");
		if (!milestones.isEmpty()) {
			sb.append("- Kalıcı Başarılar (Milestones): ").append(String.join(", ", milestones)).append("\n");
		}
		if (!recentEvents.isEmpty()) {
			sb.append("- Son Yaşanan Olaylar: ").append(String.join("; ", recentEvents)).append("\n");
		}
		return sb.toString();
	}
}
