package com.evarius.rpvca.compatibility;

import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side extension point for optional applications shown in the phone launcher. */
public interface PhoneIntegration {
    String id();

    String title();

    boolean available(ServerPlayerEntity player);

    boolean open(ServerPlayerEntity player);
}
