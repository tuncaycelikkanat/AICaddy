package com.aicaddy.ai.context;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P10.2 — Player Activity Tracker (Sliding Window State Machine).
 * Tracks player actions (mining, building, combat, farming, container use) in a 30-second window
 * with zero thread contention (<0.1 ms).
 */
public class PlayerActivityTracker {

	private static final long WINDOW_MS = 30_000L;
	private static final Map<UUID, ActivityWindow> PLAYERS = new ConcurrentHashMap<>();

	public static class ActivityWindow {
		public final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());
		public final AtomicInteger miningCount = new AtomicInteger(0);
		public final AtomicInteger buildCount = new AtomicInteger(0);
		public final AtomicInteger combatCount = new AtomicInteger(0);
		public final AtomicInteger farmingCount = new AtomicInteger(0);
		public final AtomicInteger containerCount = new AtomicInteger(0);

		public void resetIfExpired(long now) {
			if (now - windowStartMs.get() > WINDOW_MS) {
				windowStartMs.set(now);
				miningCount.set(0);
				buildCount.set(0);
				combatCount.set(0);
				farmingCount.set(0);
				containerCount.set(0);
			}
		}
	}

	private static ActivityWindow getWindow(UUID playerUuid) {
		ActivityWindow win = PLAYERS.computeIfAbsent(playerUuid, k -> new ActivityWindow());
		win.resetIfExpired(System.currentTimeMillis());
		return win;
	}

	public static void recordBlockBreak(UUID playerUuid, Block block) {
		if (playerUuid == null || block == null) return;
		ActivityWindow win = getWindow(playerUuid);
		if (isMiningBlock(block)) {
			win.miningCount.incrementAndGet();
		} else if (isFarmingBlock(block)) {
			win.farmingCount.incrementAndGet();
		}
	}

	public static void recordBlockPlace(UUID playerUuid, Block block) {
		if (playerUuid == null || block == null) return;
		ActivityWindow win = getWindow(playerUuid);
		if (isFarmingBlock(block)) {
			win.farmingCount.incrementAndGet();
		} else {
			win.buildCount.incrementAndGet();
		}
	}

	public static void recordCombatAttack(UUID playerUuid) {
		if (playerUuid == null) return;
		ActivityWindow win = getWindow(playerUuid);
		win.combatCount.incrementAndGet();
	}

	public static void recordContainerOpen(UUID playerUuid) {
		if (playerUuid == null) return;
		ActivityWindow win = getWindow(playerUuid);
		win.containerCount.incrementAndGet();
	}

	/**
	 * Returns a concise Turkish summary of what the player is currently busy doing.
	 * Less than 20 tokens for prompt efficiency.
	 */
	public static String getActivitySummary(UUID playerUuid) {
		if (playerUuid == null) {
			return "[Oyuncunun Mevcut Uğraşı: KEŞİF (Etrafı geziyor)]";
		}

		ActivityWindow win = getWindow(playerUuid);
		int mining = win.miningCount.get();
		int build = win.buildCount.get();
		int combat = win.combatCount.get();
		int farming = win.farmingCount.get();
		int container = win.containerCount.get();

		String activityLabel;
		if (combat >= 3) {
			activityLabel = "SAVAŞ (Canavarlar veya düşmanlarla çarpışıyor)";
		} else if (mining >= 5) {
			activityLabel = "MADENCİLİK (Taş ve maden blokları kazıyor)";
		} else if (build >= 5) {
			activityLabel = "İNŞAAT (Yeni yapılar inşa ediyor / blok yerleştiriyor)";
		} else if (farming >= 4) {
			activityLabel = "TARIM (Ekinlerle ve tarlayla uğraşıyor)";
		} else if (container >= 3) {
			activityLabel = "LOJİSTİK / ÜRETİM (Sandık, fırın veya çalışma masası kullanıyor)";
		} else {
			activityLabel = "KEŞİF (Etrafı geziyor / Yürüyor)";
		}

		return "[Oyuncunun Mevcut Uğraşı]: " + activityLabel;
	}

	private static boolean isMiningBlock(Block block) {
		return block == Blocks.STONE || block == Blocks.COBBLESTONE || block == Blocks.DEEPSLATE
				|| block == Blocks.COAL_ORE || block == Blocks.IRON_ORE || block == Blocks.GOLD_ORE
				|| block == Blocks.DIAMOND_ORE || block == Blocks.LAPIS_ORE || block == Blocks.REDSTONE_ORE
				|| block == Blocks.COPPER_ORE || block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.ANCIENT_DEBRIS
				|| block == Blocks.NETHERRACK || block == Blocks.GRANITE || block == Blocks.DIORITE || block == Blocks.ANDESITE;
	}

	private static boolean isFarmingBlock(Block block) {
		return block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES
				|| block == Blocks.BEETROOTS || block == Blocks.FARMLAND || block == Blocks.SUGAR_CANE
				|| block == Blocks.PUMPKIN || block == Blocks.MELON;
	}

	public static void clear(UUID playerUuid) {
		PLAYERS.remove(playerUuid);
	}
}
