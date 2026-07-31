package com.aicaddy.ai.mood;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.AiBrainManager;
import com.aicaddy.ai.debug.CompanionDebugLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects Minecraft gameplay events and routes them to CompanionMoodEngine.
 * Runs checks on a server tick schedule (every 40 ticks = 2 seconds) for performance.
 * Also triggers proactive companion speech for major events.
 */
public class EmotionalEventDetector {

	// Tick interval for checks (40 ticks = 2 seconds)
	private static final int CHECK_INTERVAL_TICKS = 40;

	// Per-player state tracking
	private static final Map<String, Boolean> wasNight = new ConcurrentHashMap<>();
	private static final Map<String, String> lastBiome = new ConcurrentHashMap<>();
	private static final Map<String, Set<Item>> knownItems = new ConcurrentHashMap<>();
	private static final Map<String, Float> lastHealth = new ConcurrentHashMap<>();
	private static final Map<String, Long> lastSpeechTime = new ConcurrentHashMap<>();
	private static final Map<String, Integer> buildingCounter = new ConcurrentHashMap<>();

	private static final long IDLE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
	private static final float LOW_HEALTH_THRESHOLD = 0.30f; // 30% of max health

	// Items that trigger EXCITED mood when first found
	private static final Set<Item> EXCITING_ITEMS = new HashSet<>();
	private static final Set<Item> CURIOUS_ITEMS = new HashSet<>();

	static {
		EXCITING_ITEMS.add(Items.DIAMOND);
		EXCITING_ITEMS.add(Items.DIAMOND_PICKAXE);
		EXCITING_ITEMS.add(Items.NETHERITE_INGOT);
		EXCITING_ITEMS.add(Items.NETHERITE_PICKAXE);
		EXCITING_ITEMS.add(Items.ENDER_DRAGON_SPAWN_EGG);
		EXCITING_ITEMS.add(Items.ELYTRA);

		CURIOUS_ITEMS.add(Items.NETHER_STAR);
		CURIOUS_ITEMS.add(Items.TOTEM_OF_UNDYING);
		CURIOUS_ITEMS.add(Items.ANCIENT_DEBRIS);
		CURIOUS_ITEMS.add(Items.HEART_OF_THE_SEA);
		CURIOUS_ITEMS.add(Items.TRIDENT);
		CURIOUS_ITEMS.add(Items.SHULKER_BOX);
	}

