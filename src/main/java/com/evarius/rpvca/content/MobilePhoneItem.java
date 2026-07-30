package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.network.CommunicationNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class MobilePhoneItem extends Item {
    public MobilePhoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player && RpVoiceServices.get() != null
                && RpVoiceServices.get().deviceItems().shouldOpenRpVcaScreen(user.getStackInHand(hand))) {
            CommunicationNetworking.openDevice(player, "phone");
        }
        return ActionResult.SUCCESS;
    }
}
