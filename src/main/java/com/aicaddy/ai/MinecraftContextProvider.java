package com.aicaddy.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MinecraftContextProvider {

	/**
	 * Collects full in-game environmental and inventory context for the specified player.
	 */
	public static String getPlayerContext(ServerPlayer player) {
		if (player == null) {
			return "Player context unavailable.";
		}

		ServerLevel level = player.serverLevel();

		// 1. Health and Hunger
		float health = player.getHealth();
		float maxHealth = player.getMaxHealth();
		int hunger = player.getFoodData().getFoodLevel();

		// 2. Environment and Biome
		String dimension = level.dimension().location().getPath();
		String biome = level.getBiome(player.blockPosition())
				.unwrapKey()
				.map(key -> key.location().toString())
				.orElse("unknown");
		boolean isNight = level.isNight();
		boolean isRaining = level.isRaining();
		String timeStr = (isNight ? "Night (Monsters spawning!)" : "Day") + (isRaining ? ", Raining" : ", Clear");

		// 3. Currently held item in main hand
		ItemStack mainHand = player.getMainHandItem();
		String holding = mainHand.isEmpty() ? "Empty Hand" : mainHand.getCount() + "x " + mainHand.getHoverName().getString();

		// 4. Raycast target (Looking at Block or Entity within 7 blocks)
		String lookingAt = getLookingAtTarget(player, level);

		// 5. Inventory summary (first 15 unique non-empty stacks)
		String inventorySummary = getInventorySummary(player);

		// 6. Structure & POI Summary (P10.1 Layer 1)
		String structureAndPoi = com.aicaddy.ai.context.StructureAndPoiDetector.getStructureAndPoiSummary(player);

		// 7. Player Activity State (P10.2 Layer 2)
		String activitySummary = com.aicaddy.ai.context.PlayerActivityTracker.getActivitySummary(player.getUUID());

		// 8. Environmental Radar (P10.4 — Autonomous Companion Perception)
		String entityRadar = com.aicaddy.ai.context.EnvironmentalRadar.getNearbyEntitiesRadar(player);
		String blockRadar = com.aicaddy.ai.context.EnvironmentalRadar.getNearbyBlocksRadar(player);

		// Format structured context string for LLM prompt
		StringBuilder sb = new StringBuilder();
		sb.append("- Can: ").append(String.format("%.0f/%.0f", health, maxHealth))
		  .append(" | Açlık: ").append(hunger).append("/20\n");
		sb.append("- Boyut: ").append(dimension)
		  .append(" | Biyom: ").append(biome.replace("minecraft:", ""))
		  .append(" | ").append(timeStr).append("\n");
		sb.append("- ").append(structureAndPoi).append("\n");
		sb.append("- ").append(activitySummary).append("\n");
		sb.append("- ").append(entityRadar).append("\n");
		sb.append("- ").append(blockRadar).append("\n");
		sb.append("- Elinde: ").append(holding).append("\n");
		sb.append("- Baktığı: ").append(lookingAt).append("\n");
		sb.append("- Envanter: ").append(inventorySummary);

		return sb.toString();
	}

	/**
	 * Performs raycast to determine if player is looking at a Block or an Entity within 7 blocks.
	 */
	private static String getLookingAtTarget(ServerPlayer player, ServerLevel level) {
		// First check for entities in line of sight
		Vec3 eyePos = player.getEyePosition(1.0F);
		Vec3 viewVec = player.getViewVector(1.0F);
		Vec3 endPos = eyePos.add(viewVec.scale(7.0D));

		AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(7.0D)).inflate(1.0D);
		List<Entity> nearbyEntities = level.getEntities(player, searchBox, e -> e.isAlive() && !e.isSpectator());

		Entity closestEntity = null;
		double closestDist = Double.MAX_VALUE;

		for (Entity entity : nearbyEntities) {
			AABB entityBox = entity.getBoundingBox().inflate(0.3D);
			Optional<Vec3> clipResult = entityBox.clip(eyePos, endPos);
			if (clipResult.isPresent()) {
				double dist = eyePos.distanceTo(clipResult.get());
				if (dist < closestDist) {
					closestDist = dist;
					closestEntity = entity;
				}
			}
		}

		if (closestEntity != null) {
			return "Entity (" + closestEntity.getName().getString() + ") at distance " + String.format("%.1f", closestDist) + "m";
		}

		// Fallback to block raycast
		HitResult blockHit = player.pick(7.0D, 0.0F, false);
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = ((BlockHitResult) blockHit).getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				return "Block (" + state.getBlock().getName().getString() + ")";
			}
		}

		return "Nothing (Air / Distance)";
	}

	/**
	 * Summarizes important inventory items (up to 10 non-empty item stacks).
	 */
	private static String getInventorySummary(ServerPlayer player) {
		List<String> items = new ArrayList<>();
		for (ItemStack stack : player.getInventory().items) {
			if (!stack.isEmpty()) {
				String itemStr = stack.getCount() + "x " + stack.getHoverName().getString();
				if (!items.contains(itemStr)) {
					items.add(itemStr);
					if (items.size() >= 15) {
						break;
					}
				}
			}
		}
		return items.isEmpty() ? "Empty" : String.join(", ", items);
	}
}
