package com.evarius.rpvca.item;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.config.DeviceConfig;
import com.evarius.rpvca.content.ModContent;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves configurable device item IDs once registries are complete. */
public final class DeviceItemResolver {
    private final Set<Item> phoneItems;
    private final Set<Item> radioItems;

    public DeviceItemResolver(DeviceConfig config) {
        phoneItems = resolve(config.phoneItems, ModContent.MOBILE_PHONE, "Handy");
        radioItems = resolve(config.radioItems, ModContent.RADIO, "Funkgerät");
    }

    public boolean isPhone(Item item) {
        return phoneItems.contains(item);
    }

    public boolean isRadio(Item item) {
        return radioItems.contains(item);
    }

    public boolean hasPhone(ServerPlayerEntity player) {
        return player.getInventory().containsAny(phoneItems);
    }

    public boolean hasRadio(ServerPlayerEntity player) {
        return player.getInventory().containsAny(radioItems);
    }

    private static Set<Item> resolve(List<String> configuredIds, Item fallback, String deviceName) {
        Set<Item> resolved = new LinkedHashSet<>();
        for (String rawId : configuredIds) {
            Identifier id = Identifier.tryParse(rawId);
            if (id == null || !Registries.ITEM.containsId(id)) {
                RpVoiceAddon.LOGGER.warn("{}-Item '{}' ist ungültig oder nicht installiert und wird ignoriert",
                        deviceName, rawId);
                continue;
            }
            resolved.add(Registries.ITEM.get(id));
        }
        if (resolved.isEmpty()) {
            RpVoiceAddon.LOGGER.warn("Keine gültigen {}-Items konfiguriert; Standard-Item wird verwendet", deviceName);
            resolved.add(fallback);
        }
        return Set.copyOf(resolved);
    }
}
