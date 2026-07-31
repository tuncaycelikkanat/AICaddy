package com.aicaddy.ai.context;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GamePhaseAnalyzer {
    public enum GamePhase {
        EARLY,    // Taş/ahşap alet, az kaynak
        MID,      // Demir/altın alet
        LATE,     // Elmas alet, enchantment
        ENDGAME   // Netherite, Elytra, Totem, Nether Star
    }
    
    private GamePhaseAnalyzer() {}
    
    public static GamePhase analyze(ServerPlayer player) {
        if (player == null) return GamePhase.EARLY;
        GamePhase scanned = scanInventory(player);
        return GamePhaseFsm.updateAndGetPhase(player, scanned);
    }

    private static GamePhase scanInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int endgameItems = 0;
        int lateItems = 0;
        int midItems = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();

            if (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SWORD || 
                item == Items.NETHERITE_PICKAXE || item == Items.NETHERITE_CHESTPLATE || 
                item == Items.ELYTRA || item == Items.NETHER_STAR || item == Items.TOTEM_OF_UNDYING) {
                return GamePhase.ENDGAME;
            }

            if (item == Items.DIAMOND_SWORD || item == Items.DIAMOND_PICKAXE || 
                item == Items.DIAMOND_CHESTPLATE || item == Items.ENCHANTED_GOLDEN_APPLE || 
                item == Items.ENDER_EYE || item == Items.BEACON || item == Items.DIAMOND_BLOCK) {
                lateItems++;
            }

            if (item == Items.IRON_SWORD || item == Items.IRON_PICKAXE || 
                item == Items.IRON_CHESTPLATE || item == Items.SHIELD || 
                item == Items.BOW || item == Items.GOLDEN_APPLE || 
                item == Items.SADDLE || item == Items.FLINT_AND_STEEL) {
                midItems++;
            }
        }

        if (lateItems >= 2) return GamePhase.LATE;
        if (midItems >= 2) return GamePhase.MID;

        return GamePhase.EARLY;
    }
    
    // Türkçe açıklama — prompt'a enjekte edilir
    public static String toPromptDescription(GamePhase phase) {
        return switch (phase) {
            case EARLY -> "Yeni başlamış — taş aletler, az kaynak. Yoldaş küçük şeylere bile heyecanlanır.";
            case MID -> "Orta seviye — demir/altın aletler, biraz deneyim. Yoldaş meraklı ve hevesli.";
            case LATE -> "Deneyimli — elmas aletler, büyülü eşyalar. Yoldaş gururlu, bilgili ama kibirli değil.";
            case ENDGAME -> "Efsane seviye — Netherite, Elytra, her şeyi gördü. Yoldaş derin, nostaljik, 'hepsini biliyor' havası.";
        };
    }
}
