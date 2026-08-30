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

/** A physical siren endpoint that can be commissioned from a controller's link mode. */
public final class SirenBlock extends Block {
    private final String variant;

    public SirenBlock(Settings settings, String variant) {
        super(settings);
        this.variant = variant;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        RpVoiceServices services = RpVoiceServices.get();
        if (world instanceof ServerWorld serverWorld && services != null) {
            services.sirens().registerSiren(serverWorld.getRegistryKey(), pos, variant);
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity user, BlockHitResult hit) {
        RpVoiceServices services = RpVoiceServices.get();
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)
                || services == null || !services.sirens().hasPendingLink(player.getUuid())) {
            return ActionResult.PASS;
        }
        services.sirens().completeLink(player, serverWorld.getRegistryKey(), pos);
        return ActionResult.SUCCESS_SERVER;
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);
        RpVoiceServices services = RpVoiceServices.get();
        if (!world.getBlockState(pos).isOf(this) && services != null) {
            services.sirens().removeSiren(world.getRegistryKey(), pos);
        }
    }
}
