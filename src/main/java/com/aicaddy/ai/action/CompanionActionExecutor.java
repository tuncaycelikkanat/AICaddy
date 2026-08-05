package com.aicaddy.ai.action;

import com.aicaddy.ExampleMod;
import com.aicaddy.entity.AiCompanionEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P10.5 — Autonomous Action Executor for AI Companion.
 * Parses "actions" from LLM JSON output and executes physical Minecraft movements,
 * attacks, mining, looking, following, and fleeing on the companion Cat entity.
 */
public final class CompanionActionExecutor {

    private static volatile List<CompanionAction> PENDING_ACTIONS = new ArrayList<>();

    private CompanionActionExecutor() {}

    /**
     * Queues actions directly (useful for tests or programmatic triggers).
     */
    public static synchronized void queueActions(List<CompanionAction> actions) {
        if (actions != null && !actions.isEmpty()) {
            PENDING_ACTIONS = new ArrayList<>(actions);
        }
    }

    public static synchronized int getPendingActionCount() {
        return PENDING_ACTIONS.size();
    }

    public static synchronized void clearPendingActions() {
        PENDING_ACTIONS = new ArrayList<>();
    }

    /**
     * Extracts and queues CompanionAction items from raw JSON string emitted by Gemini/Groq.
     */
    public static synchronized void queueActionsFromJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;

        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
            if (root.has("actions") && root.get("actions").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("actions");
                List<CompanionAction> actions = new ArrayList<>();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String type = obj.has("type") ? obj.get("type").getAsString() : "";
                    String target = obj.has("target") ? obj.get("target").getAsString() : "";
                    int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
                    int y = obj.has("y") ? obj.get("y").getAsInt() : 0;
                    int z = obj.has("z") ? obj.get("z").getAsInt() : 0;
                    String reason = obj.has("reason") ? obj.get("reason").getAsString() : "";

                    CompanionAction action = new CompanionAction(type, target, x, y, z, reason);
                    if (action.isValid()) {
                        actions.add(action);
                    }
                }
                if (!actions.isEmpty()) {
                    PENDING_ACTIONS = actions;
                    ExampleMod.LOGGER.info("🤝 [CompanionActionExecutor] Queued {} actions from JSON",
                            actions.size());
                }
            }
        } catch (Exception ignored) {
            // Not JSON or incomplete JSON; ignore silently
        }
    }

    /**
     * Executes queued actions for the specified player on the Server Main Thread.
     */
    public static synchronized void executeQueuedActions(ServerPlayer player) {
        if (player == null || PENDING_ACTIONS.isEmpty()) return;
        List<CompanionAction> actions = new ArrayList<>(PENDING_ACTIONS);
        PENDING_ACTIONS = new ArrayList<>();

        Cat cat = AiCompanionEntity.getOrCreateCompanion(player);
        if (cat == null || !cat.isAlive()) return;

        for (CompanionAction action : actions) {
            executeSingleAction(player, cat, action);
        }
    }

    private static void executeSingleAction(ServerPlayer player, Cat cat, CompanionAction action) {
        String type = action.type().toUpperCase().trim();
        String reasonStr = (action.reason() != null && !action.reason().isBlank())
                ? " (" + action.reason() + ")" : "";

        switch (type) {
            case "MOVE_TO" -> {
                cat.setOrderedToSit(false);
                cat.getNavigation().moveTo(action.x(), action.y(), action.z(), 1.35D);
                player.sendSystemMessage(Component.literal(
                        "§e⚡ [Yoldaş Eylemi]: §a[" + action.x() + ", " + action.y() + ", " + action.z() +
                                "] koordinatına gidiyorum." + reasonStr
                ));
            }
            case "ATTACK_ENTITY" -> {
                LivingEntity targetEntity = findNearbyEntity(player, action.target());
                if (targetEntity != null) {
                    cat.setOrderedToSit(false);
                    cat.setTarget(targetEntity);
                    cat.getNavigation().moveTo(targetEntity, 1.45D);
                    player.sendSystemMessage(Component.literal(
                            "§e⚡ [Yoldaş Eylemi]: §c⚔️ " + targetEntity.getDisplayName().getString() +
                                    " hedefine saldırıyorum!" + reasonStr
                    ));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§e⚡ [Yoldaş Eylemi]: §7Saldıracak '" + action.target() + "' yakında bulunamadı."
                    ));
                }
            }
            case "FOLLOW_PLAYER" -> {
                cat.setOrderedToSit(false);
                cat.getNavigation().moveTo(player, 1.35D);
                player.sendSystemMessage(Component.literal(
                        "§e⚡ [Yoldaş Eylemi]: §fHemen yanına geliyorum!" + reasonStr
                ));
            }
            case "FLEE_DANGER" -> {
                LivingEntity monster = findNearbyMonster(player);
                if (monster != null) {
                    Vec3 away = cat.position().subtract(monster.position()).normalize().scale(8.0);
                    Vec3 safePos = cat.position().add(away);
                    cat.setOrderedToSit(false);
                    cat.getNavigation().moveTo(safePos.x, safePos.y, safePos.z, 1.45D);
                    player.sendSystemMessage(Component.literal(
                            "§e⚡ [Yoldaş Eylemi]: §c🏃 Tehlikeden uzaklaşıyorum!" + reasonStr
                    ));
                }
            }
            case "MINE_BLOCK" -> {
                BlockPos pos = new BlockPos(action.x(), action.y(), action.z());
                cat.setOrderedToSit(false);
                cat.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.25D);
                player.sendSystemMessage(Component.literal(
                        "§e⚡ [Yoldaş Eylemi]: §b⛏️ [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() +
                                "] bloğunu inceliyor/kazıyorum!" + reasonStr
                ));
                double dist = Math.sqrt(cat.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()));
                if (dist <= 4.5D) {
                    player.serverLevel().destroyBlock(pos, true);
                }
            }
            case "LOOK_AT" -> {
                LivingEntity targetEntity = findNearbyEntity(player, action.target());
                if (targetEntity != null) {
                    cat.getLookControl().setLookAt(targetEntity);
                } else {
                    cat.getLookControl().setLookAt(player);
                }
                player.sendSystemMessage(Component.literal(
                        "§e⚡ [Yoldaş Eylemi]: §f" + action.target() + " hedefini inceliyorum." + reasonStr
                ));
            }
            case "FETCH_ITEM" -> {
                ItemEntity targetItem = findNearbyItemEntity(player, action.target());
                if (targetItem != null) {
                    cat.setOrderedToSit(false);
                    cat.getNavigation().moveTo(targetItem, 1.45D);
                    player.sendSystemMessage(Component.literal(
                            "§e⚡ [Yoldaş Eylemi]: §a" + targetItem.getItem().getHoverName().getString() + " eşyasını sana getiriyorum!" + reasonStr
                    ));
                    targetItem.setPos(player.getX(), player.getY(), player.getZ());
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§e⚡ [Yoldaş Eylemi]: §7Etrafta '" + action.target() + "' bulamadım."
                    ));
                }
            }
            case "DEFEND_PLAYER" -> {
                LivingEntity monster = findNearbyMonster(player);
                if (monster != null) {
                    cat.setOrderedToSit(false);
                    cat.setTarget(monster);
                    cat.getNavigation().moveTo(monster, 1.55D);
                    player.sendSystemMessage(Component.literal(
                            "§e⚡ [Yoldaş Eylemi]: §c🛡️ Seni " + monster.getDisplayName().getString() + " hedefinden koruyorum!" + reasonStr
                    ));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§e⚡ [Yoldaş Eylemi]: §aEtrafta tehdit yok, güvendeyiz."
                    ));
                }
            }
            case "SIT" -> {
                cat.setOrderedToSit(true);
                player.sendSystemMessage(Component.literal(
                        "§e⚡ [Yoldaş Eylemi]: §fOturup bekliyorum." + reasonStr
                ));
            }
            default -> ExampleMod.LOGGER.debug("Unknown action type requested: {}", type);
        }

        ExampleMod.LOGGER.info("🤝 [ActionExecuted] type={}, target={}, pos=[{},{},{}]",
                type, action.target(), action.x(), action.y(), action.z());
    }

    private static LivingEntity findNearbyEntity(ServerPlayer player, String targetName) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(15.0);
        List<Entity> entities = level.getEntities(player, box);

        for (Entity e : entities) {
            if (!(e instanceof LivingEntity le) || !e.isAlive()) continue;
            if (e.getTags().contains("ai_companion")) continue;
            String name = e.getDisplayName().getString().toLowerCase();
            String targetLower = targetName == null ? "" : targetName.toLowerCase();
            if (name.contains(targetLower) || e.getType().getDescriptionId().toLowerCase().contains(targetLower)) {
                return le;
            }
        }
        return null;
    }

    private static LivingEntity findNearbyMonster(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(15.0);
        List<Entity> entities = level.getEntities(player, box);
        for (Entity e : entities) {
            if (e instanceof Monster m && m.isAlive()) {
                return m;
            }
        }
        return null;
    }

    private static ItemEntity findNearbyItemEntity(ServerPlayer player, String targetName) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(15.0);
        List<Entity> entities = level.getEntities(player, box);

        for (Entity e : entities) {
            if (e instanceof ItemEntity itemEntity) {
                String name = itemEntity.getItem().getHoverName().getString().toLowerCase();
                String targetLower = targetName == null ? "" : targetName.toLowerCase();
                if (name.contains(targetLower) || itemEntity.getItem().getDescriptionId().toLowerCase().contains(targetLower)) {
                    return itemEntity;
                }
            }
        }
        return null;
    }
}
