package com.evarius.rpvca.api;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Optional SPI for mods contributing phone or radio items without a runtime dependency. */
@FunctionalInterface
public interface DeviceCapabilityProvider {
    Optional<DeviceCapabilities> capabilities(ItemStack stack);
}
