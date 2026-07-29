package com.evarius.rpvca.integration.terranexus;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.compatibility.PhoneIntegration;
import com.evarius.rpvca.config.CompatibilityConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reflection keeps TerraNexus genuinely optional. No TerraNexus class is linked while the
 * other mod is absent, and an incompatible future API degrades to the normal RP-VCA behavior.
 */
public final class TerraNexusIntegration implements PhoneIntegration {
    private volatile boolean runtimeAvailable;
    private final CompatibilityConfig config;
    private final Method openPhone;
    private final Method institutionStateGet;
    private final Method institutionsForMember;
    private final Method institutionId;
    private final Method institutionName;
    private final Method institutionType;

    private TerraNexusIntegration(boolean available, CompatibilityConfig config, Method openPhone,
                                  Method institutionStateGet, Method institutionsForMember,
                                  Method institutionId, Method institutionName, Method institutionType) {
        this.runtimeAvailable = available;
        this.config = config;
        this.openPhone = openPhone;
        this.institutionStateGet = institutionStateGet;
        this.institutionsForMember = institutionsForMember;
        this.institutionId = institutionId;
        this.institutionName = institutionName;
        this.institutionType = institutionType;
    }

    public static TerraNexusIntegration create(CompatibilityConfig config) {
        try {
            Class<?> phoneScreen = Class.forName("net.evarius.terranexus.phone.PhoneScreen");
            Class<?> institutionState = Class.forName("net.evarius.terranexus.institution.InstitutionState");
            Class<?> institution = Class.forName("net.evarius.terranexus.institution.Institution");
            return new TerraNexusIntegration(true, config,
                    phoneScreen.getMethod("open", ServerPlayerEntity.class),
                    institutionState.getMethod("get", net.minecraft.server.MinecraftServer.class),
                    institutionState.getMethod("forMember", java.util.UUID.class),
                    institution.getMethod("id"), institution.getMethod("name"), institution.getMethod("type"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            RpVoiceAddon.LOGGER.warn("TerraNexus wurde gefunden, aber die Integrations-API ist nicht kompatibel", exception);
            return unavailable();
        }
    }

    public static TerraNexusIntegration unavailable() {
        return new TerraNexusIntegration(false, new CompatibilityConfig(), null, null, null, null, null, null);
    }

    public boolean available() {
        return runtimeAvailable;
    }

    public boolean phoneAppEnabled() {
        return runtimeAvailable && config.terraNexusPhoneAppEnabled;
    }

    public boolean institutionPermissionsEnabled() {
        return runtimeAvailable && config.terraNexusInstitutionPermissionsEnabled;
    }

    @Override
    public String id() {
        return "terranexus";
    }

    @Override
    public String title() {
        return "TerraNexus";
    }

    @Override
    public boolean available(ServerPlayerEntity player) {
        return phoneAppEnabled();
    }

    @Override
    public boolean open(ServerPlayerEntity player) {
        if (!phoneAppEnabled()) {
            return false;
        }
        try {
            openPhone.invoke(null, player);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            RpVoiceAddon.LOGGER.error("TerraNexus-Handy-App konnte nicht geöffnet werden", exception);
            runtimeAvailable = false;
            return false;
        }
    }

    public Set<String> institutionKeys(ServerPlayerEntity player) {
        if (!institutionPermissionsEnabled()) {
            return Set.of();
        }
        try {
            Object state = institutionStateGet.invoke(null, player.getServer());
            Object result = institutionsForMember.invoke(state, player.getUuid());
            if (!(result instanceof List<?> institutions)) {
                return Set.of();
            }
            Set<String> keys = new LinkedHashSet<>();
            for (Object institution : institutions) {
                add(keys, institutionId.invoke(institution));
                add(keys, institutionName.invoke(institution));
                add(keys, institutionType.invoke(institution));
            }
            return Set.copyOf(keys);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            RpVoiceAddon.LOGGER.error("TerraNexus-Institutionsmitgliedschaften konnten nicht gelesen werden", exception);
            runtimeAvailable = false;
            return Set.of();
        }
    }

    private static void add(Set<String> values, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            values.add(text);
        }
    }
}
