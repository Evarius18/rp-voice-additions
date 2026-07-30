package com.evarius.rpvca.compatibility;

import com.evarius.rpvca.config.CompatibilityConfig;
import com.evarius.rpvca.api.PhoneApplicationProvider;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

public final class CompatibilityManager {
    private final boolean terraNexusEnabled;
    private final boolean phoneAppsEnabled;
    private final boolean institutionPermissionsEnabled;

    public CompatibilityManager(CompatibilityConfig config) {
        institutionPermissionsEnabled = config.terraNexusInstitutionPermissionsEnabled;
        terraNexusEnabled = config.terraNexusEnabled;
        phoneAppsEnabled = config.terraNexusPhoneAppEnabled;
    }

    public boolean terraNexusAvailable() {
        return terraNexusEnabled && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("terranexus");
    }

    public java.util.Map<String, PhoneApplicationProvider> phoneIntegrations() {
        return terraNexusEnabled && phoneAppsEnabled
                ? com.evarius.rpvca.api.RpVcaApi.phoneApplications() : java.util.Map.of();
    }

    public Set<String> terraNexusInstitutionKeys(ServerPlayerEntity player) {
        if (!institutionPermissionsEnabled) {
            return Set.of();
        }
        return com.evarius.rpvca.api.RpVcaApi.institutionMembershipKeys(player);
    }
}
