package com.evarius.rpvca.api;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional server-side SPI for institution mods.
 *
 * <p>Providers return immutable IDs, names or types used by configured radio mappings.
 * RP-VCA never needs access to another mod's persistence classes.</p>
 */
@FunctionalInterface
public interface InstitutionMembershipProvider {
    Set<String> membershipKeys(ServerPlayer player);
}
