package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Bounded server-authored snapshot for the non-container controller screen. */
public record SirenControllerPayload(String json) implements CustomPayload {
    public static final Id<SirenControllerPayload> ID = new Id<>(RpVoiceAddon.id("siren_controller"));
    public static final PacketCodec<RegistryByteBuf, SirenControllerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(65_536), SirenControllerPayload::json, SirenControllerPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
