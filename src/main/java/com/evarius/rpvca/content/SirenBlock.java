package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A physical siren endpoint that can be commissioned from a controller's link mode. */
public final class SirenBlock extends Block {
    private final String variant;

    public SirenBlock(Properties settings, String variant) {
        super(settings);
        this.variant = variant;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        RpVoiceServices services = RpVoiceServices.get();
        if (world instanceof ServerLevel serverWorld && services != null) {
            services.sirens().registerSiren(serverWorld.dimension(), pos, variant);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                 Player user, BlockHitResult hit) {
        RpVoiceServices services = RpVoiceServices.get();
        if (!(world instanceof ServerLevel serverWorld) || !(user instanceof ServerPlayer player)
                || services == null || !services.sirens().hasPendingLink(player.getUUID())) {
            return InteractionResult.PASS;
        }
        services.sirens().completeLink(player, serverWorld.dimension(), pos);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
        RpVoiceServices services = RpVoiceServices.get();
        if (!world.getBlockState(pos).is(this) && services != null) {
            services.sirens().removeSiren(world.dimension(), pos);
        }
    }
}
