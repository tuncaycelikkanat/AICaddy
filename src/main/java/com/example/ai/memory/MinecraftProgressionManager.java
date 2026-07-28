package com.example.ai.memory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Progression memory manager that tracks the player's overall journey in Minecraft.
 * Provides natural conversational context about current stage and next milestones
 * without forcing robotic bullet-point lists.
 */
public class MinecraftProgressionManager {

	public enum ProgressionStage {
		WOOD_AGE("Başlangıç & Tahta Çağı", "Oyuncu henüz yolun başında. Doğal hedefler: Ağaç toplayıp çalışma masası ve ilk taş kazmayı yapmak."),
		STONE_AGE("Taş Çağı & Temel Araçlar", "Oyuncu taş araçları üretti. Doğal hedefler: Fırın yapmak, Y=16 katında demir bulup eritmek ve tahta aletleri bırakmak."),
		IRON_AGE("Demir Çağı & Zırhla Güvence", "Oyuncu demire ulaştı. Doğal hedefler: Demir Kazma, Kalkan ve tam demir zırh setini tamamlamak; Y=-58 katına inmeye hazırlanmak."),
		DIAMOND_AGE("Elmas & Büyü Çağı", "Oyuncuda demir/elmas ekipman var. Doğal hedefler: Y=-58 katında elmas çıkarmak, obsidyen toplayıp Büyü Masası (Enchanting Table) yapmak."),
		NETHER_AGE("Nether & Ejderha Hazırlığı", "Oyuncu Nether geçidini açtı. Doğal hedefler: Nether Kalesinde Blaze Çubuğu toplamak ve Piglinlerle takas yapıp Ender İncisi almak."),
		DRAGON_AGE("Ender Ejderhası Avı", "Oyuncu Ender Gözlerine sahip veya End dünyasında. Doğal hedef: Kaleyi bulup Ejderhayı yatak patlatma stratejisiyle avlamak.");

		private final String stageName;
		private final String naturalGoalDescription;

		ProgressionStage(String stageName, String naturalGoalDescription) {
			this.stageName = stageName;
			this.naturalGoalDescription = naturalGoalDescription;
		}

		public String getStageName() {
			return stageName;
		}

		public String getNaturalGoalDescription() {
			return naturalGoalDescription;
		}
	}

	/**
	 * Automatically evaluates the player's inventory and dimension to determine their progression stage.
	 */
	public static ProgressionStage evaluatePlayerStage(ServerPlayer player) {
		String dimPath = player.serverLevel().dimension().location().getPath();
		boolean inNether = dimPath.contains("nether");
		boolean inEnd = dimPath.contains("end");

		boolean hasDiamond = false;
		boolean hasIron = false;
		boolean hasStone = false;
		boolean hasEnderEye = false;

		for (ItemStack stack : player.getInventory().items) {
			if (stack.isEmpty()) continue;

			if (stack.is(Items.ENDER_EYE) || stack.is(Items.ENDER_PEARL) || stack.is(Items.BLAZE_ROD)) {
				hasEnderEye = true;
			}
			if (stack.is(Items.DIAMOND) || stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.DIAMOND_SWORD)) {
				hasDiamond = true;
			}
			if (stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_PICKAXE) || stack.is(Items.IRON_SWORD) || stack.is(Items.SHIELD)) {
				hasIron = true;
			}
			if (stack.is(Items.STONE_PICKAXE) || stack.is(Items.STONE_SWORD) || stack.is(Items.FURNACE)) {
				hasStone = true;
			}
		}

		if (inEnd || hasEnderEye) {
			return ProgressionStage.DRAGON_AGE;
		} else if (inNether) {
			return ProgressionStage.NETHER_AGE;
		} else if (hasDiamond) {
			return ProgressionStage.DIAMOND_AGE;
		} else if (hasIron) {
			return ProgressionStage.IRON_AGE;
		} else if (hasStone) {
			return ProgressionStage.STONE_AGE;
		} else {
			return ProgressionStage.WOOD_AGE;
		}
	}

	/**
	 * Generates conversational progression memory prompt for the AI.
	 */
	public static String getProgressionMemoryPrompt(ServerPlayer player) {
		ProgressionStage stage = evaluatePlayerStage(player);
		StringBuilder sb = new StringBuilder();
		sb.append("[GELİŞİM HAFIZASI VE OYUN AKIŞI]:\n")
				.append("- Oyuncunun Mevcut Aşaması: ").append(stage.getStageName()).append("\n")
				.append("- Sıradaki Doğal Oyun Akışı: ").append(stage.getNaturalGoalDescription()).append("\n")
				.append("- ÖNEMLİ KURAL: Cevabında KESİNLİKLE 'Adım 1, Adım 2' gibi robotik maddeler/listeler kullanma! ")
				.append("Sanki nerede olduğunuzu ve sıradaki amacınızı çok iyi bilen doğal bir arkadaş gibi konuşarak oyuncuyu esprili şekilde bu akışa yönlendir.\n");
		return sb.toString();
	}
}
