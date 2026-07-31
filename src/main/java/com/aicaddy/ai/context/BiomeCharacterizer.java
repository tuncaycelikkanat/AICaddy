package com.aicaddy.ai.context;

import net.minecraft.server.level.ServerPlayer;

public final class BiomeCharacterizer {
    private BiomeCharacterizer() {}
    
    public record BiomeCharacter(String biomeId, String turkishName, String emotionalNote, float moodValenceHint) {}
    
    public static BiomeCharacter characterize(ServerPlayer player) {
        String biomeId = player.serverLevel()
            .getBiome(player.blockPosition())
            .unwrapKey()
            .map(k -> k.location().toString())
            .orElse("minecraft:plains");
        return fromBiomeId(biomeId);
    }
    
    public static BiomeCharacter fromBiomeId(String id) {
        String lower = id.toLowerCase();
        
        if (lower.contains("deep_dark") || lower.contains("ancient_city")) 
            return new BiomeCharacter(id, "Ölüm Sessizliği", "Warden alanı. Nefes alma, ses çıkarma. Fısılda, paniği hissettir.", -0.8f);
        if (lower.contains("soul_sand_valley")) 
            return new BiomeCharacter(id, "Ruhlar Vadisi", "Nether'ın en kasvetli yeri. Ruhlar bağırıyor. Varoluşsal korku.", -0.7f);
        if (lower.contains("nether_wastes")) 
            return new BiomeCharacter(id, "Nether Çorakları", "Lanetli topraklar. Her köşede tehlike. Hep tetikte.", -0.5f);
        if (lower.contains("basalt_deltas")) 
            return new BiomeCharacter(id, "Bazalt Deltaları", "Boğucu, dar, Magma Cube'lar her yerde. Nefes almak bile zor.", -0.4f);
        if (lower.contains("crimson_forest")) 
            return new BiomeCharacter(id, "Kızıl Orman", "Kanlı kırmızı ağaçlar. Garip ama büyüleyici. Piglin toprağı.", -0.2f);
        if (lower.contains("warped_forest")) 
            return new BiomeCharacter(id, "Çarpık Orman", "Nether'ın en güzel yeri. Turkuaz renkler. Görece sakin.", 0.2f);
        if (lower.contains("end_highlands")) 
            return new BiomeCharacter(id, "End Karaları", "End Şehirleri var. Elytra ve hazine belki yakın.", 0.3f);
        if (lower.contains("the_end")) 
            return new BiomeCharacter(id, "Son", "Sonsuz karanlık, mor kristaller. Ejderha burada. Epic an yaklaşıyor.", 0.0f);
        if (lower.contains("cherry_grove")) 
            return new BiomeCharacter(id, "Kiraz Bahçesi", "Pembe çiçekler, romantik rüzgar. Minecraft'ın en güzel köşesi. Şarkı söyle.", 0.9f);
        if (lower.contains("mushroom_fields")) 
            return new BiomeCharacter(id, "Mantar Adası", "Dünyadan kopmuş huzurlu ada. Mob yok. Garip ama cennet gibi.", 0.8f);
        if (lower.contains("lush_caves")) 
            return new BiomeCharacter(id, "Yeşil Mağara", "Yeraltı cenneti. Pembe kiraz ve yeşillik. 'Bu kadar güzel nasıl?' dedirtiyor.", 0.7f);
        if (lower.contains("meadow")) 
            return new BiomeCharacter(id, "Çayır", "Yumuşak çimen, dağ manzarası. Rahat, huzurlu ama biraz sıradan.", 0.6f);
        if (lower.contains("frozen_peaks") || lower.contains("jagged_peaks")) 
            return new BiomeCharacter(id, "Buz Zirveleri", "Dondurucu, nefes kesici manzara. Hayranlık ve soğuk birlikte.", 0.3f);
        if (lower.contains("desert")) 
            return new BiomeCharacter(id, "Çöl", "Sıcak, kuru, monoton. Piramit var mı acaba?", 0.1f);
        if (lower.contains("jungle")) 
            return new BiomeCharacter(id, "Orman", "Gizemli, girift. Tapınak saklı olabilir. Dikkatli ol.", 0.2f);
        if (lower.contains("deep_ocean") || lower.contains("ocean")) 
            return new BiomeCharacter(id, "Okyanus", "Derin karanlık sular. Altında ne olduğu belli değil. Güzel ama ürkütücü.", -0.1f);
        if (lower.contains("swamp")) 
            return new BiomeCharacter(id, "Bataklık", "Kasvetli, ıslak. Cadı olabilir. Hoş değil ama bir çekimi var.", -0.2f);
        if (lower.contains("plains")) 
            return new BiomeCharacter(id, "Ovalar", "Sıradan ama güvenli. Köy olabilir yakında.", 0.4f);
        if (lower.contains("forest")) 
            return new BiomeCharacter(id, "Orman", "Ağaçlar, gölge. Gündüz sakin gece tehlikeli.", 0.3f);
        if (lower.contains("taiga")) 
            return new BiomeCharacter(id, "Tayga", "Çam ağaçları, kurt ve ayı. Güzel ama vahşi.", 0.2f);
        if (lower.contains("snowy_plains") || lower.contains("ice_spikes")) 
            return new BiomeCharacter(id, "Karlı Düzlükler", "Soğuk, sessiz. Bir köy olsa iyi olur.", 0.1f);
        if (lower.contains("badlands")) 
            return new BiomeCharacter(id, "Kızıl Badlands", "Kızıl kayalıklar ve altın. Western havası. Gizli define burada.", 0.4f);
        if (lower.contains("windswept_hills")) 
            return new BiomeCharacter(id, "Rüzgarlı Tepeler", "Dramatik kayalıklar. Altın maden yakın olabilir.", 0.3f);
            
        return new BiomeCharacter(id, "Keşfedilmemiş Alan", "Bilinmeyenin heyecanı. Ne çıkacağı belli değil.", 0.2f);
    }
    
    public static String toPromptString(ServerPlayer player) {
        BiomeCharacter bc = characterize(player);
        return "[BİYOM KARAKTERI - " + bc.turkishName() + "]: " + bc.emotionalNote();
    }
}
