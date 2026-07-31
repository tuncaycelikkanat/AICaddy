package com.aicaddy.entity;

import com.aicaddy.ai.mood.CompanionMoodState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P10.3 — In-Game Companion Entity (Evcilleştirilmiş Kedi Tabanlı Akıllı Yol Arkadaşı).
 * Manages a physical in-game Cat companion that follows the player, obeys voice/chat commands,
 * updates its name badge dynamically with mood, and emits emotion particles.
 */
public class AiCompanionEntity {

	private static final Map<UUID, Cat> ACTIVE_COMPANIONS = new ConcurrentHashMap<>();

	/**
	 * Spawns or retrieves the active companion cat for the specified player.
	 */
	public static synchronized Cat getOrCreateCompanion(ServerPlayer player) {
		if (player == null) return null;
		UUID uuid = player.getUUID();

		Cat existing = ACTIVE_COMPANIONS.get(uuid);
		if (existing != null && existing.isAlive()) {
			if (existing.level() != player.level()) {
				existing.discard();
				ACTIVE_COMPANIONS.remove(uuid);
			} else {
				return existing;
			}
		}

		ServerLevel level = player.serverLevel();
		Cat cat = EntityType.CAT.create(level);
		if (cat == null) return null;

		cat.setPos(player.getX() + 1.0D, player.getY(), player.getZ() + 1.0D);
		cat.tame(player);
		cat.setOrderedToSit(false);
		cat.addTag("ai_companion");

		updateNameBadge(cat, CompanionMoodState.CURIOUS);
		level.addFreshEntity(cat);

		ACTIVE_COMPANIONS.put(uuid, cat);
		return cat;
	}

	/**
	 * Oyuncu sunucudan çıktığında dünyadaki kedi varlığını siler (Entity/Memory Leak koruması).
	 */
	public static synchronized void removeCompanion(ServerPlayer player) {
		if (player == null) return;
		Cat existing = ACTIVE_COMPANIONS.remove(player.getUUID());
		if (existing != null && existing.isAlive()) {
			existing.discard();
		}
	}

	/**
	 * Updates the companion cat's floating name tag badge based on current Turkish mood label.
	 */
	public static void updateMoodBadge(ServerPlayer player, CompanionMoodState mood) {
		if (player == null || mood == null) return;
		Cat cat = getOrCreateCompanion(player);
		if (cat != null) {
			updateNameBadge(cat, mood);
		}
	}

	private static void updateNameBadge(Cat cat, CompanionMoodState mood) {
		String colorCode = getMoodColorCode(mood);
		cat.setCustomName(Component.literal(colorCode + "🤝 [AI Arkadaş - " + mood.getLabel().toUpperCase() + "]"));
		cat.setCustomNameVisible(true);
	}

	private static String getMoodColorCode(CompanionMoodState mood) {
		switch (mood) {
			case EXCITED: return "§d✨ ";
			case SCARED: return "§c🙀 ";
			case SAD: return "§9😿 ";
			case PROUD: return "§a😸 ";
			case FRUSTRATED: return "§4😾 ";
			case CURIOUS: return "§b🧐 ";
			case TENSE: return "§6😼 ";
			case BORED:
			default: return "§7🤝 ";
		}
	}

	public static boolean isSitCommand(String speech) {
		if (speech == null) return false;
		String lower = speech.toLowerCase().trim();
		return lower.contains("otur") || lower.contains("bekle") ||
				lower.contains("kalkma") || lower.contains("dur orada") ||
				lower.contains("burada kal");
	}

	public static boolean isFollowCommand(String speech) {
		if (speech == null) return false;
		String lower = speech.toLowerCase().trim();
		return lower.contains("yanıma gel") || lower.contains("beni takip et") ||
				lower.contains("gel buraya") || lower.contains("peşimden gel") ||
				lower.contains("arkamdan gel") || lower.equals("gel") ||
				lower.contains("gelsene") || lower.contains("gelesene") ||
				lower.contains("benimle gel");
	}

	public static boolean isInvestigateCommand(String speech) {
		if (speech == null) return false;
		String lower = speech.toLowerCase().trim();
		return lower.contains("şuraya bak") || lower.contains("incele") ||
				lower.contains("şu bloğa bak") || lower.contains("oraya bak") ||
				lower.contains("baksana") || lower.contains("şuna bak") ||
				lower.contains("ne var orada");
	}

