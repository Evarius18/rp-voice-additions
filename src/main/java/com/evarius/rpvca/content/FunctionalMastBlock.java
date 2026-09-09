package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.state.TowerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A mast top that contributes to one server-side communication network. */
public final class FunctionalMastBlock extends Block {
    private final TowerRegistry.TowerType towerType;

    public FunctionalMastBlock(Properties settings, TowerRegistry.TowerType towerType) {
        super(settings);
        this.towerType = towerType;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        if (world instanceof ServerLevel serverWorld && RpVoiceServices.get() != null) {
            RpVoiceServices.get().towers().add(serverWorld.dimension(), pos, towerType);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
        if (!world.getBlockState(pos).is(this) && RpVoiceServices.get() != null) {
            RpVoiceServices.get().towers().remove(world.dimension(), pos);
        }
    }
}
