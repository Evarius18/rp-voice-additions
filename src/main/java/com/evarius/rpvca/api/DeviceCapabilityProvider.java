package com.evarius.rpvca.api;

import net.minecraft.item.ItemStack;

import java.util.Optional;

/** Optional SPI for mods contributing phone or radio items without a runtime dependency. */
@FunctionalInterface
public interface DeviceCapabilityProvider {
    Optional<DeviceCapabilities> capabilities(ItemStack stack);
}
