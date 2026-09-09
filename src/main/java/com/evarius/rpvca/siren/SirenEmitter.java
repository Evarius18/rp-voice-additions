package com.evarius.rpvca.siren;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Runtime-only resolved siren position; never persisted or exposed through the API. */
public record SirenEmitter(UUID sirenId, ServerLevel world, BlockPos pos) {
}
