package com.example.item;

import com.example.entity.AiPetEntity;
import com.example.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class AiPetSummonerItem extends Item {

	public AiPetSummonerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (!level.isClientSide && player != null) {
			BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
			spawnAiPet(level, player, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) {
			spawnAiPet(level, player, player.getX(), player.getY(), player.getZ());
			return InteractionResultHolder.success(stack);
		}
		return InteractionResultHolder.consume(stack);
	}

	private void spawnAiPet(Level level, Player player, double x, double y, double z) {
		AiPetEntity pet = ModEntities.AI_PET.create(level);
		if (pet != null) {
			pet.moveTo(x, y, z, player.getYRot(), 0.0F);
			pet.tame(player); // Automatically tame pet for the owner
			level.addFreshEntity(pet);
			player.sendSystemMessage(Component.literal("§e🐱 AI Kedi çağrıldı ve artık seni sürekli takip ediyor!"));
		}
	}
}
