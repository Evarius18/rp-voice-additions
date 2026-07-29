package com.evarius.rpvca.compatibility;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.config.CompatibilityConfig;
import com.evarius.rpvca.integration.terranexus.TerraNexusIntegration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Set;

public final class CompatibilityManager {
    private final TerraNexusIntegration terraNexus;

    public CompatibilityManager(CompatibilityConfig config) {
        boolean loaded = config.terraNexusEnabled && FabricLoader.getInstance().isModLoaded("terranexus");
        terraNexus = loaded ? TerraNexusIntegration.create(config) : TerraNexusIntegration.unavailable();
        if (loaded && terraNexus.available()) {
            RpVoiceAddon.LOGGER.info("TerraNexus-Kompatibilität aktiviert");
        }
    }

    public boolean terraNexusAvailable() {
        return terraNexus.available();
    }

    public List<PhoneIntegration> phoneIntegrations() {
        return terraNexus.phoneAppEnabled() ? List.of(terraNexus) : List.of();
    }

    public Set<String> terraNexusInstitutionKeys(ServerPlayerEntity player) {
        return terraNexus.institutionKeys(player);
    }
}
