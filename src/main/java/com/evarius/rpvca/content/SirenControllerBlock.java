package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Physical commissioning and local operation point for a persistent siren network. */
public final class SirenControllerBlock extends Block {
    public SirenControllerBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        RpVoiceServices services = RpVoiceServices.get();
        if (world instanceof ServerWorld serverWorld && services != null) {
            services.sirens().registerController(serverWorld.getRegistryKey(), pos);
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity user, BlockHitResult hit) {
        RpVoiceServices services = RpVoiceServices.get();
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)
                || services == null) {
            return ActionResult.SUCCESS;
        }
        return services.sirens().openController(player, serverWorld.getRegistryKey(), pos)
                ? ActionResult.SUCCESS_SERVER : ActionResult.FAIL;
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);
        RpVoiceServices services = RpVoiceServices.get();
        if (!world.getBlockState(pos).isOf(this) && services != null) {
            services.sirens().removeController(world.getRegistryKey(), pos);
        }
    }
}
