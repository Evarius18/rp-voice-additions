package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.network.CommunicationNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class MobilePhoneItem extends Item {
    public MobilePhoneItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!world.isClientSide() && user instanceof ServerPlayer player && RpVoiceServices.get() != null
                && RpVoiceServices.get().deviceItems().shouldOpenRpVcaScreen(user.getItemInHand(hand))) {
            CommunicationNetworking.openDevice(player, "phone");
        }
        return InteractionResult.SUCCESS;
    }
}
