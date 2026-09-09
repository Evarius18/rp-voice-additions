package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StatusPayload(String json) implements CustomPacketPayload {
    public static final Type<StatusPayload> ID = new Type<>(RpVoiceAddon.id("status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16_384), StatusPayload::json, StatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
