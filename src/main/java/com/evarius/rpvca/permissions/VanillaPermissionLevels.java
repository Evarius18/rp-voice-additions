package com.evarius.rpvca.permissions;

import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Central adapter for Minecraft 1.21.11's level-based permission API. */
public final class VanillaPermissionLevels {
    private VanillaPermissionLevels() {
    }

    public static boolean has(ServerCommandSource source, int level) {
        return has(source.getPermissions(), level);
    }

    public static boolean has(ServerPlayerEntity player, int level) {
        return has(player.getPermissions(), level);
    }

    private static boolean has(PermissionPredicate permissions, int level) {
        return permissions.hasPermission(new Permission.Level(PermissionLevel.fromLevel(level)));
    }
}
