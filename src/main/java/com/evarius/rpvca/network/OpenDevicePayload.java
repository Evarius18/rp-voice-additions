package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record OpenDevicePayload(String device) implements CustomPayload {
    public static final Id<OpenDevicePayload> ID = new Id<>(RpVoiceAddon.id("open_device"));
    public static final PacketCodec<RegistryByteBuf, OpenDevicePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(16), OpenDevicePayload::device, OpenDevicePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