	/**
	 * Executes physical companion commands triggered by voice or chat.
	 * Examples: "Otur", "Yanıma gel", "İncele"
	 */
	public static boolean handleCompanionCommand(ServerPlayer player, String speech) {
		if (player == null || speech == null) return false;

		Cat cat = getOrCreateCompanion(player);
		if (cat == null) return false;

		if (isSitCommand(speech)) {
			CompanionBehaviorFsm.fireEvent(player, CompanionBehaviorEvent.COMMAND_SIT);
			spawnParticles(player, cat, "NOTE");
			player.sendSystemMessage(Component.literal("§e🤝 [AI Arkadaş]: §fBurada oturup seni bekliyorum!"));
			return true;
		}

		if (isFollowCommand(speech)) {
			CompanionBehaviorFsm.fireEvent(player, CompanionBehaviorEvent.COMMAND_FOLLOW);
			spawnParticles(player, cat, "HEART");
			player.sendSystemMessage(Component.literal("§e🤝 [AI Arkadaş]: §fHemen yanına geliyorum!"));
			return true;
		}

		if (isInvestigateCommand(speech)) {
			cat.setOrderedToSit(false);
			Vec3 lookVec = player.getViewVector(1.0F);
			BlockPos target = player.blockPosition().offset((int) (lookVec.x * 5), 0, (int) (lookVec.z * 5));
			cat.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.25D);
			spawnParticles(player, cat, "ENCHANTED");
			player.sendSystemMessage(Component.literal("§e🤝 [AI Arkadaş]: §fBakalım orada ne varmış, inceliyorum!"));
			return true;
		}

		return false;
	}

	/**
	 * Triggers the CELEBRATE behavior in the physical companion FSM.
	 */
	public static void triggerCelebration(ServerPlayer player) {
		if (player == null) return;
		CompanionBehaviorFsm.fireEvent(player, CompanionBehaviorEvent.PLAYER_ACHIEVEMENT);
	}

	/**
	 * Spawns emotion particles around the companion cat.
	 */
	public static void spawnEmotionParticles(ServerPlayer player, CompanionMoodState mood) {
		if (player == null || mood == null) return;
		Cat cat = getOrCreateCompanion(player);
		if (cat == null) return;

		switch (mood) {
			case EXCITED:
			case PROUD:
				spawnParticles(player, cat, "HEART");
				break;
			case CURIOUS:
			case TENSE:
				spawnParticles(player, cat, "ENCHANTED");
				break;
			case SCARED:
			case FRUSTRATED:
				spawnParticles(player, cat, "ANGRY");
				break;
			default:
				spawnParticles(player, cat, "NOTE");
				break;
		}
	}

	private static void spawnParticles(ServerPlayer player, Cat cat, String type) {
		ServerLevel level = player.serverLevel();
		double x = cat.getX();
		double y = cat.getY() + 0.8D;
		double z = cat.getZ();

		if ("HEART".equals(type)) {
			level.sendParticles(ParticleTypes.HEART, x, y, z, 4, 0.3D, 0.3D, 0.3D, 0.05D);
		} else if ("ENCHANTED".equals(type)) {
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 8, 0.4D, 0.4D, 0.4D, 0.1D);
		} else if ("ANGRY".equals(type)) {
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y, z, 3, 0.3D, 0.3D, 0.3D, 0.05D);
		} else {
			level.sendParticles(ParticleTypes.NOTE, x, y, z, 4, 0.3D, 0.3D, 0.3D, 0.05D);
		}
	}

	/**
	 * Calculates a smart flee position away from nearby hostile monsters.
	 * If monsters are nearby, Kedi runs in the opposite direction; otherwise runs toward player.
	 */
	public static Vec3 calculateSmartFleePosition(ServerPlayer player, Cat cat) {
		if (cat == null || player == null) return Vec3.ZERO;

		List<Monster> monsters = cat.level().getEntitiesOfClass(
				Monster.class, cat.getBoundingBox().inflate(12.0));

		if (monsters.isEmpty()) {
			return player.position();
		}

		double avgX = 0, avgZ = 0;
		for (Monster m : monsters) {
			avgX += m.getX();
			avgZ += m.getZ();
		}
		avgX /= monsters.size();
		avgZ /= monsters.size();

		double dx = cat.getX() - avgX;
		double dz = cat.getZ() - avgZ;
		double dist = Math.sqrt(dx * dx + dz * dz);

		if (dist < 0.01) {
			return player.position();
		}

		double normDx = dx / dist;
		double normDz = dz / dist;

		return new Vec3(cat.getX() + normDx * 8.0, cat.getY(), cat.getZ() + normDz * 8.0);
	}

	public static void clear(UUID playerUuid) {
		ACTIVE_COMPANIONS.remove(playerUuid);
		CompanionBehaviorFsm.removeFsm(playerUuid);
	}
}
