package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DeviceActionPayload(String action, String value) implements CustomPacketPayload {
    public static final Type<DeviceActionPayload> ID = new Type<>(RpVoiceAddon.id("device_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), DeviceActionPayload::action,
            ByteBufCodecs.stringUtf8(256), DeviceActionPayload::value,
            DeviceActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
