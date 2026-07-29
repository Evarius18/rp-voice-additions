package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record StatusPayload(String json) implements CustomPayload {
    public static final Id<StatusPayload> ID = new Id<>(RpVoiceAddon.id("status"));
    public static final PacketCodec<RegistryByteBuf, StatusPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(16_384), StatusPayload::json, StatusPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
