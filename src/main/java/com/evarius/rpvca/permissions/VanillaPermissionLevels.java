package com.evarius.rpvca.permissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

/** Central adapter for Minecraft 1.21.11's level-based permission API. */
public final class VanillaPermissionLevels {
    private VanillaPermissionLevels() {
    }

    public static boolean has(CommandSourceStack source, int level) {
        return has(source.permissions(), level);
    }

    public static boolean has(ServerPlayer player, int level) {
        return has(player.permissions(), level);
    }

    private static boolean has(PermissionSet permissions, int level) {
        return permissions.hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
}
