package com.aicaddy.registry;

import com.aicaddy.ExampleMod;
import com.aicaddy.item.AiPetSummonerItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ModItems {

	// Register custom summoner item.
	public static final Item AI_PET_SUMMONER = Registry.register(
			BuiltInRegistries.ITEM,
			ExampleMod.id("ai_pet_summoner"),
			new AiPetSummonerItem(new Item.Properties().stacksTo(1))
	);

	// Register custom creative mode tab for AI Companion items.
	public static final CreativeModeTab AI_PET_TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			ExampleMod.id("ai_pet_tab"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.modid.ai_pet_tab"))
					.icon(() -> new ItemStack(AI_PET_SUMMONER))
					.displayItems((itemDisplayParameters, output) -> {
						output.accept(AI_PET_SUMMONER);
					})
					.build()
	);

	public static void register() {
		// Triggers static class initialization to register items and tabs.
	}
}
