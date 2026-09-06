package com.evarius.rpvca.permissions;

import com.evarius.rpvca.compatibility.CompatibilityManager;
import com.evarius.rpvca.config.CompatibilityConfig;
import com.evarius.rpvca.config.RadioConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Combines vanilla team access with optional institution-derived grants. */
public final class RadioPermissionResolver {
    private final CompatibilityManager compatibility;
    private final CompatibilityConfig config;
    private final Map<UUID, MembershipCache> membershipCache = new ConcurrentHashMap<>();

    public RadioPermissionResolver(CompatibilityManager compatibility, CompatibilityConfig config) {
        this.compatibility = compatibility;
        this.config = config;
    }

    public boolean hasAccess(ServerPlayerEntity player, RadioConfig.Channel channel) {
        if (channel.requiredTeam == null || channel.requiredTeam.isBlank()) {
            return true;
        }
        if (player.getScoreboardTeam() != null
                && player.getScoreboardTeam().getName().equalsIgnoreCase(channel.requiredTeam)) {
            return true;
        }
        String requestedChannel = channel.id.toLowerCase(Locale.ROOT);
        Set<String> memberships = memberships(player);
        for (Map.Entry<String, List<String>> mapping : config.institutionRadioChannels.entrySet()) {
            boolean membershipMatches = memberships.stream().anyMatch(value -> value.equalsIgnoreCase(mapping.getKey()));
            if (membershipMatches && mapping.getValue().stream()
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(requestedChannel))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> memberships(ServerPlayerEntity player) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        if (player.getScoreboardTeam() != null) {
            resolved.add(player.getScoreboardTeam().getName().trim().toLowerCase(Locale.ROOT));
        }
        long now = System.currentTimeMillis();
        MembershipCache cached = membershipCache.get(player.getUuid());
        if (cached != null && cached.expiresAt() > now) {
            resolved.addAll(cached.keys());
            return Set.copyOf(resolved);
        }
        Set<String> keys = compatibility.terraNexusInstitutionKeys(player).stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        membershipCache.put(player.getUuid(), new MembershipCache(keys, now + 2_000L));
        resolved.addAll(keys);
        return Set.copyOf(resolved);
    }

    private record MembershipCache(Set<String> keys, long expiresAt) {
    }
}
