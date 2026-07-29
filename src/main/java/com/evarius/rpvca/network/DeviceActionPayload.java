package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record DeviceActionPayload(String action, String value) implements CustomPayload {
    public static final Id<DeviceActionPayload> ID = new Id<>(RpVoiceAddon.id("device_action"));
    public static final PacketCodec<RegistryByteBuf, DeviceActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32), DeviceActionPayload::action,
            PacketCodecs.string(256), DeviceActionPayload::value,
            DeviceActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
