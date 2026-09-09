package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenDevicePayload(String device) implements CustomPacketPayload {
    public static final Type<OpenDevicePayload> ID = new Type<>(RpVoiceAddon.id("open_device"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDevicePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), OpenDevicePayload::device, OpenDevicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
