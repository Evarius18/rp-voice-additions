package com.evarius.rpvca.permissions;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Central adapter for Minecraft 1.21.9's level-based permission API. */
public final class VanillaPermissionLevels {
    private VanillaPermissionLevels() {
    }

    public static boolean has(ServerCommandSource source, int level) {
        return source.hasPermissionLevel(level);
    }

    public static boolean has(ServerPlayerEntity player, int level) {
        return player.hasPermissionLevel(level);
    }
}
