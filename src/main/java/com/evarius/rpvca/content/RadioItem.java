package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.network.CommunicationNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class RadioItem extends Item {
    public RadioItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!world.isClientSide() && user instanceof ServerPlayer player && RpVoiceServices.get() != null) {
            CommunicationNetworking.openDevice(player, "radio");
        }
        return InteractionResult.SUCCESS;
    }
}
