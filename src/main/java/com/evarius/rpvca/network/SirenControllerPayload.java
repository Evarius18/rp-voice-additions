package com.evarius.rpvca.network;

import com.evarius.rpvca.RpVoiceAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded server-authored snapshot for the non-container controller screen. */
public record SirenControllerPayload(String json) implements CustomPacketPayload {
    public static final Type<SirenControllerPayload> ID = new Type<>(RpVoiceAddon.id("siren_controller"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SirenControllerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65_536), SirenControllerPayload::json, SirenControllerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
