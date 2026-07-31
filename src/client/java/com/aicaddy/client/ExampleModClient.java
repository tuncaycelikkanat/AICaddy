package com.aicaddy.client;

import com.aicaddy.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.CatRenderer;

public class ExampleModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register AI Pet renderer using default cat model.
		EntityRendererRegistry.register(ModEntities.AI_PET, CatRenderer::new);
	}
}