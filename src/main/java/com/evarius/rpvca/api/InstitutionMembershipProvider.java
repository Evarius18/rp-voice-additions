package com.evarius.rpvca.api;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

/**
 * Optional server-side SPI for institution mods.
 *
 * <p>Providers return immutable IDs, names or types used by configured radio mappings.
 * RP-VCA never needs access to another mod's persistence classes.</p>
 */
@FunctionalInterface
public interface InstitutionMembershipProvider {
    Set<String> membershipKeys(ServerPlayerEntity player);
}
