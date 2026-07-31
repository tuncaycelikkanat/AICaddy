package com.aicaddy.registry;

import com.aicaddy.ExampleMod;
import com.aicaddy.entity.AiPetEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cat;

public class ModEntities {

	public static final EntityType<AiPetEntity> AI_PET = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			ExampleMod.id("ai_pet"),
			EntityType.Builder.of(AiPetEntity::new, MobCategory.CREATURE)
					.sized(0.6F, 0.7F)
					.fireImmune() // Immune to fire and lava
					.build("ai_pet")
	);

	public static void register() {
		FabricDefaultAttributeRegistry.register(AI_PET, Cat.createAttributes());
	}
}
