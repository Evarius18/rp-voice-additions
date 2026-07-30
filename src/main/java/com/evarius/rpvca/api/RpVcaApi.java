package com.evarius.rpvca.api;

import com.evarius.rpvca.RpVoiceServices;

import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Public, implementation-independent entry point for optional integrations.
 *
 * <p>Callers should first check that the Fabric mod ID {@code rp-vca} is loaded. The
 * service is present only while a Minecraft server is running.</p>
 */
public final class RpVcaApi {
    private static final Map<String, InstitutionMembershipProvider> INSTITUTION_PROVIDERS =
            new ConcurrentHashMap<>();
    private static final Map<String, DeviceCapabilityProvider> DEVICE_PROVIDERS =
            new ConcurrentHashMap<>();
    private static final Map<String, PhoneApplicationProvider> PHONE_APPLICATIONS =
            new ConcurrentHashMap<>();
    private RpVcaApi() {
    }

    public static Optional<PhoneApi> getPhoneService() {
        RpVoiceServices services = RpVoiceServices.get();
        return services == null ? Optional.empty() : Optional.of(services.phones());
    }

    public static void registerInstitutionMembershipProvider(String providerId,
                                                             InstitutionMembershipProvider provider) {
        if (providerId == null || providerId.isBlank() || provider == null) {
            throw new IllegalArgumentException("Provider-ID und Provider dürfen nicht leer sein");
        }
        INSTITUTION_PROVIDERS.put(providerId, provider);
    }

    public static void unregisterInstitutionMembershipProvider(String providerId) {
        if (providerId != null) {
            INSTITUTION_PROVIDERS.remove(providerId);
        }
    }

    public static void registerDeviceCapabilityProvider(String providerId, DeviceCapabilityProvider provider) {
        if (providerId == null || providerId.isBlank() || provider == null) {
            throw new IllegalArgumentException("Provider-ID und Provider dürfen nicht leer sein");
        }
        DEVICE_PROVIDERS.put(providerId, provider);
    }

    public static void unregisterDeviceCapabilityProvider(String providerId) {
        if (providerId != null) DEVICE_PROVIDERS.remove(providerId);
    }

    public static java.util.Optional<DeviceCapabilities> deviceCapabilities(
            net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return java.util.Optional.empty();
        for (Map.Entry<String, DeviceCapabilityProvider> entry : DEVICE_PROVIDERS.entrySet()) {
            try {
                java.util.Optional<DeviceCapabilities> result = entry.getValue().capabilities(stack);
                if (result != null && result.isPresent()) return result;
            } catch (RuntimeException exception) {
                com.evarius.rpvca.RpVoiceAddon.LOGGER.error(
                        "Geräteprovider '{}' ist fehlgeschlagen", entry.getKey(), exception);
            }
        }
        return java.util.Optional.empty();
    }

    public static void registerPhoneApplication(String applicationId, PhoneApplicationProvider provider) {
        if (applicationId == null || applicationId.isBlank() || provider == null) {
            throw new IllegalArgumentException("App-ID und Provider dürfen nicht leer sein");
        }
        PHONE_APPLICATIONS.put(applicationId, provider);
    }

    public static void unregisterPhoneApplication(String applicationId) {
        if (applicationId != null) PHONE_APPLICATIONS.remove(applicationId);
    }

    public static Map<String, PhoneApplicationProvider> phoneApplications() {
        return Map.copyOf(PHONE_APPLICATIONS);
    }

    public static Set<String> institutionMembershipKeys(ServerPlayerEntity player) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        INSTITUTION_PROVIDERS.forEach((id, provider) -> {
            try {
                Set<String> keys = provider.membershipKeys(player);
                if (keys != null) {
                    keys.stream().filter(java.util.Objects::nonNull)
                            .map(String::trim).filter(value -> !value.isBlank()).forEach(result::add);
                }
            } catch (RuntimeException exception) {
                com.evarius.rpvca.RpVoiceAddon.LOGGER.error(
                        "Institutionsprovider '{}' ist fehlgeschlagen", id, exception);
            }
        });
        return Set.copyOf(result);
    }
}
