package com.aicaddy.ai.context;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class ThreatAssessor {
    private ThreatAssessor() {}
    
    public record ThreatResult(float score, String description) {}
    
    public static ThreatResult assess(ServerPlayer player) {
        float score = 0.0f;
        List<String> factors = new ArrayList<>();
        
        // 1. HP oranı (düşük HP = yüksek tehdit)
        float hpRatio = player.getHealth() / player.getMaxHealth();
        if (hpRatio < 0.25f) { score += 0.35f; factors.add("canı kritik seviyede"); }
        else if (hpRatio < 0.5f) { score += 0.20f; factors.add("canı azalmış"); }
        
        // 2. Gece
        if (player.serverLevel().isNight()) { score += 0.15f; factors.add("gece"); }
        
        // 3. Y < 0 (yeraltı)
        if (player.blockPosition().getY() < 0) { score += 0.10f; factors.add("yeraltında"); }
        
        // 4. Y < -40 (deep dark bölgesi)
        if (player.blockPosition().getY() < -40) { score += 0.15f; factors.add("derin karanlık"); }
        
        // 5. Nether boyutu
        String dim = player.serverLevel().dimension().location().getPath();
        if (dim.contains("nether")) { score += 0.15f; factors.add("Nether'da"); }
        if (dim.contains("end")) { score += 0.20f; factors.add("End'de"); }
        
        // 6. Yakın mob tehdit puanları (10 blok yarıçap)
        AABB searchBox = player.getBoundingBox().inflate(10.0);
        ServerLevel level = player.serverLevel();
        
        int wardenCount = level.getEntitiesOfClass(Warden.class, searchBox).size();
        if (wardenCount > 0) { score += 0.50f; factors.add("Warden"); }
        
        int creeperCount = level.getEntitiesOfClass(Creeper.class, searchBox).size();
        if (creeperCount > 0) { score += Math.min(creeperCount, 2) * 0.30f; factors.add("Creeper"); }
        
        int skeletonCount = level.getEntitiesOfClass(Skeleton.class, searchBox).size();
        int witchCount = level.getEntitiesOfClass(Witch.class, searchBox).size();
        if (skeletonCount > 0 || witchCount > 0) {
            score += Math.min(skeletonCount + witchCount, 3) * 0.10f;
            factors.add("Skeleton/Witch");
        }
        
        int zombieCount = level.getEntitiesOfClass(Zombie.class, searchBox).size();
        if (zombieCount > 0) { score += Math.min(zombieCount, 3) * 0.05f; factors.add("Zombi"); }
        
        int endermanCount = level.getEntitiesOfClass(EnderMan.class, searchBox).size();
        if (endermanCount > 0) { score += 0.10f; factors.add("Enderman"); }
        
        int blazeCount = level.getEntitiesOfClass(Blaze.class, searchBox).size();
        if (blazeCount > 0) { score += 0.20f; factors.add("Blaze"); }
        
        int ghastCount = level.getEntitiesOfClass(Ghast.class, searchBox).size();
        if (ghastCount > 0) { score += 0.25f; factors.add("Ghast"); }
        
        int spiderCount = level.getEntitiesOfClass(Spider.class, searchBox).size();
        int caveSpiderCount = level.getEntitiesOfClass(CaveSpider.class, searchBox).size();
        if (spiderCount > 0 || caveSpiderCount > 0) {
            score += (spiderCount + caveSpiderCount) * 0.05f;
            factors.add("Örümcek");
        }
        
        // Cap score at 1.0
        score = Math.min(1.0f, score);
        
        // Türkçe açıklama
        String desc = buildDescription(score, factors);
        return new ThreatResult(score, desc);
    }
    
    private static String buildDescription(float score, List<String> factors) {
        String level;
        if (score >= 0.80f) level = "ÇOK YÜKSEK TEHDİT — ölüm kapıda";
        else if (score >= 0.55f) level = "YÜKSEK TEHDİT — dikkat et";
        else if (score >= 0.30f) level = "ORTA TEHDİT — temkinli ol";
        else if (score >= 0.10f) level = "DÜŞÜK TEHDİT — görece güvende";
        else level = "TEHDİT YOK — tamamen güvende";
        
        return level + (factors.isEmpty() ? "" : " (" + String.join(", ", factors) + ")");
    }
    
    // Prompt'a enjekte edilecek özet
    public static String toPromptString(ServerPlayer player) {
        ThreatResult result = assess(player);
        return "[TEHDİT SEVİYESİ]: " + result.description();
    }
}
