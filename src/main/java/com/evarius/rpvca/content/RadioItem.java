package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.network.CommunicationNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class RadioItem extends Item {
    public RadioItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player && RpVoiceServices.get() != null) {
            CommunicationNetworking.openDevice(player, "radio");
        }
        return ActionResult.SUCCESS;
    }
}
