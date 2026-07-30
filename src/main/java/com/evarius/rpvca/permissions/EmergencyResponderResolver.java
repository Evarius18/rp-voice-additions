package com.evarius.rpvca.permissions;

import com.evarius.rpvca.api.RpVcaApi;
import com.evarius.rpvca.config.EmergencyConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Combines vanilla teams and optional institution providers without knowing their mods. */
public final class EmergencyResponderResolver {
    public boolean isEligible(ServerPlayerEntity player, EmergencyConfig.Number definition) {
        Set<String> required = new LinkedHashSet<>();
        if (definition.responderTeam != null && !definition.responderTeam.isBlank()) {
            required.add(normalize(definition.responderTeam));
        }
        if (definition.responderKeys != null) {
            definition.responderKeys.stream().filter(java.util.Objects::nonNull)
                    .map(EmergencyResponderResolver::normalize).filter(value -> !value.isBlank())
                    .forEach(required::add);
        }
        if (required.isEmpty()) return true;
        Set<String> memberships = resolveMembershipKeys(player);
        return required.stream().anyMatch(memberships::contains);
    }

    public Set<String> resolveMembershipKeys(ServerPlayerEntity player) {
        Set<String> result = new LinkedHashSet<>();
        if (player.getScoreboardTeam() != null) {
            result.add(normalize(player.getScoreboardTeam().getName()));
        }
        RpVcaApi.institutionMembershipKeys(player).stream()
                .map(EmergencyResponderResolver::normalize).filter(value -> !value.isBlank())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
