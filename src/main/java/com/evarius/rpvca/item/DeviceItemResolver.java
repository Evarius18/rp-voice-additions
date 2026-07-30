package com.evarius.rpvca.item;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.api.DeviceCapabilities;
import com.evarius.rpvca.api.RpVcaApi;
import com.evarius.rpvca.config.DeviceConfig;
import com.evarius.rpvca.config.PhoneConfig;
import com.evarius.rpvca.config.RadioConfig;
import com.evarius.rpvca.content.ModContent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Cached registry-level device resolution plus optional public provider capabilities. */
public final class DeviceItemResolver {
    private final Set<Item> defaultPhoneItems;
    private final Set<Item> externalPhoneItems;
    private final Set<Item> radioItems;
    private final RadioConfig radioConfig;

    public DeviceItemResolver(DeviceConfig config, RadioConfig radioConfig) {
        this.radioConfig = radioConfig;
        defaultPhoneItems = resolve(config.phoneItems, ModContent.MOBILE_PHONE, "Handy", true);
        externalPhoneItems = resolve(config.externalPhoneItems, null, "externes Handy", false);
        radioItems = resolve(config.radioItems, ModContent.RADIO, "Funkgerät", true);
    }

    public DeviceCapabilities capabilities(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        // External ownership wins for migrated configs that listed the same item in phoneItems.
        if (externalPhoneItems.contains(item)) return DeviceCapabilities.externalPhone();
        if (defaultPhoneItems.contains(item)) {
            return new DeviceCapabilities(true, true, true, true, true, false);
        }
        if (radioItems.contains(item)) {
            return new DeviceCapabilities(false, false, false, false, false, true);
        }
        return RpVcaApi.deviceCapabilities(stack).orElse(null);
    }

    public boolean isPhone(ItemStack stack) {
        DeviceCapabilities capabilities = capabilities(stack);
        return capabilities != null && capabilities.phoneEnabled();
    }

    public boolean shouldOpenRpVcaScreen(ItemStack stack) {
        DeviceCapabilities capabilities = capabilities(stack);
        return capabilities != null && capabilities.phoneEnabled()
                && capabilities.openDefaultRpVcaScreen();
    }

    public boolean isRadio(ItemStack stack) {
        DeviceCapabilities capabilities = capabilities(stack);
        return capabilities != null && capabilities.radioEnabled();
    }

    public boolean hasPhone(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (isPhone(player.getInventory().getStack(slot))) return true;
        }
        return false;
    }

    public boolean isHoldingUsablePhone(ServerPlayerEntity player, PhoneConfig config) {
        return resolveHeldPhone(player, config).isPresent();
    }

    public java.util.Optional<ResolvedPhoneDevice> resolveHeldPhone(
            ServerPlayerEntity player, PhoneConfig config) {
        if (config.allowMainHand) {
            ResolvedPhoneDevice resolved = resolvePhone(player.getMainHandStack());
            if (resolved != null) return java.util.Optional.of(resolved);
        }
        if (config.allowOffHand) {
            ResolvedPhoneDevice resolved = resolvePhone(player.getOffHandStack());
            if (resolved != null) return java.util.Optional.of(resolved);
        }
        return java.util.Optional.empty();
    }

    private ResolvedPhoneDevice resolvePhone(ItemStack stack) {
        DeviceCapabilities capabilities = capabilities(stack);
        if (capabilities == null || !capabilities.phoneEnabled()) return null;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return new ResolvedPhoneDevice(stack.copy(), capabilities,
                itemId == null ? "" : itemId.getNamespace());
    }

    public boolean hasRadio(ServerPlayerEntity player) {
        if ("HAND".equals(radioConfig.deviceLocation)) {
            return isRadio(player.getMainHandStack())
                    || (radioConfig.allowOffHand && isRadio(player.getOffHandStack()));
        }
        int upper = "HOTBAR".equals(radioConfig.deviceLocation) ? 9 : player.getInventory().size();
        for (int slot = 0; slot < upper; slot++) {
            if (slot < 9 && !radioConfig.allowHotbar) continue;
            if (slot >= 9 && slot < 36 && !radioConfig.allowMainInventory) continue;
            if (slot >= 36 && slot < 40 && !radioConfig.allowArmorSlots) continue;
            if (slot >= 40 && !radioConfig.allowOffHand) continue;
            if (isRadio(player.getInventory().getStack(slot))) return true;
        }
        return false;
    }

    private static Set<Item> resolve(List<String> configuredIds, Item fallback,
                                     String deviceName, boolean warnMissing) {
        Set<Item> resolved = new LinkedHashSet<>();
        for (String rawId : configuredIds) {
            Identifier id = Identifier.tryParse(rawId);
            if (id == null || !Registries.ITEM.containsId(id)) {
                if (warnMissing) {
                    RpVoiceAddon.LOGGER.warn("{}-Item '{}' ist ungültig oder nicht installiert und wird ignoriert",
                            deviceName, rawId);
                }
                continue;
            }
            resolved.add(Registries.ITEM.get(id));
        }
        if (resolved.isEmpty() && fallback != null) {
            RpVoiceAddon.LOGGER.warn("Keine gültigen {}-Items konfiguriert; Standard-Item wird verwendet",
                    deviceName);
            resolved.add(fallback);
        }
        return Set.copyOf(resolved);
    }
}
