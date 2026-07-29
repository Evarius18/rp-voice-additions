package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class CellTowerBlock extends Block {
    public CellTowerBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world instanceof ServerWorld serverWorld && RpVoiceServices.get() != null) {
            RpVoiceServices.get().towers().add(serverWorld.getRegistryKey(), pos);
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);
        if (!world.getBlockState(pos).isOf(this) && RpVoiceServices.get() != null) {
            RpVoiceServices.get().towers().remove(world.getRegistryKey(), pos);
        }
    }
}
