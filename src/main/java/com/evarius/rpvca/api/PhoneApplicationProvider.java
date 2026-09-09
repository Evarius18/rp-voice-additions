package com.evarius.rpvca.api;

import net.minecraft.server.level.ServerPlayer;

/** Optional server-side application exposed in RP-VCA's phone launcher. */
public interface PhoneApplicationProvider {
    String title();
    boolean available(ServerPlayer player);
    boolean open(ServerPlayer player);
}
