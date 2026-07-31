package com.aicaddy.ai.context;

import net.minecraft.server.level.ServerPlayer;

public final class SmartContextBuilder {
    private SmartContextBuilder() {}
    
    public record ContextPayload(
        String mcContext,              // MinecraftContextProvider çıktısı
        GamePhaseAnalyzer.GamePhase gamePhase,
        String gamePhaseDesc,
        ThreatAssessor.ThreatResult threat,
        BiomeCharacterizer.BiomeCharacter biomeCharacter,
        String structureAndPoi
    ) {
        // Prompt'a enjekte edilecek tam string
        public String toPromptBlock() {
            StringBuilder sb = new StringBuilder();
            sb.append("[OYUNUN FAZI]: ").append(gamePhaseDesc).append("\n");
            sb.append("[BİYOM KARAKTERI - ").append(biomeCharacter.turkishName()).append("]: ")
              .append(biomeCharacter.emotionalNote()).append("\n");
            sb.append("[TEHDİT SEVİYESİ]: ").append(threat.description()).append("\n");
            sb.append("[OYUN DURUMU - Şu an nerede, ne var, ne oluyor]:\n").append(mcContext).append("\n");
            return sb.toString();
        }
        
        // Proaktif konuşmalar için kompakt versiyon
        public String toCompactString() {
            return "Oyun fazı: " + gamePhaseDesc.split(" — ")[0] + 
                   ". Biyom: " + biomeCharacter.turkishName() +
                   ". Tehdit: " + threat.description().split(" \\(")[0];
        }
    }
    
    public static ContextPayload build(ServerPlayer player) {
        GamePhaseAnalyzer.GamePhase phase = GamePhaseAnalyzer.analyze(player);
        ThreatAssessor.ThreatResult threat = ThreatAssessor.assess(player);
        BiomeCharacterizer.BiomeCharacter biome = BiomeCharacterizer.characterize(player);
        
        // com.aicaddy.ai.MinecraftContextProvider implementation is assumed to exist as requested
        String mcCtx = com.aicaddy.ai.MinecraftContextProvider.getPlayerContext(player);
        
        // StructureAndPoiDetector implementation is assumed to exist as requested
        String structurePoi = StructureAndPoiDetector.getStructureAndPoiSummary(player);
        
        return new ContextPayload(
            mcCtx, phase, GamePhaseAnalyzer.toPromptDescription(phase),
            threat, biome, structurePoi
        );
    }
}
