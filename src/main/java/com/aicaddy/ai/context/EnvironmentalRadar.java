package com.aicaddy.ai.context;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * P10.4 — Environmental Radar for Autonomous Companion AI.
 * Scans nearby entities (monsters, animals, items) within 15 blocks
 * and interesting voxel grid blocks (ores, hazards, utilities) within 10 blocks.
 */
public final class EnvironmentalRadar {

    private static volatile int RADAR_RANGE = 15; // varsayılan: 15 blok
    private static volatile boolean XRAY_MODE = false; // varsayılan: KAPALI (Yerin altını/duvar arkasını görmez)

    private EnvironmentalRadar() {}

    public static int getRadarRange() {
        return RADAR_RANGE;
    }

    public static void setRadarRange(int range) {
        RADAR_RANGE = Math.max(3, Math.min(32, range));
    }

    public static boolean isXrayMode() {
        return XRAY_MODE;
    }

    public static void setXrayMode(boolean xray) {
        XRAY_MODE = xray;
    }

    /**
     * Scans nearby entities within RADAR_RANGE and formats direction & distance.
     */
    public static String getNearbyEntitiesRadar(ServerPlayer player) {
        if (player == null) return "Varlık radarı kullanılamıyor.";

        ServerLevel level = player.serverLevel();
        AABB searchBox = player.getBoundingBox().inflate(RADAR_RANGE);
        List<Entity> entities = level.getEntities(player, searchBox);

        List<String> monsters = new ArrayList<>();
        List<String> animals = new ArrayList<>();
        List<String> items = new ArrayList<>();

        for (Entity e : entities) {
            if (!e.isAlive()) continue;
            double dist = Math.sqrt(player.distanceToSqr(e));
            if (dist > RADAR_RANGE) continue;

            // Skip companion cat itself
            if (e.getTags().contains("ai_companion")) continue;

            // If X-Ray mode is OFF, ignore entities hidden behind walls or underground without line-of-sight
            if (!XRAY_MODE && !player.hasLineOfSight(e)) {
                continue;
            }

            String dirStr = getDirectionLabel(player.position(), e.position(), dist);
            String name = e.getDisplayName().getString();

            if (e instanceof Monster) {
                if (monsters.size() < 4) {
                    monsters.add(name + " (" + dirStr + ")");
                }
            } else if (e instanceof ItemEntity itemEntity) {
                if (items.size() < 3) {
                    String itemName = itemEntity.getItem().getHoverName().getString();
                    items.add(itemName + " (" + dirStr + ")");
                }
            } else if (e instanceof Animal) {
                if (animals.size() < 2) {
                    animals.add(name + " (" + dirStr + ")");
                }
            }
        }

        if (monsters.isEmpty() && items.isEmpty() && animals.isEmpty()) {
            return "[RADAR - YAKIN VARLIKLAR]: " + RADAR_RANGE + " blok çevrede Görüş Alanında tehdit veya önemli varlık yok.";
        }

        StringBuilder sb = new StringBuilder("[RADAR - YAKIN VARLIKLAR]: ");
        List<String> parts = new ArrayList<>();
        if (!monsters.isEmpty()) {
            parts.add("Canavarlar: " + String.join(", ", monsters));
        }
        if (!items.isEmpty()) {
            parts.add("Yerdeki Eşyalar: " + String.join(", ", items));
        }
        if (!animals.isEmpty()) {
            parts.add("Hayvanlar: " + String.join(", ", animals));
        }
        sb.append(String.join(" | ", parts));
        return sb.toString();
    }

    /**
     * Scans nearby voxel grid around the player for ores, hazards, and utility blocks.
     */
    public static String getNearbyBlocksRadar(ServerPlayer player) {
        if (player == null) return "Blok radarı kullanılamıyor.";

        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();

        List<String> importantBlocks = new ArrayList<>();

        int radiusXZ = Math.min(RADAR_RANGE, 12);
        int radiusY = Math.min(RADAR_RANGE / 2, 6);

        for (int dx = -radiusXZ; dx <= radiusXZ; dx += 2) {
            if (importantBlocks.size() >= 5) break;
            for (int dy = -radiusY; dy <= radiusY; dy += 2) {
                if (importantBlocks.size() >= 5) break;
                for (int dz = -radiusXZ; dz <= radiusXZ; dz += 2) {
                    if (importantBlocks.size() >= 5) break;
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    String name = state.getBlock().getName().getString();
                    String blockId = state.getBlock().getDescriptionId().toLowerCase();

                    boolean isOre = blockId.contains("ore") || blockId.contains("ancient_debris");
                    boolean isHazard = blockId.contains("lava") || blockId.contains("spawner");
                    boolean isUtility = blockId.contains("chest") || blockId.contains("crafting_table") || blockId.contains("enchanting_table");

                    if (isOre || isHazard || isUtility) {
                        double dist = Math.sqrt(player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()));
                        if (dist <= RADAR_RANGE && importantBlocks.size() < 5) {
                            // If X-Ray mode is OFF, ignore buried underground / occluded blocks
                            if (!XRAY_MODE && !isBlockVisibleToPlayer(player, level, pos)) {
                                continue;
                            }
                            String dirStr = getDirectionLabel(player.position(), new Vec3(pos.getX(), pos.getY(), pos.getZ()), dist);
                            importantBlocks.add(name + " [X:" + pos.getX() + ", Y:" + pos.getY() + ", Z:" + pos.getZ() + "] (" + dirStr + ")");
                        }
                    }
                }
            }
        }

        if (importantBlocks.isEmpty()) {
            return "[RADAR - ÖNEMLİ BLOKLAR]: " + RADAR_RANGE + " blok çapında Görüş Alanında kayda değer cevher/tehlike/araç yok.";
        }

        return "[RADAR - ÖNEMLİ BLOKLAR]: " + String.join("; ", importantBlocks);
    }

    private static boolean isBlockVisibleToPlayer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        // 1. Must have at least one adjacent non-solid face (exposed to air, water, or cave)
        boolean hasExposedFace = false;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockState adj = level.getBlockState(pos.relative(dir));
            if (!adj.isSolidRender(level, pos.relative(dir))) {
                hasExposedFace = true;
                break;
            }
        }
        if (!hasExposedFace) return false;

        // 2. Perform Line-of-Sight raycast from player's eyes to block center
        Vec3 eyePos = player.getEyePosition();
        Vec3 blockCenter = pos.getCenter();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(
                new net.minecraft.world.level.ClipContext(
                        eyePos, blockCenter,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        player
                )
        );

        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ||
               hit.getBlockPos().equals(pos);
    }

    private static String getDirectionLabel(Vec3 from, Vec3 to, double dist) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        int distInt = (int) Math.round(dist);

        String dir = "Yakında";
        if (Math.abs(dx) > Math.abs(dz)) {
            dir = dx > 0 ? "Doğu" : "Batı";
        } else if (Math.abs(dz) > 0.5) {
            dir = dz > 0 ? "Güney" : "Kuzey";
        }
        return distInt + "m " + dir;
    }
}
