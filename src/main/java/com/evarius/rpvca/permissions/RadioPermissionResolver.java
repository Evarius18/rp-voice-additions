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
    private final Map<String, List<String>> institutionRadioChannels;
    private final Map<UUID, MembershipCache> membershipCache = new ConcurrentHashMap<>();

    public RadioPermissionResolver(CompatibilityManager compatibility, CompatibilityConfig config) {
        this.compatibility = compatibility;
        this.institutionRadioChannels = new java.util.LinkedHashMap<>();
        config.institutionRadioChannels.forEach((key, channels) -> institutionRadioChannels.put(
                normalizeKey(key), channels.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList()));
    }

    public boolean hasAccess(ServerPlayerEntity player, RadioConfig.Channel channel) {
        if (channel.requiredTeam == null || channel.requiredTeam.isBlank()) {
            return true;
        }
        return hasAccessNormalized(memberships(player), channel);
    }

    boolean hasAccess(Set<String> memberships, RadioConfig.Channel channel) {
        Set<String> normalized = memberships.stream().map(RadioPermissionResolver::normalizeKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return hasAccessNormalized(normalized, channel);
    }

    private boolean hasAccessNormalized(Set<String> memberships, RadioConfig.Channel channel) {
        if (channel.requiredTeam == null || channel.requiredTeam.isBlank()) return true;
        if (memberships.contains(normalizeKey(channel.requiredTeam))) return true;
        String requestedChannel = channel.id.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> mapping : institutionRadioChannels.entrySet()) {
            boolean membershipMatches = memberships.stream().anyMatch(value -> keyMatches(value, mapping.getKey()));
            if (membershipMatches && mapping.getValue().stream()
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(requestedChannel))) {
                return true;
            }
        }
        return false;
    }

    private static boolean keyMatches(String membership, String configuredKey) {
        if (membership.equals(configuredKey)) return true;
        // Descriptive names such as "EFS Stadtwerke" should match the configured
        // institution term. Short codes (FW/RD/SW/AC) remain exact to avoid accidental grants.
        return configuredKey.length() >= 3 && (" " + membership + " ").contains(" " + configuredKey + " ");
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        boolean separator = false;
        for (int offset = 0; offset < lower.length();) {
            int codePoint = lower.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                if (separator && !normalized.isEmpty()) normalized.append(' ');
                normalized.appendCodePoint(codePoint);
                separator = false;
            } else {
                separator = true;
            }
        }
        return normalized.toString();
    }

    private Set<String> memberships(ServerPlayerEntity player) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        if (player.getScoreboardTeam() != null) {
            resolved.add(normalizeKey(player.getScoreboardTeam().getName()));
        }
        long now = System.currentTimeMillis();
        MembershipCache cached = membershipCache.get(player.getUuid());
        if (cached != null && cached.expiresAt() > now) {
            resolved.addAll(cached.keys());
            return Set.copyOf(resolved);
        }
        Set<String> keys = compatibility.terraNexusInstitutionKeys(player).stream()
                .filter(java.util.Objects::nonNull)
                .map(RadioPermissionResolver::normalizeKey)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        membershipCache.put(player.getUuid(), new MembershipCache(keys, now + 2_000L));
        resolved.addAll(keys);
        return Set.copyOf(resolved);
    }

    private record MembershipCache(Set<String> keys, long expiresAt) {
    }
}
