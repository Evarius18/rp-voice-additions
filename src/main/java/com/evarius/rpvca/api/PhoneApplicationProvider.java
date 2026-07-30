package com.evarius.rpvca.api;

import net.minecraft.server.network.ServerPlayerEntity;

/** Optional server-side application exposed in RP-VCA's phone launcher. */
public interface PhoneApplicationProvider {
    String title();
    boolean available(ServerPlayerEntity player);
    boolean open(ServerPlayerEntity player);
}
