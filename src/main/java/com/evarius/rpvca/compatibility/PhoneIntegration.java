package com.evarius.rpvca.compatibility;

import net.minecraft.server.level.ServerPlayer;

/** Server-side extension point for optional applications shown in the phone launcher. */
public interface PhoneIntegration {
    String id();

    String title();

    boolean available(ServerPlayer player);

    boolean open(ServerPlayer player);
}
