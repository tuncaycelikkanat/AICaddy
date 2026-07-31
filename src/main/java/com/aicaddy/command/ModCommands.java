package com.aicaddy.command;

import com.aicaddy.ExampleMod;
import com.aicaddy.ai.AiBrainManager;
import com.aicaddy.ai.memory.PlayerMemoryStore;
import com.aicaddy.ai.prompt.SharedPromptRules;
import com.aicaddy.ai.tts.TtsManager;
import com.aicaddy.ai.config.ConfigManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

public final class ModCommands {
    private ModCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /arkadas <mesaj> — Talk to companion (Primary command)
            dispatcher.register(Commands.literal("arkadas")
                    .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                            .executes(context -> {
                                String mesaj = StringArgumentType.getString(context, "mesaj");
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
                                        Component.literal("§a💬 [" + player.getScoreboardName() + "]: §f" + mesaj),
                                        false
                                );
                                player.displayClientMessage(
                                        Component.literal("§7⏳ AI Arkadaş düşünüyor..."),
                                        true
                                );
                                AiBrainManager.processAndRespond(player, mesaj);
                                return 1;
                            }))
            );

            // /yoldas <mesaj> — Alias
            dispatcher.register(Commands.literal("yoldas")
                    .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                            .executes(context -> {
                                String mesaj = StringArgumentType.getString(context, "mesaj");
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
                                        Component.literal("§a💬 [" + player.getScoreboardName() + "]: §f" + mesaj),
                                        false
                                );
                                player.displayClientMessage(
                                        Component.literal("§7⏳ AI Arkadaş düşünüyor..."),
                                        true
                                );
                                AiBrainManager.processAndRespond(player, mesaj);
                                return 1;
                            }))
            );

            // /kedi <mesaj> — Legacy alias
            dispatcher.register(Commands.literal("kedi")
                    .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                            .executes(context -> {
                                String mesaj = StringArgumentType.getString(context, "mesaj");
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ExampleMod.SERVER_INSTANCE.getPlayerList().broadcastSystemMessage(
                                        Component.literal("§a💬 [" + player.getScoreboardName() + "]: §f" + mesaj),
                                        false
                                );
                                player.displayClientMessage(
                                        Component.literal("§7⏳ AI Arkadaş düşünüyor..."),
                                        true
                                );
                                AiBrainManager.processAndRespond(player, mesaj);
                                return 1;
                            }))
            );

            // /aiarkadas & /aiarkadas — Control commands
            dispatcher.register(Commands.literal("aiarkadas")
                    .then(Commands.literal("unut").executes(context -> {
                        ServerPlayer p = context.getSource().getPlayerOrException();
                        AiBrainManager.clearHistory(p);
                        context.getSource().sendSuccess(() -> Component.literal("§e🤝 [AI Arkadaş]: §fTamam, ne konuşmuşsak sildim. Sıfırdan başlıyoruz!"), false);
                        return 1;
                    }))
            );

            dispatcher.register(Commands.literal("aikedi")
                    .then(Commands.literal("unut").executes(context -> {
                        ServerPlayer p = context.getSource().getPlayerOrException();
                        AiBrainManager.clearHistory(p);
                        context.getSource().sendSuccess(() -> Component.literal("§e🤝 [AI Arkadaş]: §fTamam, ne konuşmuşsak sildim. Sıfırdan başlıyoruz!"), false);
                        return 1;
                    }))
                    .then(Commands.literal("hafiza").executes(context -> {
                        ServerPlayer p = context.getSource().getPlayerOrException();
                        int score = PlayerMemoryStore.getAffinityScore(p.getUUID());
                        String tier = PlayerMemoryStore.getAffinityTierLabel(score);
                        List<String> ms = PlayerMemoryStore.getMajorMilestones(p.getUUID());
                        context.getSource().sendSuccess(() -> Component.literal("§e🤝 [AI Arkadaş Hafıza] §aSamimiyet Seviyesi: §f" + tier), false);
                        if (!ms.isEmpty()) {
                            context.getSource().sendSuccess(() -> Component.literal("§e🤝 [Kalıcı Başarılar] §f" + String.join(", ", ms)), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal("§7🤝 [Kalıcı Başarılar] §fHenüz kaydedilmiş büyük bir başarımız yok."), false);
                        }
                        return 1;
                    }))
                    .then(Commands.literal("ses")
                            .then(Commands.literal("aç").executes(context -> {
                                TtsManager.setTtsEnabled(true);
                                context.getSource().sendSuccess(() -> Component.literal("§e🤝 [AI Arkadaş]: §f🔊 Sesli konuşma açıldı!"), false);
                                return 1;
                            }))
                            .then(Commands.literal("kapat").executes(context -> {
                                TtsManager.setTtsEnabled(false);
                                context.getSource().sendSuccess(() -> Component.literal("§e🤝 [AI Arkadaş]: §f🔇 Sesli konuşma kapatıldı (sadece metin)."), false);
                                return 1;
                            }))
                    )
                    .then(Commands.literal("ses_servisi")
                            .then(Commands.literal("groq_whisper").executes(context -> {
                                ConfigManager.saveActiveSttProvider("groq_whisper");
                                context.getSource().sendSuccess(() -> Component.literal("§e🎙️ [STT Servisi]: §aGroq Whisper API §faktif edildi."), false);
                                return 1;
                            }))
                            .then(Commands.literal("vosk_local").executes(context -> {
                                ConfigManager.saveActiveSttProvider("vosk_local");
                                context.getSource().sendSuccess(() -> Component.literal("§e🎙️ [STT Servisi]: §aVosk Local STT §faktif edildi."), false);
                                return 1;
                            }))
                    )
                    .then(Commands.literal("mod")
                            .then(Commands.literal("serbest").executes(context -> {
                                SharedPromptRules.setActiveMode(SharedPromptRules.PromptMode.SERBEST);
                                context.getSource().sendSuccess(() -> Component.literal("§e📝 [AI Arkadaş Prompt Modu]: §aSERBEST §f(Varsayılan dengeli macera arkadaşı) aktif edildi."), false);
                                return 1;
                            }))
                            .then(Commands.literal("taktiksel").executes(context -> {
                                SharedPromptRules.setActiveMode(SharedPromptRules.PromptMode.TAKTIKSEL);
                                context.getSource().sendSuccess(() -> Component.literal("§e📝 [AI Arkadaş Prompt Modu]: §aTAKTIKSEL §f(Kısa, keskin, askeri hayatta kalma odaklı) aktif edildi."), false);
                                return 1;
                            }))
                            .then(Commands.literal("eglenceli").executes(context -> {
                                SharedPromptRules.setActiveMode(SharedPromptRules.PromptMode.EGLENCELI);
                                context.getSource().sendSuccess(() -> Component.literal("§e📝 [AI Arkadaş Prompt Modu]: §aEGLENCELI §f(Mizahi, oyuncu argosu ve samimi kanka dili) aktif edildi."), false);
                                return 1;
                            }))
                    )
            );
        });
    }
}