	/**
	 * Registers the server tick listener. Call this from ExampleMod.onInitialize().
	 */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(EmotionalEventDetector::onServerTick);
		ExampleMod.LOGGER.info("[EmotionalEventDetector] Registered server tick listener.");
	}

	/**
	 * Called every server tick. Runs checks every CHECK_INTERVAL_TICKS ticks.
	 */
	private static void onServerTick(MinecraftServer server) {
		// Only run every 2 seconds
		if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String pid = player.getStringUUID();

			checkNightDay(player, pid);
			checkHealth(player, pid);
			checkNewBiome(player, pid);
			checkInventoryForNewItems(player, pid);
			checkCreeperProximity(player, pid);
			checkWardenZone(player, pid);
			checkDimension(player, pid);
			checkIdleness(player, pid);
		}
	}

	// ─── Event Checkers ───────────────────────────────────────────────────────

	private static void checkNightDay(ServerPlayer player, String pid) {
		boolean night = player.serverLevel().isNight();
		Boolean prev = wasNight.get(pid);

		if (prev == null) {
			wasNight.put(pid, night);
			return;
		}

		if (!prev && night) {
			wasNight.put(pid, true);
			CompanionDebugLogger.logEventDetected(player, "GECE_OLDU", "isNight() = true");
			CompanionMoodEngine.processTrigger(MoodTrigger.NIGHT_FELL, player);
			triggerProactiveSpeech(player, "night_fell",
				"Gece oldu! Companion spontane korkuyorum tarzında kısa bir şey söylesin, ders verme.");
		} else if (prev && !night) {
			wasNight.put(pid, false);
			CompanionDebugLogger.logEventDetected(player, "GUN_DOGDU", "isNight() = false");
			CompanionMoodEngine.processTrigger(MoodTrigger.DAY_CAME, player);
			triggerProactiveSpeech(player, "day_came",
				"Gün doğdu! Companion rahatlamış, mutlu, kısa spontane bir şey söylesin.");
		}
	}

	private static void checkHealth(ServerPlayer player, String pid) {
		float currentHp = player.getHealth();
		float maxHp = player.getMaxHealth();
		float ratio = currentHp / maxHp;
		Float prev = lastHealth.get(pid);

		lastHealth.put(pid, currentHp);

		if (prev == null) return;

		if (currentHp <= 0 && prev > 0) {
			CompanionDebugLogger.logEventDetected(player, "OYUNCU_ÖLDÜ", "Can 0'a düştü");
			com.aicaddy.ai.memory.PlayerMemoryStore.appendEventAsync(
				player.getUUID(), player.getScoreboardName(), "Tehlikeli şekilde can vererek öldü");
			com.aicaddy.ai.memory.PlayerMemoryStore.modifyAffinityAsync(
				player.getUUID(), player.getScoreboardName(), -2, "Tehlikeye dikkat etmeyerek öldü");
			CompanionMoodEngine.processTrigger(MoodTrigger.LOW_HEALTH, player);
		} else if (ratio < LOW_HEALTH_THRESHOLD && (prev / maxHp) >= LOW_HEALTH_THRESHOLD) {
			CompanionDebugLogger.logEventDetected(player, "DÜŞÜK_CAN",
				String.format("HP: %.1f / %.1f (%.0f%%)", currentHp, maxHp, ratio * 100));
			CompanionMoodEngine.processTrigger(MoodTrigger.LOW_HEALTH, player);
			com.aicaddy.ai.memory.PlayerMemoryStore.appendEventAsync(
				player.getUUID(), player.getScoreboardName(), "Tehlikeli şekilde canı azaldı (" + (int)(ratio * 100) + "%)");
			com.aicaddy.ai.memory.PlayerMemoryStore.modifyAffinityAsync(
				player.getUUID(), player.getScoreboardName(), -1, "Canını tehlikeli şekilde azalttı");
			triggerProactiveSpeech(player, "low_health",
				"Oyuncunun canı tehlikeli derecede düşük! Companion panikleyerek kısa bir şey söylesin.");
		}
	}

	private static void checkNewBiome(ServerPlayer player, String pid) {
		String biome = player.serverLevel()
				.getBiome(player.blockPosition())
				.unwrapKey()
				.map(k -> k.location().toString())
				.orElse("unknown");

		String prev = lastBiome.get(pid);
		if (prev == null) {
			lastBiome.put(pid, biome);
			return;
		}

		if (!biome.equals(prev)) {
			lastBiome.put(pid, biome);
			String biomeName = biome.replace("minecraft:", "").replace("_", " ");
			CompanionDebugLogger.logEventDetected(player, "YENİ_BİYOM", prev + " → " + biome);
			CompanionMoodEngine.processTrigger(MoodTrigger.NEW_BIOME, player);
			com.aicaddy.ai.memory.PlayerMemoryStore.appendEventAsync(
				player.getUUID(), player.getScoreboardName(), "'" + biomeName + "' biyomunu keşfetti");
			triggerProactiveSpeech(player, "new_biome",
				"Yeni bir biyoma girdik: '" + biomeName + "'. Companion merakla kısa bir yorum yapsın.");
		}
	}

	private static void checkInventoryForNewItems(ServerPlayer player, String pid) {
		Set<Item> known = knownItems.computeIfAbsent(pid, k -> new HashSet<>());

		for (ItemStack stack : player.getInventory().items) {
			if (stack.isEmpty()) continue;
			Item item = stack.getItem();

			if (!known.contains(item)) {
				known.add(item);

				if (EXCITING_ITEMS.contains(item)) {
					MoodTrigger trigger = (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_PICKAXE)
							? MoodTrigger.FOUND_NETHERITE
							: MoodTrigger.FOUND_DIAMOND;
					String itemName = stack.getHoverName().getString();
					CompanionDebugLogger.logEventDetected(player, "EFSANE_EŞYA", itemName);
					CompanionMoodEngine.processTrigger(trigger, player);
					com.aicaddy.ai.memory.PlayerMemoryStore.addMilestoneAsync(
						player.getUUID(), player.getScoreboardName(), "Efsanevi Eşya Kazandı: " + itemName);
					com.aicaddy.ai.memory.PlayerMemoryStore.appendEventAsync(
						player.getUUID(), player.getScoreboardName(), "Efsanevi eşya buldu: '" + itemName + "'");
					triggerProactiveSpeech(player, "exciting_item_" + itemName,
						"Oyuncu '" + itemName + "' buldu! Companion çok excited, bağıra çağıra coşsun.");

				} else if (CURIOUS_ITEMS.contains(item)) {
					String itemName = stack.getHoverName().getString();
					CompanionDebugLogger.logEventDetected(player, "İLGİNÇ_EŞYA", itemName);
					CompanionMoodEngine.processTrigger(MoodTrigger.NEW_ITEM, player);
					com.aicaddy.ai.memory.PlayerMemoryStore.appendEventAsync(
						player.getUUID(), player.getScoreboardName(), "Nadir eşya buldu: '" + itemName + "'");
					triggerProactiveSpeech(player, "curious_item_" + itemName,
						"Oyuncu ilk kez '" + itemName + "' buldu. Companion meraklı, 'bu ne ya' tarzında.");
				}
			}
		}
	}

	private static void checkCreeperProximity(ServerPlayer player, String pid) {
		var creepers = player.serverLevel()
				.getEntitiesOfClass(
						net.minecraft.world.entity.monster.Creeper.class,
						player.getBoundingBox().inflate(6.0)
				);
		boolean creeperNear = creepers.stream().anyMatch(c -> !c.isDeadOrDying());

		if (creeperNear) {
			CompanionDebugLogger.logEventDetected(player, "CREEPER_YAKIN",
				creepers.size() + " creeper(s) 6 blok içinde");
			CompanionMoodEngine.processTrigger(MoodTrigger.CREEPER_NEARBY, player);
			triggerProactiveSpeech(player, "creeper",
				"Yakında Creeper var! Companion çok kısa, panikleyen ama komik bir uyarı versin.");
		}
	}

	private static void checkWardenZone(ServerPlayer player, String pid) {
		String biome = player.serverLevel()
				.getBiome(player.blockPosition())
				.unwrapKey()
				.map(k -> k.location().toString())
				.orElse("");

		if (biome.contains("deep_dark") && player.blockPosition().getY() < -40) {
			CompanionMoodEngine.processTrigger(MoodTrigger.WARDEN_ZONE);
		}
	}

	private static void checkDimension(ServerPlayer player, String pid) {
		String dim = player.serverLevel().dimension().location().getPath();
		String prevDim = lastBiome.getOrDefault(pid + "_dim", "overworld");

		if (!dim.equals(prevDim)) {
			lastBiome.put(pid + "_dim", dim);
			if (dim.contains("nether")) {
				CompanionMoodEngine.processTrigger(MoodTrigger.NETHER_ENTERED);
				com.aicaddy.ai.memory.PlayerMemoryStore.addMilestoneAsync(
					player.getUUID(), player.getScoreboardName(), "Nether Boyutuna Adım Attı");
				com.aicaddy.ai.memory.PlayerMemoryStore.modifyAffinityAsync(
					player.getUUID(), player.getScoreboardName(), 3, "Nether macerasına adım attı");
				triggerProactiveSpeech(player, "nether_entered",
					"Nether'a girdik! Companion hem heyecanlı hem biraz gergin, kısa bir şey söylesin.");
			} else if (dim.contains("end")) {
				CompanionMoodEngine.processTrigger(MoodTrigger.END_ENTERED);
				com.aicaddy.ai.memory.PlayerMemoryStore.addMilestoneAsync(
					player.getUUID(), player.getScoreboardName(), "End Boyutuna Ulaştı");
				com.aicaddy.ai.memory.PlayerMemoryStore.modifyAffinityAsync(
					player.getUUID(), player.getScoreboardName(), 5, "End boyutunda Ejderha savaşına girdi");
				triggerProactiveSpeech(player, "end_entered",
					"End'e girdik! Ejderha var burada. Companion hem korkmuş hem coşkulu, kısa bir şey söylesin.");
			}
		}
	}

	private static void checkIdleness(ServerPlayer player, String pid) {
		long lastSpoke = lastSpeechTime.getOrDefault(pid, System.currentTimeMillis());
		long idleMs = System.currentTimeMillis() - lastSpoke;
		long idleSec = idleMs / 1000;

		if (idleMs >= IDLE_THRESHOLD_MS) {
			CompanionDebugLogger.logEventDetected(player, "OYUNCU_SESSIZ",
				idleSec + "s boşta");
			CompanionMoodEngine.processTrigger(MoodTrigger.PLAYER_IDLE, player);
			lastSpeechTime.put(pid, System.currentTimeMillis());
			triggerProactiveSpeech(player, "idle",
				"Oyuncu " + idleSec + " saniyedir sessiz. Companion 'heeey burada mısın?' tarzında uyandırsın.");
		}
	}

	// ─── Helpers ─────────────────────────────────────────────────────────────

	/**
	 * Updates the last speech time for a player (call this when player speaks).
	 */
	public static void onPlayerSpoke(ServerPlayer player) {
		lastSpeechTime.put(player.getStringUUID(), System.currentTimeMillis());
	}

	/**
	 * Records a building event for a player (call from block place event).
	 */
	public static void onBlockPlaced(ServerPlayer player) {
		String pid = player.getStringUUID();
		int count = buildingCounter.getOrDefault(pid, 0) + 1;
		buildingCounter.put(pid, count);
		// Trigger PROUD after 10 blocks placed in a session
		if (count == 10 || count == 30 || count == 60) {
			CompanionMoodEngine.processTrigger(MoodTrigger.BUILDING_DETECTED);
			triggerProactiveSpeech(player, "building_" + count,
				"Oyuncu " + count + " blok koydu, bir şeyler inşa ediyor. Companion hayran kalmış, 'ne yapıyorsun ya' tarzında kısa bir şey söylesin.");
		}
	}

	/**
	 * Records a player death event.
	 */
	public static void onPlayerDied(ServerPlayer player) {
		CompanionDebugLogger.logEventDetected(player, "OYUNCU_ÖLDÜ", "LivingDeathEvent");
		CompanionMoodEngine.processTrigger(MoodTrigger.PLAYER_DIED, player);
		triggerProactiveSpeech(player, "player_died",
			"Oyuncu az önce öldü. Companion üzgün ama teselli eden, 'geçer geçer' tarzında kısa bir şey söylesin.");
	}

	/**
	 * Fires a proactive (unsolicited) companion speech if cooldown allows.
	 */
	private static void triggerProactiveSpeech(ServerPlayer player, String eventKey, String situationHint) {
		if (!CompanionMoodEngine.canSpeakProactively(eventKey)) {
			CompanionDebugLogger.logProactiveSpeechBlocked(player, "'" + eventKey + "' cooldown aktif (30s)");
			return;
		}
		CompanionDebugLogger.logProactiveSpeechFired(player, eventKey);

		String prompt = "Sen " + player.getScoreboardName() + " ile birlikte oynayan bir arkadaşsın. "
				+ "DURUM: " + situationHint + " "
				+ CompanionMoodEngine.getMoodContext()
				+ "Maksimum 1-2 cümle. Robotik taktik verme. Sadece spontane, samimi bir arkadaş tepkisi ver. "
				+ "JSON formatında: {\"final_replik\": \"...\"}";

		AiBrainManager.triggerProactiveResponse(player, prompt);
	}
}
