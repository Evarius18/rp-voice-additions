package com.evarius.rpvca.siren;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Runtime-only resolved siren position; never persisted or exposed through the API. */
public record SirenEmitter(UUID sirenId, ServerWorld world, BlockPos pos) {
}
